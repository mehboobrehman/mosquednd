package com.mosquednd.updater

import org.junit.Assert.*
import org.junit.Test

class GitHubReleaseRepositoryTest {

    @Test
    fun testParseChecksum() {
        val repository = GitHubReleaseRepository()
        val checksumText = """
            e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  MosqueDND-release-v1.0.12.apk
            a5f1c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b123 *MosqueDND-release-v1.0.11.apk
        """.trimIndent()

        val hash1 = repository.parseChecksum(checksumText, "MosqueDND-release-v1.0.12.apk")
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash1)

        val hash2 = repository.parseChecksum(checksumText, "MosqueDND-release-v1.0.11.apk")
        assertEquals("a5f1c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b123", hash2)
    }

    @Test(expected = java.io.IOException::class)
    fun testParseChecksumNotFound() {
        val repository = GitHubReleaseRepository()
        val checksumText = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890  other.apk"
        repository.parseChecksum(checksumText, "MosqueDND-release-v1.0.12.apk")
    }
}
