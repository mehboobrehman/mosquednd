package com.mosquednd.util

import java.util.regex.Pattern

/**
 * RedactionEngine / RedactionUtils:
 * Provides robust redaction for credentials, API tokens, Authorization headers,
 * email addresses, phone numbers, precise GPS locations, and custom configurable regex patterns.
 */
object RedactionUtils {
    private val customRules = mutableListOf<Pair<Pattern, String>>()

    fun addCustomRule(regex: String, replacement: String) {
        customRules.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE) to replacement)
    }

    fun clearCustomRules() {
        customRules.clear()
    }

    private val PASSWORD_PATTERN = Pattern.compile(
        """(?i)(password|passwd|pwd|secret|api_key|apikey|auth_token|accesstoken)\s*[:=]\s*([^\s,}\"]+)""",
        Pattern.CASE_INSENSITIVE
    )
    private val BEARER_PATTERN = Pattern.compile(
        """(?i)(Authorization\s*[:=]\s*Bearer\s+)([a-zA-Z0-9_\-\.]+)""",
        Pattern.CASE_INSENSITIVE
    )
    private val EMAIL_PATTERN = Pattern.compile(
        """[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""
    )
    private val PHONE_PATTERN = Pattern.compile(
        """\+?[0-9]{1,4}?[-.\s]?\(?[0-9]{1,3}?\)?[-.\s]?[0-9]{3,4}[-.\\s]?[0-9]{4}"""
    )
    private val GPS_PATTERN = Pattern.compile(
        """(?i)\b(lat(?:itude)?|lng|longitude)\s*[:=]\s*-?\d+\.\d+\b|\b-?[1-9]?\d\.\d{4,},\s*-?1?[0-8]?\d\.\d{4,}\b"""
    )

    fun redact(input: String?): String {
        if (input == null) return ""
        if (input.isEmpty()) return ""
        var result: String? = input

        // 1. Credentials & Secrets
        result = PASSWORD_PATTERN.matcher(result!!).replaceAll("$1: \"[REDACTED]\"")

        // 2. Authorization / Bearer tokens
        result = BEARER_PATTERN.matcher(result).replaceAll("$1[REDACTED]")

        // 3. Email addresses
        result = EMAIL_PATTERN.matcher(result).replaceAll("[REDACTED_EMAIL]")

        // 4. Phone numbers
        result = PHONE_PATTERN.matcher(result).replaceAll("[REDACTED_PHONE]")

        // 5. Precise GPS locations
        result = GPS_PATTERN.matcher(result).replaceAll("[REDACTED_GPS]")

        // 6. Custom rules
        for ((pattern, replacement) in customRules) {
            result = pattern.matcher(result).replaceAll(replacement)
        }

        return result ?: ""
    }

    fun redactMap(map: Map<String, Any?>): Map<String, Any?> {
        val redacted = mutableMapOf<String, Any?>()
        for ((key, value) in map) {
            val lowerKey = key.lowercase()
            if (lowerKey.contains("password") || lowerKey.contains("secret") || lowerKey.contains("token") || lowerKey.contains("authorization") || lowerKey.contains("apikey")) {
                redacted[key] = "[REDACTED]"
            } else if (value is String) {
                redacted[key] = redact(value)
            } else if (value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                redacted[key] = redactMap(value as Map<String, Any?>)
            } else if (value is List<*>) {
                redacted[key] = value.map { item ->
                    when (item) {
                        is String -> redact(item)
                        is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            redactMap(item as Map<String, Any?>)
                        }
                        else -> item
                    }
                }
            } else {
                redacted[key] = value
            }
        }
        return redacted
    }
}
