package com.mosquednd.util

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * RemoteCommandManager: Receives, validates against an allow-list, executes, and acknowledges
 * host-to-device remote commands (e.g., change log level, change sampling rates, toggle categories, memory/state dump).
 */
object RemoteCommandManager {
    private const val TAG = "RemoteCommandManager"
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "AgentLogger-RemoteCommands").apply { isDaemon = true }
    }
    private var isPolling = false

    private val allowedCommands = setOf(
        "set_log_level",
        "set_sampling_rate",
        "toggle_category",
        "request_memory_dump",
        "request_state_dump",
        "ping"
    )

    fun startPolling(serverIpProvider: () -> String?, deviceIdProvider: () -> String?, authTokenProvider: () -> String?) {
        if (isPolling) return
        isPolling = true

        scheduler.scheduleAtFixedRate({
            val serverIp = serverIpProvider() ?: return@scheduleAtFixedRate
            val deviceId = deviceIdProvider() ?: return@scheduleAtFixedRate
            val authToken = authTokenProvider()

            pollAndExecuteCommands(serverIp, deviceId, authToken)
        }, 10L, 15L, TimeUnit.SECONDS)
    }

    fun stopPolling() {
        if (!isPolling) return
        isPolling = false
        try {
            scheduler.shutdown()
        } catch (_: Exception) {}
    }

    private fun pollAndExecuteCommands(serverIp: String, deviceId: String, authToken: String?) {
        try {
            val url = URL("$serverIp/api/commands?deviceId=$deviceId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                authToken?.let { setRequestProperty("X-Telemetry-Token", it) }
            }

            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                if (body.isNotBlank()) {
                    val jsonArray = try {
                        JSONArray(body)
                    } catch (_: Exception) {
                        try {
                            val obj = JSONObject(body)
                            obj.optJSONArray("commands") ?: JSONArray()
                        } catch (_: Exception) {
                            JSONArray()
                        }
                    }

                    for (i in 0 until jsonArray.length()) {
                        val cmdObj = jsonArray.optJSONObject(i) ?: continue
                        val cmdId = cmdObj.optString("id", "cmd-${System.currentTimeMillis()}")
                        val commandName = cmdObj.optString("command", "")
                        val params = cmdObj.optJSONObject("params") ?: JSONObject()

                        handleCommand(serverIp, deviceId, authToken, cmdId, commandName, params)
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.d(TAG, "Failed polling remote commands: ${e.message}")
        }
    }

    fun executeLocalCommand(commandName: String, params: JSONObject): Pair<Boolean, JSONObject> {
        if (!allowedCommands.contains(commandName)) {
            val errResult = JSONObject().apply { put("error", "Command not allowed: $commandName") }
            return false to errResult
        }

        val result = JSONObject()
        try {
            when (commandName) {
                "ping" -> {
                    result.put("status", "pong")
                    result.put("timestamp", System.currentTimeMillis())
                }
                "set_log_level" -> {
                    val level = params.optString("level", "INFO")
                    result.put("logLevelSet", level)
                    AgentLogger.i("Remote command executed: set_log_level to $level", TAG)
                }
                "set_sampling_rate" -> {
                    val interval = params.optLong("intervalMs", 30_000L)
                    result.put("samplingRateMsSet", interval)
                    AgentLogger.i("Remote command executed: set_sampling_rate to ${interval}ms", TAG)
                }
                "toggle_category" -> {
                    val category = params.optString("category", "all")
                    val enabled = params.optBoolean("enabled", true)
                    result.put("category", category)
                    result.put("enabled", enabled)
                    AgentLogger.i("Remote command executed: toggle_category $category enabled=$enabled", TAG)
                }
                "request_memory_dump" -> {
                    val runtime = Runtime.getRuntime()
                    val total = runtime.totalMemory() / (1024 * 1024)
                    val free = runtime.freeMemory() / (1024 * 1024)
                    val used = total - free
                    result.put("totalMemoryMB", total)
                    result.put("usedMemoryMB", used)
                    result.put("freeMemoryMB", free)
                    AgentLogger.i("Remote command executed: memory dump requested", TAG)
                }
                "request_state_dump" -> {
                    val screen = TelemetryLifecycleCallbacks.currentScreenName ?: "Unknown"
                    val state = AgentLogger.getConnectionState().name
                    result.put("currentScreen", screen)
                    result.put("connectionState", state)
                    result.put("droppedEvents", AgentLogger.getDroppedEventCount())
                    AgentLogger.i("Remote command executed: state dump requested", TAG)
                }
            }
            return true to result
        } catch (e: Exception) {
            val errResult = JSONObject().apply { put("error", e.message ?: "Unknown execution error") }
            return false to errResult
        }
    }

    private fun handleCommand(serverIp: String, deviceId: String, authToken: String?, cmdId: String, commandName: String, params: JSONObject) {
        val (success, result) = executeLocalCommand(commandName, params)
        acknowledgeCommand(serverIp, deviceId, authToken, cmdId, if (success) "success" else "error", result)
    }

    private fun acknowledgeCommand(serverIp: String, deviceId: String, authToken: String?, cmdId: String, status: String, resultData: JSONObject) {
        try {
            val url = URL("$serverIp/api/commands/ack")
            val ackPayload = JSONObject().apply {
                put("deviceId", deviceId)
                put("commandId", cmdId)
                put("status", status)
                put("result", resultData)
                put("timestamp", System.currentTimeMillis())
            }

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 3000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                authToken?.let { setRequestProperty("X-Telemetry-Token", it) }
            }

            conn.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { writer ->
                    writer.write(ackPayload.toString())
                    writer.flush()
                }
            }
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            Log.d(TAG, "Failed acknowledging command $cmdId: ${e.message}")
        }
    }
}
