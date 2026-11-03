package com.mosquednd.util

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AgentLoggerTest {

    @Test
    fun `test connection state machine valid values`() {
        val currentState = AgentLogger.getConnectionState()
        assertNotNull(currentState)
        val states = AgentLogger.ConnectionState.values()
        assertTrue(states.contains(currentState))
        assertTrue(states.contains(AgentLogger.ConnectionState.DISCONNECTED))
        assertTrue(states.contains(AgentLogger.ConnectionState.CONNECTING))
        assertTrue(states.contains(AgentLogger.ConnectionState.CONNECTED))
        assertTrue(states.contains(AgentLogger.ConnectionState.DEGRADED))
        assertTrue(states.contains(AgentLogger.ConnectionState.OFFLINE))
    }

    @Test
    fun `test telemetry envelope structure and required fields`() {
        val payloadMap = mapOf(
            "level" to "INFO",
            "message" to "Test telemetry message password=secret123",
            "tag" to "TestTag"
        )
        
        val redactedPayload = RedactionUtils.redactMap(payloadMap)
        
        val envelopeMap = mutableMapOf<String, Any>(
            "schemaVersion" to "1.0.0",
            "sessionId" to "session-test-123",
            "deviceId" to "device-test-model",
            "sequenceNumber" to 1L,
            "monotonicTimestamp" to 123456.78,
            "wallClockTimestamp" to "2026-11-04T12:00:00.000Z",
            "messageType" to "log",
            "payload" to redactedPayload
        )

        val jsonString = JSONObject(envelopeMap as Map<*, *>).toString()
        val json = JSONObject(jsonString)

        assertEquals("1.0.0", json.getString("schemaVersion"))
        assertEquals("session-test-123", json.getString("sessionId"))
        assertEquals("device-test-model", json.getString("deviceId"))
        assertEquals(1L, json.getLong("sequenceNumber"))
        assertEquals(123456.78, json.getDouble("monotonicTimestamp"), 0.001)
        assertEquals("2026-11-04T12:00:00.000Z", json.getString("wallClockTimestamp"))
        assertEquals("log", json.getString("messageType"))

        val payload = json.getJSONObject("payload")
        assertEquals("INFO", payload.getString("level"))
        assertTrue(payload.getString("message").contains("[REDACTED]"))
        assertFalse(payload.getString("message").contains("secret123"))
    }

    @Test
    fun `test dropped event counter initial state`() {
        val dropped = AgentLogger.getDroppedEventCount()
        assertTrue(dropped >= 0L)
    }

    @Test
    fun `test normalizeServerUrl via reflection`() {
        val method = AgentLogger::class.java.getDeclaredMethod("normalizeServerUrl", String::class.java)
        method.isAccessible = true

        val res1 = method.invoke(AgentLogger, "192.168.1.50") as String
        assertEquals("http://192.168.1.50:5000", res1)

        val res2 = method.invoke(AgentLogger, "192.168.1.50:8080") as String
        assertEquals("http://192.168.1.50:8080", res2)

        val res3 = method.invoke(AgentLogger, "https://example.com") as String
        assertEquals("https://example.com", res3)
    }
}
