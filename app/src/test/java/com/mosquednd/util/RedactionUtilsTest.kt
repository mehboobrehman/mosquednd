package com.mosquednd.util

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RedactionUtilsTest {

    @Before
    fun setUp() {
        RedactionUtils.clearCustomRules()
    }

    @Test
    fun `test password and credential redaction`() {
        val input = "User login failed with password=secret123 and api_key=abc123xyz"
        val redacted = RedactionUtils.redact(input)
        assertTrue(redacted.contains("password: \"[REDACTED]\""))
        assertTrue(redacted.contains("api_key: \"[REDACTED]\""))
        assertFalse(redacted.contains("secret123"))
        assertFalse(redacted.contains("abc123xyz"))
    }

    @Test
    fun `test authorization header redaction`() {
        val input = "Authorization: Bearer secretToken999 header value"
        val redacted = RedactionUtils.redact(input)
        assertTrue(redacted.contains("Authorization: Bearer [REDACTED]"))
        assertFalse(redacted.contains("secretToken999"))
    }

    @Test
    fun `test email address redaction`() {
        val input = "Contact user at [email protected] or support@mosquednd.org for help."
        val redacted = RedactionUtils.redact(input)
        assertTrue(redacted.contains("[REDACTED_EMAIL]"))
        assertFalse(redacted.contains("test.user@example.com"))
        assertFalse(redacted.contains("support@mosquednd.org"))
    }

    @Test
    fun `test phone number redaction`() {
        val input = "Call +1-555-123-4567 or 555-0199 for emergency."
        val redacted = RedactionUtils.redact(input)
        assertTrue(redacted.contains("[REDACTED_PHONE]"))
        assertFalse(redacted.contains("+1-555-123-4567"))
    }

    @Test
    fun `test precise GPS location redaction`() {
        val input = "User location lat=37.7749, lng=-122.4194 and coordinate 31.5204, 74.3587 found."
        val redacted = RedactionUtils.redact(input)
        assertTrue(redacted.contains("[REDACTED_GPS]"))
        assertFalse(redacted.contains("37.7749"))
        assertFalse(redacted.contains("31.5204"))
    }

    @Test
    fun `test custom configurable regex rules`() {
        RedactionUtils.addCustomRule("SECRET_CODE-\\d+", "[REDACTED_SECRET]")
        val input = "Confidential code is SECRET_CODE-98765."
        val redacted = RedactionUtils.redact(input)
        assertEquals("Confidential code is [REDACTED_SECRET].", redacted)
    }

    @Test
    fun `test redactMap with sensitive keys and values`() {
        val map = mapOf(
            "username" to "john_doe",
            "password" to "superSecret",
            "email" to "john@example.com",
            "nested" to mapOf(
                "apiKey" to "token123",
                "text" to "My email is test@example.com"
            )
        )
        val redactedMap = RedactionUtils.redactMap(map)
        assertEquals("john_doe", redactedMap["username"])
        assertEquals("[REDACTED]", redactedMap["password"])
        assertEquals("[REDACTED_EMAIL]", redactedMap["email"])

        @Suppress("UNCHECKED_CAST")
        val nested = redactedMap["nested"] as Map<String, Any?>
        assertEquals("[REDACTED]", nested["apiKey"])
        assertTrue((nested["text"] as String).contains("[REDACTED_EMAIL]"))
    }
}
