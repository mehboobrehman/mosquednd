package com.mosquednd.updater

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class AppUpdateSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testVerifyChecksumSuccess() {
        val testFile = tempFolder.newFile("test.apk")
        val content = "MosqueDND dummy apk content for testing"
        testFile.writeText(content)

        // Compute expected SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        val expectedHash = digest.digest(content.toByteArray()).joinToString("") { "%02x".format(it) }

        val result = AppUpdateSecurity.verifyChecksum(testFile, expectedHash)
        assertTrue(result is AppUpdateSecurity.SecurityResult.Success)
    }

    @Test
    fun testVerifyChecksumMismatch() {
        val testFile = tempFolder.newFile("test.apk")
        testFile.writeText("some content")

        val wrongHash = "0000000000000000000000000000000000000000000000000000000000000000"
        val result = AppUpdateSecurity.verifyChecksum(testFile, wrongHash)
        assertTrue(result is AppUpdateSecurity.SecurityResult.Error)

        val nullResult = AppUpdateSecurity.verifyChecksum(testFile, null)
        assertTrue(nullResult is AppUpdateSecurity.SecurityResult.Error)
        assertEquals("Missing expected SHA-256 checksum (SHA256SUMS required)", (nullResult as AppUpdateSecurity.SecurityResult.Error).message)
    }
}
