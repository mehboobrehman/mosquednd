package com.mosquednd.util

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import com.mosquednd.data.AgentLogDao
import com.mosquednd.data.AgentLogEntity
import com.mosquednd.data.MosqueDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * AgentLogger: Production-grade Telemetry Client for MosqueDND (Phase 3).
 * - Enforces redaction via RedactionUtils.
 * - Manages connection state machine (DISCONNECTED, CONNECTING, CONNECTED, DEGRADED, OFFLINE).
 * - Performs handshake & clock-offset synchronization on connection establishment.
 * - Implements bounded local buffering, structured drop policies, rate limiting, and batch flushing.
 * - Crash & Uncaught Exception Capture with synchronous persistence & flush-before-death.
 * - Main-Thread Hang / ANR Detection via AnrWatchdog.
 * - Performance Sampling (memory, threads, battery, uptime).
 * - Remote Commands execution and acknowledgment.
 */
object AgentLogger {
    private const val TAG = "AgentLogger"
    private const val PREFS_NAME = "agent_logger_prefs"
    private const val KEY_SERVER_IP = "server_ip"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val DEFAULT_PORT = 5000
    private const val HEALTH_CHECK_INTERVAL_MS = 60_000L
    private const val MAX_LOG_CAPACITY = 500

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DEGRADED,
        OFFLINE
    }

    private val connectionState = AtomicReference(ConnectionState.DISCONNECTED)
    private val healthCheckScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "AgentLogger-HealthCheck").apply { isDaemon = true }
    }
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AgentLogger-Executor").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Volatile private var cachedServerIp: String? = null
    @Volatile private var cachedDeviceId: String? = null
    @Volatile private var cachedAuthToken: String? = null
    @Volatile private var sessionId: String = "session-${System.currentTimeMillis()}"
    @Volatile private var isInitialized = false
    private val sequenceNumberCounter = AtomicLong(0)
    private val sequenceNumber: Long get() = sequenceNumberCounter.incrementAndGet()

    @Volatile private var clockOffsetMs: Long = 0L
    @Volatile private var appContext: Context? = null
    @Volatile private var agentLogDao: AgentLogDao? = null

    private val isDiscovering = AtomicBoolean(false)
    private val manualOverrideActive = AtomicBoolean(false)

    // Backoff state for connection retries
    private var currentBackoffMs = 1000L
    private const val MAX_BACKOFF_MS = 30_000L

    // Dropped event counter
    private val droppedEventCount = AtomicLong(0L)
    fun getDroppedEventCount(): Long = droppedEventCount.get()

    // Rate limiting map per category/level: category -> (count, windowStart)
    private data class RateLimitRecord(var count: Int, var windowStart: Long)
    private val rateLimits = ConcurrentHashMap<String, RateLimitRecord>()
    private const val RATE_LIMIT_WINDOW_MS = 60_000L
    private const val RATE_LIMIT_MAX_PER_WINDOW = 200

    private var anrWatchdog: AnrWatchdog? = null

    fun getConnectionState(): ConnectionState = connectionState.get()

    fun init(context: Context, defaultServerIp: String? = null) {
        if (isInitialized) return
        appContext = context.applicationContext

        executor.execute {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

                // Load or generate persistent device ID
                var devId = prefs.getString(KEY_DEVICE_ID, null)
                if (devId.isNullOrEmpty()) {
                    devId = "device-${Build.MODEL}-${System.currentTimeMillis() % 10000}"
                    prefs.edit().putString(KEY_DEVICE_ID, devId).apply()
                }
                cachedDeviceId = devId
                cachedAuthToken = prefs.getString(KEY_AUTH_TOKEN, null)

                // Initialize DAO
                agentLogDao = MosqueDatabase.getInstance(context).agentLogDao()

                val savedIp = prefs.getString(KEY_SERVER_IP, null)
                if (!defaultServerIp.isNullOrEmpty()) {
                    cachedServerIp = defaultServerIp
                    prefs.edit().putString(KEY_SERVER_IP, defaultServerIp).apply()
                } else {
                    cachedServerIp = savedIp
                }

                isInitialized = true
                startHealthCheckScheduler()
                setupUncaughtExceptionHandler()
                startAnrWatchdog()

                context.applicationContext.let { ctx ->
                    if (ctx is android.app.Application) {
                        ctx.registerActivityLifecycleCallbacks(TelemetryLifecycleCallbacks)
                    }
                    PerformanceSampler.start(ctx)
                }

                RemoteCommandManager.startPolling(
                    serverIpProvider = { cachedServerIp },
                    deviceIdProvider = { cachedDeviceId },
                    authTokenProvider = { cachedAuthToken }
                )

                Log.d(TAG, "AgentLogger initialized. DeviceId: $cachedDeviceId, SessionId: $sessionId, ServerIp: $cachedServerIp")

                establishConnectionOrDiscover()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AgentLogger", e)
                connectionState.set(ConnectionState.OFFLINE)
            }
        }
    }

    private fun startAnrWatchdog() {
        if (anrWatchdog != null) return
        anrWatchdog = AnrWatchdog(
            anrThresholdMs = 3000L,
            checkIntervalMs = 1000L,
            onAnrDetected = { stackTrace ->
                Log.e(TAG, "ANR Detected! Main thread blocked.\n$stackTrace")
                emitEnvelope("crash", mapOf(
                    "level" to "FATAL",
                    "message" to "ANR detected: Main thread unresponsive for >3000ms",
                    "tag" to "ANR",
                    "stack" to stackTrace
                ))
            }
        ).apply { start() }
    }

    private fun establishConnectionOrDiscover() {
        connectionState.set(ConnectionState.CONNECTING)

        // 1. Try USB adb reverse / localhost first
        if (tryConnectServer("http://localhost:$DEFAULT_PORT")) return
        if (tryConnectServer("http://10.0.2.2:$DEFAULT_PORT")) return

        // 2. Try cached or configured server IP
        val serverIp = cachedServerIp
        if (!serverIp.isNullOrEmpty()) {
            if (tryConnectServer(serverIp)) return
        }

        // 3. Fallback to Wi-Fi subnet discovery
        startSubnetDiscovery()
    }

    private fun tryConnectServer(baseUrl: String): Boolean {
        try {
            val normalized = normalizeServerUrl(baseUrl)
            val url = URL("$normalized/api/config")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2000
                readTimeout = 2000
            }
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                cachedServerIp = normalized
                val token = json.optString("authToken", "")
                if (token.isNotEmpty()) {
                    cachedAuthToken = token
                    appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        ?.edit()?.putString(KEY_AUTH_TOKEN, token)?.apply()
                }

                // Estimate clock offset if server timestamp header/field present
                val serverTimeStr = json.optString("timestamp", "")
                if (serverTimeStr.isNotEmpty()) {
                    try {
                        val serverTimeMs = dateFormat.parse(serverTimeStr)?.time ?: 0L
                        if (serverTimeMs > 0) {
                            clockOffsetMs = serverTimeMs - System.currentTimeMillis()
                        }
                    } catch (_: Exception) {}
                }

                connectionState.set(ConnectionState.CONNECTED)
                currentBackoffMs = 1000L
                Log.d(TAG, "Connected to telemetry server at $normalized (Clock Offset: ${clockOffsetMs}ms)")
                
                performHandshake()
                flushPendingLogs()
                conn.disconnect()
                return true
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.d(TAG, "Could not connect to $baseUrl: ${e.message}")
        }
        return false
    }

    private fun performHandshake() {
        val serverIp = cachedServerIp ?: return
        try {
            val url = URL("$serverIp/api/log")
            val appVersion = appContext?.let { ctx ->
                try {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                } catch (_: Exception) { "1.0.0" }
            } ?: "1.0.0"

            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            val payload = mapOf(
                "level" to "INFO",
                "message" to "Device Telemetry Handshake",
                "model" to Build.MODEL,
                "sdkVersion" to Build.VERSION.SDK_INT,
                "abi" to abi,
                "appVersion" to appVersion,
                "buildType" to "debug",
                "capabilities" to listOf("logging", "metrics", "crash-reporting", "redaction", "batching", "anr-detection", "network-interceptor", "performance-sampling", "lifecycle-tracking", "remote-commands")
            )

            val wallClock = dateFormat.format(Date(System.currentTimeMillis() + clockOffsetMs))
            val monotonic = SystemClock.elapsedRealtime().toDouble()
            val seq = sequenceNumber
            val devId = cachedDeviceId ?: "unknown-android-device"

            val envelopeMap = mutableMapOf<String, Any>(
                "schemaVersion" to "1.0.0",
                "sessionId" to sessionId,
                "deviceId" to devId,
                "sequenceNumber" to seq,
                "monotonicTimestamp" to monotonic,
                "wallClockTimestamp" to wallClock,
                "messageType" to "handshake",
                "payload" to payload
            )
            cachedAuthToken?.let { envelopeMap["authToken"] = it }

            val jsonString = JSONObject(envelopeMap as Map<*, *>).toString()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 3000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                cachedAuthToken?.let { setRequestProperty("X-Telemetry-Token", it) }
            }

            conn.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { writer ->
                    writer.write(jsonString)
                    writer.flush()
                }
            }
            conn.responseCode
            conn.disconnect()
            Log.d(TAG, "Handshake successfully performed with server.")
        } catch (e: Exception) {
            Log.w(TAG, "Handshake failed: ${e.message}")
        }
    }

    private fun startHealthCheckScheduler() {
        healthCheckScheduler.scheduleAtFixedRate({
            try {
                val serverIp = cachedServerIp
                if (serverIp == null) {
                    connectionState.set(ConnectionState.DISCONNECTED)
                    if (!isDiscovering.get() && !manualOverrideActive.get()) {
                        establishConnectionOrDiscover()
                    }
                    return@scheduleAtFixedRate
                }

                val url = URL("$serverIp/api/health")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 2500
                    readTimeout = 2500
                }
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) {
                    connectionState.set(ConnectionState.CONNECTED)
                } else {
                    connectionState.set(ConnectionState.DEGRADED)
                }
            } catch (e: Exception) {
                connectionState.set(ConnectionState.DEGRADED)
                Log.w(TAG, "Health check failed: ${e.message}")
            }
        }, HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    fun setServerIpFromUser(url: String) {
        val normalized = normalizeServerUrl(url)
        if (normalized.isEmpty()) return
        manualOverrideActive.set(true)
        cachedServerIp = normalized
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_SERVER_IP, normalized)?.apply()
        showToast("Telemetry server set to $normalized")
        executor.execute {
            if (tryConnectServer(normalized)) {
                connectionState.set(ConnectionState.CONNECTED)
            } else {
                connectionState.set(ConnectionState.DEGRADED)
            }
        }
    }

    fun setServerIp(context: Context, ip: String) {
        cachedServerIp = ip
        appContext = context.applicationContext
        executor.execute {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_SERVER_IP, ip).apply()
            } catch (_: Exception) {}
        }
    }

    fun getServerIp(context: Context): String? {
        appContext = context.applicationContext
        if (cachedServerIp != null) return cachedServerIp
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            cachedServerIp = prefs.getString(KEY_SERVER_IP, null)
        } catch (_: Exception) {}
        return cachedServerIp
    }

    fun clearServerIp(context: Context) {
        manualOverrideActive.set(false)
        cachedServerIp = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_SERVER_IP).apply()
        connectionState.set(ConnectionState.DISCONNECTED)
        executor.execute { establishConnectionOrDiscover() }
    }

    private fun normalizeServerUrl(raw: String): String {
        var s = raw.trim().trimEnd('/')
        if (s.isEmpty()) return ""
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "http://$s"
        }
        return try {
            val uri = java.net.URI(s)
            if (uri.scheme == "http" && uri.port == -1 && !uri.host.isNullOrEmpty()) {
                "${uri.scheme}://${uri.host}:$DEFAULT_PORT"
            } else {
                s
            }
        } catch (e: Exception) {
            s
        }
    }

    fun d(message: String, tag: String = TAG) {
        val redactedMsg = RedactionUtils.redact(message)
        Log.d(tag, redactedMsg)
        emitEnvelope("log", mapOf<String, Any>("level" to "DEBUG", "message" to "[$tag] $redactedMsg", "tag" to tag))
    }

    fun i(message: String, tag: String = TAG) {
        val redactedMsg = RedactionUtils.redact(message)
        Log.i(tag, redactedMsg)
        emitEnvelope("log", mapOf<String, Any>("level" to "INFO", "message" to "[$tag] $redactedMsg", "tag" to tag))
    }

    fun w(message: String, tag: String = TAG) {
        val redactedMsg = RedactionUtils.redact(message)
        Log.w(tag, redactedMsg)
        emitEnvelope("log", mapOf<String, Any>("level" to "WARN", "message" to "[$tag] $redactedMsg", "tag" to tag))
    }

    fun logToAgent(level: String, message: String, stack: String?) {
        val redactedMsg = RedactionUtils.redact(message)
        val redactedStack = stack?.let { RedactionUtils.redact(it) }
        emitEnvelope("log", mapOf<String, Any>(
            "level" to level,
            "message" to redactedMsg,
            "stack" to (redactedStack ?: "")
        ))
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = TAG) {
        val redactedMsg = RedactionUtils.redact(message)
        Log.e(tag, redactedMsg, throwable)
        val stack = throwable?.let { Log.getStackTraceString(it) }
        val redactedStack = stack?.let { RedactionUtils.redact(it) }
        emitEnvelope("log", mapOf<String, Any>("level" to "ERROR", "message" to "[$tag] $redactedMsg", "tag" to tag, "stack" to (redactedStack ?: "")))
    }

    private fun checkRateLimit(category: String): Boolean {
        val now = System.currentTimeMillis()
        var record = rateLimits[category]
        if (record == null || now - record.windowStart > RATE_LIMIT_WINDOW_MS) {
            record = RateLimitRecord(0, now)
            rateLimits[category] = record
        }
        record.count++
        return record.count <= RATE_LIMIT_MAX_PER_WINDOW
    }

    fun emitEnvelope(messageType: String, payloadMap: Map<String, Any>) {
        val category = (payloadMap["tag"] as? String) ?: (payloadMap["level"] as? String) ?: "general"
        if (!checkRateLimit(category)) {
            droppedEventCount.incrementAndGet()
            return
        }

        val redactedPayload = RedactionUtils.redactMap(payloadMap)
        val wallClock = dateFormat.format(Date(System.currentTimeMillis() + clockOffsetMs))
        val monotonic = SystemClock.elapsedRealtime().toDouble()
        val seq = sequenceNumber
        val devId = cachedDeviceId ?: "unknown-android-device"

        val envelopeMap = mutableMapOf<String, Any>(
            "schemaVersion" to "1.0.0",
            "sessionId" to sessionId,
            "deviceId" to devId,
            "sequenceNumber" to seq,
            "monotonicTimestamp" to monotonic,
            "wallClockTimestamp" to wallClock,
            "messageType" to messageType,
            "payload" to redactedPayload
        )
        cachedAuthToken?.let { envelopeMap["authToken"] = it }

        val jsonString = JSONObject(envelopeMap as Map<*, *>).toString()
        val level = (redactedPayload["level"] as? String) ?: "INFO"
        val message = (redactedPayload["message"] as? String) ?: ""
        val stack = redactedPayload["stack"] as? String

        executor.execute {
            val dao = agentLogDao
            if (dao != null) {
                runBlocking {
                    try {
                        val count = dao.getCount()
                        if (count >= MAX_LOG_CAPACITY) {
                            dao.trimOldLogs(MAX_LOG_CAPACITY - 1)
                            droppedEventCount.incrementAndGet()
                        }
                        dao.insert(AgentLogEntity(level = level, message = message, stack = stack, timestamp = wallClock))
                        dao.trimOldLogs(MAX_LOG_CAPACITY)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed inserting log into DB: ${e.message}")
                    }
                }
            }

            if (connectionState.get() == ConnectionState.CONNECTED || cachedServerIp != null) {
                sendEnvelopeToServer(jsonString)
            } else {
                val jitter = (Math.random() * 500).toLong()
                val sleepTime = currentBackoffMs + jitter
                currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                try {
                    Thread.sleep(sleepTime.coerceAtMost(3000L))
                } catch (_: InterruptedException) {}
                establishConnectionOrDiscover()
            }
        }
    }

    private fun sendEnvelopeToServer(jsonPayload: String) {
        val serverIp = cachedServerIp ?: return
        try {
            val url = URL("$serverIp/api/log")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 3000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                cachedAuthToken?.let { setRequestProperty("X-Telemetry-Token", it) }
            }

            conn.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { writer ->
                    writer.write(jsonPayload)
                    writer.flush()
                }
            }

            val code = conn.responseCode
            if (code in 200..299) {
                connectionState.set(ConnectionState.CONNECTED)
            } else {
                connectionState.set(ConnectionState.DEGRADED)
                Log.w(TAG, "Server responded with HTTP $code to telemetry log")
            }
            conn.disconnect()
        } catch (e: Exception) {
            connectionState.set(ConnectionState.DEGRADED)
            Log.w(TAG, "Failed sending telemetry envelope: ${e.message}")
        }
    }

    private fun flushPendingLogs() {
        val dao = agentLogDao ?: return
        executor.execute {
            runBlocking {
                try {
                    val logs = dao.getLogs(50)
                    if (logs.isNotEmpty()) {
                        Log.d(TAG, "Flushing batch of ${logs.size} pending logs...")
                        val idsToDelete = mutableListOf<Long>()
                        for (l in logs) {
                            idsToDelete.add(l.id)
                            emitEnvelope("log", mapOf("level" to l.level, "message" to l.message, "stack" to (l.stack ?: "")))
                        }
                        if (idsToDelete.isNotEmpty()) {
                            dao.deleteByIds(idsToDelete)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error flushing pending logs: ${e.message}")
                }
            }
        }
    }

    private fun startSubnetDiscovery() {
        if (!isDiscovering.compareAndSet(false, true)) return
        executor.execute {
            try {
                val subnet = detectSubnetPrefix() ?: "192.168.1."
                Log.i(TAG, "Starting subnet discovery on ${subnet}1-254...")
                for (i in 1..254) {
                    val ip = "$subnet$i"
                    if (tryConnectServer("http://$ip:$DEFAULT_PORT")) {
                        isDiscovering.set(false)
                        return@execute
                    }
                }
                connectionState.set(ConnectionState.OFFLINE)
                isDiscovering.set(false)
            } catch (e: Exception) {
                connectionState.set(ConnectionState.OFFLINE)
                isDiscovering.set(false)
            }
        }
    }

    private fun detectSubnetPrefix(): String? {
        try {
            val ctx = appContext ?: return null
            val wifiMgr = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val ip = wifiMgr?.dhcpInfo?.ipAddress ?: 0
            if (ip != 0) {
                return String.format("%d.%d.%d.", ip and 0xff, (ip shr 8) and 0xff, (ip shr 16) and 0xff)
            }
        } catch (_: Exception) {}
        return "192.168.1."
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stack = Log.getStackTraceString(throwable)
                val redactedStack = RedactionUtils.redact(stack)
                val message = "Uncaught exception in thread ${thread.name}: ${throwable.message}"
                val redactedMsg = RedactionUtils.redact(message)

                // 1. Synchronously persist to Room DB
                val dao = agentLogDao
                if (dao != null && appContext != null) {
                    try {
                        val wallClock = dateFormat.format(Date(System.currentTimeMillis() + clockOffsetMs))
                        runBlocking {
                            dao.insert(AgentLogEntity(level = "FATAL", message = redactedMsg, stack = redactedStack, timestamp = wallClock))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed synchronous crash persistence in DB", e)
                    }
                }

                // 2. Synchronously emit crash envelope & flush to server before death
                val payload = mapOf<String, Any>(
                    "level" to "FATAL",
                    "message" to redactedMsg,
                    "tag" to "CrashHandler",
                    "stack" to redactedStack
                )
                val wallClock = dateFormat.format(Date(System.currentTimeMillis() + clockOffsetMs))
                val monotonic = SystemClock.elapsedRealtime().toDouble()
                val seq = sequenceNumber
                val devId = cachedDeviceId ?: "unknown-android-device"

                val envelopeMap = mutableMapOf<String, Any>(
                    "schemaVersion" to "1.0.0",
                    "sessionId" to sessionId,
                    "deviceId" to devId,
                    "sequenceNumber" to seq,
                    "monotonicTimestamp" to monotonic,
                    "wallClockTimestamp" to wallClock,
                    "messageType" to "crash",
                    "payload" to payload
                )
                cachedAuthToken?.let { envelopeMap["authToken"] = it }
                val jsonString = JSONObject(envelopeMap as Map<*, *>).toString()

                val serverIp = cachedServerIp
                if (!serverIp.isNullOrEmpty()) {
                    try {
                        val url = URL("$serverIp/api/log")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 1500
                            readTimeout = 1500
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            cachedAuthToken?.let { setRequestProperty("X-Telemetry-Token", it) }
                        }
                        conn.outputStream.use { os ->
                            OutputStreamWriter(os, "UTF-8").use { writer ->
                                writer.write(jsonString)
                                writer.flush()
                            }
                        }
                        conn.responseCode
                        conn.disconnect()
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun showToast(msg: String) {
        mainHandler.post {
            appContext?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() }
        }
    }
}
