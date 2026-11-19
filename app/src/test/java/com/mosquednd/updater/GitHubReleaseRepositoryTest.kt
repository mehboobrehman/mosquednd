package com.mosquednd.updater

import org.junit.Assert.*
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

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
        val checksumText =
            "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890  prior-MosqueDND-release-v1.0.12.apk"
        repository.parseChecksum(checksumText, "MosqueDND-release-v1.0.12.apk")
    }

    @Test(expected = java.io.IOException::class)
    fun testParseChecksumRequiresFilename() {
        val repository = GitHubReleaseRepository()
        val checksumText = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
        repository.parseChecksum(checksumText, "MosqueDND-release-v1.0.12.apk")
    }

    @Test
    fun fetchLatestReleaseUsesGitHubAssetDigestWhenChecksumManifestIsAbsent() {
        val expectedDigest = "1a24524f2b77338bb63abc8f4a83df9de3849bf96d6e422fd7bb74bd78dba086"
        val releaseJson = """
            {
              "tag_name": "v1.0.14",
              "name": "Release",
              "body": "Release notes",
              "html_url": "https://github.com/mehboobrehman/mosquednd/releases/tag/v1.0.14",
              "assets": [{
                "name": "MosqueDND-release-v1.0.14.apk",
                "browser_download_url": "https://github.com/mehboobrehman/mosquednd/releases/download/v1.0.14/MosqueDND-release-v1.0.14.apk",
                "size": 1234,
                "digest": "sha256:$expectedDigest"
              }]
            }
        """.trimIndent()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(releaseJson.toResponseBody())
                    .build()
            }
            .build()
        val repository = GitHubReleaseRepository(client, "https://api.github.test/releases/latest")

        val release = repository.fetchLatestRelease()

        assertEquals("v1.0.14", release.tagName)
        assertEquals(expectedDigest, release.expectedSha256)
        assertEquals("MosqueDND-release-v1.0.14.apk", release.apkFilename)
    }

    @Test
    fun fetchLatestReleaseFallsBackToChecksumManifestWhenGitHubDigestIsMissing() {
        val expectedDigest = "b".repeat(64)
        val releaseJson = """
            {
              "tag_name": "v1.0.14",
              "assets": [
                {
                  "name": "MosqueDND-release-v1.0.14.apk",
                  "browser_download_url": "https://downloads.github.test/MosqueDND-release-v1.0.14.apk",
                  "size": 1234
                },
                {
                  "name": "SHA256SUMS",
                  "browser_download_url": "https://downloads.github.test/SHA256SUMS",
                  "size": 128
                }
              ]
            }
        """.trimIndent()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val body = if (chain.request().url.encodedPath.endsWith("latest")) {
                    releaseJson
                } else {
                    "$expectedDigest  MosqueDND-release-v1.0.14.apk"
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()
        val repository = GitHubReleaseRepository(client, "https://api.github.test/releases/latest")

        assertEquals(expectedDigest, repository.fetchLatestRelease().expectedSha256)
    }

    @Test
    fun parseGitHubDigestRejectsMissingOrMalformedDigest() {
        val repository = GitHubReleaseRepository()

        assertEquals("a".repeat(64), repository.parseGitHubDigest("sha256:${"a".repeat(64)}"))
        assertThrows(java.io.IOException::class.java) {
            repository.parseGitHubDigest("sha1:${"a".repeat(64)}")
        }
        assertThrows(java.io.IOException::class.java) {
            repository.parseGitHubDigest("sha256:short")
        }
    }
}
