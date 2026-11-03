package com.mosquednd.util

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RemoteCommandTest {

    @Test
    fun `test allow-list validation for remote commands`() {
        val (successPing, resPing) = RemoteCommandManager.executeLocalCommand("ping", JSONObject())
        assertTrue(successPing)
        assertEquals("pong", resPing.getString("status"))

        val (successInvalid, resInvalid) = RemoteCommandManager.executeLocalCommand("drop_database", JSONObject())
        assertFalse(successInvalid)
        assertTrue(resInvalid.getString("error").contains("not allowed"))
    }

    @Test
    fun `test set_log_level remote command execution`() {
        val params = JSONObject().apply { put("level", "DEBUG") }
        val (success, result) = RemoteCommandManager.executeLocalCommand("set_log_level", params)
        assertTrue(success)
        assertEquals("DEBUG", result.getString("logLevelSet"))
    }

    @Test
    fun `test set_sampling_rate remote command execution`() {
        val params = JSONObject().apply { put("intervalMs", 15000L) }
        val (success, result) = RemoteCommandManager.executeLocalCommand("set_sampling_rate", params)
        assertTrue(success)
        assertEquals(15000L, result.getLong("samplingRateMsSet"))
    }

    @Test
    fun `test toggle_category remote command execution`() {
        val params = JSONObject().apply {
            put("category", "NetworkLog")
            put("enabled", false)
        }
        val (success, result) = RemoteCommandManager.executeLocalCommand("toggle_category", params)
        assertTrue(success)
        assertEquals("NetworkLog", result.getString("category"))
        assertFalse(result.getBoolean("enabled"))
    }

    @Test
    fun `test request_memory_dump remote command execution`() {
        val (success, result) = RemoteCommandManager.executeLocalCommand("request_memory_dump", JSONObject())
        assertTrue(success)
        assertTrue(result.has("totalMemoryMB"))
        assertTrue(result.has("usedMemoryMB"))
        assertTrue(result.has("freeMemoryMB"))
    }

    @Test
    fun `test request_state_dump remote command execution`() {
        val (success, result) = RemoteCommandManager.executeLocalCommand("request_state_dump", JSONObject())
        assertTrue(success)
        assertTrue(result.has("connectionState"))
        assertTrue(result.has("droppedEvents"))
    }
}
