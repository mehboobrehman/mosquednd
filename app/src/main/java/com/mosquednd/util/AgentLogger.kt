package com.mosquednd.util

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * AgentLogger: Zero-dependency, non-blocking Wi-Fi log telemetry and crash reporting utility.
 * Streams runtime logs and uncaught exceptions over local Wi-Fi to a Node.js log collector server.
 * Operates safely with zero USB debugging and zero ADB cable requirements.
 */
object AgentLogger {
    private const val TAG = "AgentLogger"
    private const val PREFS_NAME = "agent_logger_prefs"
    private const val KEY_SERVER_IP = "server_ip"
    private const val KEY_DEVICE_ID = "device_id"
    private const val DEFAULT_PORT = 5000
    private const val MAX_PENDING_LOGS = 50
    private const val RETRY_COOLDOWN_MS = 30_000L

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Volatile
    private var cachedServerIp: String? = null
    @Volatile
    private var cachedDeviceId: String? = null
    @Volatile
    private var isInitialized = false
    @Volatile
    private var lastDiscoveryAttemptTime: Long = 0L

    @Volatile
    private var appContext: Context? = null

    private data class LogEntry(val level: String, val message: String, val stack: String?, val timestamp: String)
    private val pendingLogs = ConcurrentLinkedQueue<LogEntry>()
    private val isDiscovering = AtomicBoolean(false)
    /** True once the user (or app code) pinned a server IP via setServerIpFromUser().
     *  Prevents a late subnet-discovery hit from overwriting the manual override. */
    private val manualOverrideActive = AtomicBoolean(false)

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

                // Load cached server IP or save default if provided
                val savedIp = prefs.getString(KEY_SERVER_IP, null)
                if (!defaultServerIp.isNullOrEmpty()) {
                    cachedServerIp = defaultServerIp
                    prefs.edit().putString(KEY_SERVER_IP, defaultServerIp).apply()
                } else {
                    cachedServerIp = savedIp
                }

                // Flush any logs that were buffered before the server IP was available
                if (cachedServerIp != null) {
                    flushPendingLogs()
                }

                isInitialized = true
                Log.d(TAG, "AgentLogger initialized. DeviceId: $cachedDeviceId, ServerIp: $cachedServerIp")

                // Toast: init complete
                val serverInfo = cachedServerIp ?: "scanning..."
                showToast("AgentLogger init: Server=$serverInfo")

                // Setup global uncaught exception handler
                setupUncaughtExceptionHandler()

                // If no server IP is available, start autonomous subnet discovery
                if (cachedServerIp == null) {
                    startSubnetDiscovery()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AgentLogger", e)
            }
        }
    }

    fun setServerIp(context: Context, ip: String) {
        cachedServerIp = ip
        executor.execute {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_SERVER_IP, ip).apply()
                Log.d(TAG, "Server IP updated and cached: $ip")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save server IP", e)
            }
        }
    }

    /**
     * Manually set the log server IP/URL from the app UI — THE reliable override when
     * the phone and the PC log server are on DIFFERENT subnets (auto-discovery only
     * scans the phone's own subnet, so it can never find a server elsewhere, e.g.
     * phone on 10.12.93.x vs PC on 192.168.105.79).
     *
     * Accepts a bare IP ("192.168.105.79"), host:port ("192.168.105.79:5000"), or a full
     * URL ("http://192.168.105.79:5000"). The value is normalized to canonical
     * "http://host:port" form, persisted to SharedPreferences, cached immediately, and
     * any buffered logs are flushed. Because it is persisted, init() will use it on every
     * subsequent launch WITHOUT subnet scanning (see init()).
     */
    @Synchronized
    fun setServerIpFromUser(url: String) {
        val normalized = normalizeServerUrl(url)
        if (normalized.isEmpty()) {
            Log.w(TAG, "setServerIpFromUser: ignored empty/invalid URL")
            return
        }
        manualOverrideActive.set(true)
        cachedServerIp = normalized
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs?.edit()?.putString(KEY_SERVER_IP, normalized)?.apply()
        Log.i(TAG, "Server IP set manually by user: $normalized (persisted, flush scheduled)")
        showToast("Log server set to $normalized")

        // Flush any pending logs now that we have a target (no-op when buffer is empty)
        executor.execute { flushPendingLogs() }
    }

    /**
     * Normalizes a user-supplied server target into canonical "http://host:port" form so it
     * works in every code path. "192.168.105.79" -> "http://192.168.105.79:5000" (a bare IP
     * must not fall through to HTTP port 80), "192.168.105.79:5000" is kept, and full URLs
     * pass through unchanged.
     */
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
            // Malformed input — keep raw; doSendToServer will fail fast with a clear log
            s
        }
    }

    fun getServerIp(context: Context): String? {
        if (cachedServerIp != null) return cachedServerIp
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            cachedServerIp = prefs.getString(KEY_SERVER_IP, null)
        } catch (e: Exception) {
            // Ignore
        }
        return cachedServerIp
    }

    /**
     * Clears a previously pinned server IP/URL, returning to autonomous subnet discovery.
     * Removes the persisted value and resets the cache so the next log call (or the next
     * app launch, via init()) triggers subnet scanning again.
     */
    fun clearServerIp(context: Context) {
        manualOverrideActive.set(false)
        cachedServerIp = null
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_SERVER_IP).apply()
            Log.i(TAG, "Server IP cleared — subnet auto-discovery re-enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear server IP", e)
        }
    }

    fun d(message: String, tag: String = TAG) {
        Log.d(tag, message)
        logToAgent("DEBUG", "[$tag] $message", null)
    }

    fun i(message: String, tag: String = TAG) {
        Log.i(tag, message)
        logToAgent("INFO", "[$tag] $message", null)
    }

    fun w(message: String, tag: String = TAG) {
        Log.w(tag, message)
        logToAgent("WARN", "[$tag] $message", null)
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = TAG) {
        Log.e(tag, message, throwable)
        val stack = throwable?.let { android.util.Log.getStackTraceString(it) }
        logToAgent("ERROR", "[$tag] $message", stack)
    }

    fun logToAgent(level: String, message: String, stack: String?) {
        val timestamp = dateFormat.format(Date())

        // If server IP not yet discovered, buffer the log and trigger discovery
        if (cachedServerIp == null) {
            if (pendingLogs.size < MAX_PENDING_LOGS) {
                pendingLogs.offer(LogEntry(level, message, stack, timestamp))
            }
            // Trigger discovery if not already running and cooldown expired
            val now = System.currentTimeMillis()
            if (!isDiscovering.get() && (now - lastDiscoveryAttemptTime > RETRY_COOLDOWN_MS)) {
                startSubnetDiscovery()
            }
            return
        }

        val serverIp = cachedServerIp ?: return
        val deviceId = cachedDeviceId ?: "unknown-android-device"

        sendLogToServer(serverIp, deviceId, level, message, stack, timestamp)
    }

    private fun sendLogToServer(serverIp: String, deviceId: String, level: String, message: String, stack: String?, timestamp: String) {
        executor.execute {
            doSendToServer(serverIp, deviceId, level, message, stack, timestamp)
        }
    }

    /**
     * Performs the actual HTTP POST to the log server.
     * Must be called from a background thread (never wraps in executor to avoid deadlock).
     */
    private fun doSendToServer(serverIp: String, deviceId: String, level: String, message: String, stack: String?, timestamp: String) {
        try {
            val baseUrl = if (serverIp.startsWith("http")) serverIp else "http://$serverIp:$DEFAULT_PORT"
            val url = URL("$baseUrl/api/log")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 2000
                readTimeout = 2000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            val jsonPayload = buildString {
                append("{")
                append("\"level\":\"").append(escapeJson(level)).append("\",")
                append("\"message\":\"").append(escapeJson(message)).append("\",")
                append("\"stack\":").append(if (stack != null) "\"${escapeJson(stack)}\"" else "null").append(",")
                append("\"timestamp\":\"").append(timestamp).append("\",")
                append("\"deviceId\":\"").append(escapeJson(deviceId)).append("\"")
                append("}")
            }

            connection.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { writer ->
                    writer.write(jsonPayload)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.w(TAG, "doSendToServer: HTTP $responseCode from $baseUrl/api/log")
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "doSendToServer: Failed to send log to $serverIp — ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Flushes all pending log entries to the server.
     * Must be called from the executor thread — does NOT wrap in executor.execute
     * to avoid deadlock (single-thread executor would block on itself).
     */
    private fun flushPendingLogs() {
        val serverIp = cachedServerIp ?: return
        val deviceId = cachedDeviceId ?: "unknown-android-device"

        var entry = pendingLogs.poll()
        while (entry != null) {
            doSendToServer(serverIp, deviceId, entry.level, entry.message, entry.stack, entry.timestamp)
            entry = pendingLogs.poll()
        }
        Log.d(TAG, "Flushed pending log buffer to server")
    }

    private fun startSubnetDiscovery() {
        if (!isDiscovering.compareAndSet(false, true)) return // already running
        lastDiscoveryAttemptTime = System.currentTimeMillis()

        executor.execute discovery@{
            try {
                Log.d(TAG, "Starting autonomous subnet discovery...")

                val subnetPrefix = detectSubnetPrefix()
                if (subnetPrefix == null) {
                    Log.w(TAG, "Could not detect local subnet. Discovery aborted.")
                    isDiscovering.set(false)
                    return@discovery
                }

                showToast("Subnet detected: $subnetPrefix")
                Log.i(TAG, "Subnet discovery result: prefix=$subnetPrefix, will scan ${subnetPrefix}1-254 on ports 5000, 5001")

                val discoveryExecutor = Executors.newFixedThreadPool(20)
                val found = AtomicBoolean(false)
                val latch = CountDownLatch(254)

                for (i in 1..254) {
                    val candidateIp = "$subnetPrefix$i"
                    discoveryExecutor.execute scan@{
                        try {
                            if (found.get()) { latch.countDown(); return@scan }

                            for (port in intArrayOf(5000, 5001)) {
                                if (found.get()) break
                                try {
                                    // Use raw socket HTTP to bypass Android Network Security Config.
                                    // NSC only whitelists ~35 exact IPs; raw sockets are not subject to NSC rules.
                                    val socket = Socket()
                                    socket.connect(InetSocketAddress(candidateIp, port), 600)
                                    socket.soTimeout = 600

                                    val output = socket.getOutputStream()
                                    val input = socket.getInputStream()

                                    val httpGet = "GET /api/config HTTP/1.1\r\nHost: $candidateIp:$port\r\nConnection: close\r\n\r\n"
                                    output.write(httpGet.toByteArray(Charsets.UTF_8))
                                    output.flush()

                                    // Read full HTTP response
                                    val response = StringBuilder()
                                    val buffer = ByteArray(4096)
                                    try {
                                        while (true) {
                                            val bytesRead = input.read(buffer)
                                            if (bytesRead == -1) break
                                            response.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                                        }
                                    } catch (_: Exception) {
                                        // Timeout or read error — acceptable for probes
                                    }

                                    socket.close()

                                    val responseStr = response.toString()
                                    // Check for HTTP 200 status
                                    if (responseStr.startsWith("HTTP/1.1 200") || responseStr.startsWith("HTTP/1.0 200")) {
                                        val bodyStart = responseStr.indexOf("\r\n\r\n")
                                        if (bodyStart >= 0) {
                                            val body = responseStr.substring(bodyStart + 4)
                                            val json = JSONObject(body)
                                            val status = json.optString("status", "")
                                            val service = json.optString("service", "")
                                            if (status == "active" || service.contains("MosqueDND", ignoreCase = true)) {
                                                // Never let a late discovery hit overwrite an IP the user pinned manually
                                                if (!manualOverrideActive.get() && found.compareAndSet(false, true)) {
                                                    val discoveredIp = "http://$candidateIp:$port"
                                                    cachedServerIp = discoveredIp
                                                    appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                                        ?.edit()?.putString(KEY_SERVER_IP, discoveredIp)?.apply()
                                                    Log.d(TAG, "✅ Server discovered: $discoveredIp")
                                                    showToast("Server found: $discoveredIp")
                                                    flushPendingLogs()
                                                    isDiscovering.set(false)
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Port not open or not our server, try next
                                }
                            }
                        } catch (e: Exception) {
                            // Skip this candidate
                        } finally {
                            latch.countDown()
                        }
                    }
                }

                latch.await(30, TimeUnit.SECONDS)
                discoveryExecutor.shutdownNow()

                if (!found.get()) {
                    Log.w(TAG, "⚠️ No telemetry server found on subnet $subnetPrefix. Will retry in ${RETRY_COOLDOWN_MS / 1000}s.")
                    // Tell the user WHICH subnet was scanned and that a manual override exists.
                    // Override hint only matters when the user has not already pinned an IP.
                    if (!manualOverrideActive.get()) {
                        showToast("No server found on ${subnetPrefix}*. Set log server IP in Settings")
                    }
                    isDiscovering.set(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Subnet discovery failed", e)
                isDiscovering.set(false)
            }
        }
    }

    private fun detectSubnetPrefix(): String? {
        // PRIMARY: Use WifiManager to get the actual Wi-Fi interface IP.
        // NetworkInterface.getNetworkInterfaces() returns interfaces in arbitrary order,
        // and on Android the cellular interface (10.x.x.x) often appears before Wi-Fi.
        // WifiManager always returns the active Wi-Fi connection, bypassing the ordering problem.
        try {
            val context = appContext
            if (context != null) {
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                if (wifiManager != null) {
                    val dhcpInfo = wifiManager.dhcpInfo
                    val ip = dhcpInfo.ipAddress
                    if (ip != 0) {
                        val subnet = String.format(
                            "%d.%d.%d.",
                            ip and 0xff,
                            (ip shr 8) and 0xff,
                            (ip shr 16) and 0xff
                        )
                        Log.i(TAG, "detectSubnetPrefix: WifiManager returned IP 0x${Integer.toHexString(ip)}, subnet=$subnet")
                        // Accept private subnets and common emulator subnets
                        if (subnet.startsWith("192.168.") || subnet.startsWith("10.0.2.") || subnet.startsWith("10.0.3.")) {
                            Log.i(TAG, "detectSubnetPrefix: Using WiFi subnet from WifiManager: $subnet")
                            return subnet
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "detectSubnetPrefix: WifiManager approach failed: ${e.message}")
        }

        // SECONDARY: Use ConnectivityManager + LinkProperties (API 23+),
        // which is more reliable than the deprecated WifiManager.dhcpInfo on Android 12+.
        try {
            val ctx = appContext
            if (ctx != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val linkProps = cm?.getLinkProperties(cm.activeNetwork)
                val wifiAddr = linkProps?.linkAddresses?.firstOrNull {
                    val addr = it.address.hostAddress ?: ""
                    addr.startsWith("192.168.") || addr.startsWith("10.0.2.") || addr.startsWith("10.0.3.")
                }
                if (wifiAddr != null) {
                    val ip = wifiAddr.address.hostAddress ?: ""
                    val prefix = ip.substringBeforeLast(".") + "."
                    Log.i(TAG, "detectSubnetPrefix: ConnectivityManager found WiFi IP: $ip, subnet=$prefix")
                    return prefix
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "detectSubnetPrefix: ConnectivityManager approach failed: ${e.message}")
        }

        // FALLBACK: NetworkInterface enumeration
        Log.i(TAG, "detectSubnetPrefix: Falling back to NetworkInterface enumeration")
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                for (address in networkInterface.inetAddresses) {
                    val ip = address.hostAddress ?: continue
                    if (ip.startsWith("192.168.") && !ip.endsWith(".0") && !ip.endsWith(".255")) {
                        Log.i(TAG, "detectSubnetPrefix: NetworkInterface fallback selected ${networkInterface.name} -> ${ip.substringBeforeLast(".")}.")
                        return ip.substringBeforeLast(".") + "."
                    }
                    if (ip.startsWith("10.") && !ip.endsWith(".0") && !ip.endsWith(".255")) {
                        Log.i(TAG, "detectSubnetPrefix: NetworkInterface fallback selected ${networkInterface.name} -> ${ip.substringBeforeLast(".")}.")
                        return ip.substringBeforeLast(".") + "."
                    }
                    if (ip.startsWith("172.")) {
                        val secondOctet = ip.split(".")[1].toIntOrNull() ?: continue
                        if (secondOctet in 16..31 && !ip.endsWith(".0") && !ip.endsWith(".255")) {
                            Log.i(TAG, "detectSubnetPrefix: NetworkInterface fallback selected ${networkInterface.name} -> ${ip.substringBeforeLast(".")}.")
                            return ip.substringBeforeLast(".") + "."
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "detectSubnetPrefix: Failed to detect subnet via NetworkInterface", e)
        }

        // Fallback: try common subnets
        Log.w(TAG, "detectSubnetPrefix: No interface found, falling back to default 192.168.1.")
        return "192.168.1."
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = android.util.Log.getStackTraceString(throwable)
                val serverIp = cachedServerIp
                val deviceId = cachedDeviceId ?: "unknown-android-device"
                
                if (serverIp != null) {
                    try {
                        val baseUrl = if (serverIp.startsWith("http")) serverIp else "http://$serverIp:$DEFAULT_PORT"
                        val url = URL("$baseUrl/api/log")
                        val connection = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 1500
                            readTimeout = 1500
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        }
                        val timestamp = dateFormat.format(Date())
                        val payload = "{\"level\":\"FATAL\",\"message\":\"Uncaught exception in thread ${thread.name}: ${throwable.message}\",\"stack\":\"${escapeJson(stackTrace)}\",\"timestamp\":\"$timestamp\",\"deviceId\":\"$deviceId\"}"
                        
                        connection.outputStream.use { os ->
                            OutputStreamWriter(os, "UTF-8").use { writer ->
                                writer.write(payload)
                                writer.flush()
                            }
                        }
                        connection.responseCode
                        connection.disconnect()
                    } catch (e: Exception) {
                        // Ignore network fail during crash
                    }
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun showToast(message: String) {
        mainHandler.post {
            appContext?.let { ctx ->
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun escapeJson(string: String): String {
        return string
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\u0008", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
