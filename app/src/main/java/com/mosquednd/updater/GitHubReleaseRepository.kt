package com.mosquednd.updater

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class ReleaseInfo(
    val tagName: String,
    val semVer: SemVer,
    val releaseName: String?,
    val releaseNotes: String?,
    val htmlUrl: String,
    val apkUrl: String,
    val apkFilename: String,
    val expectedSha256: String,
    val apkSize: Long
)

class GitHubReleaseRepository(
    private val client: OkHttpClient = com.mosquednd.net.NetworkClientProvider.httpClient,
    private val baseUrl: String = "https://api.github.com/repos/mehboobrehman/mosquednd/releases/latest"
) {
    private val gson = Gson()

    fun fetchLatestRelease(): ReleaseInfo {
        if (!baseUrl.startsWith("https://", ignoreCase = true)) {
            throw SecurityException("HTTPS required for GitHub releases API")
        }

        val request = Request.Builder()
            .url(baseUrl)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "MosqueDND-Updater")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.request.url.isHttps) {
                throw SecurityException("GitHub releases API redirected to an insecure URL")
            }
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch latest release: ${response.code} ${response.message}")
            }
            val bodyString = response.body?.string() ?: throw IOException("Empty response body from GitHub API")
            val releaseDto = gson.fromJson(bodyString, GitHubReleaseDto::class.java)
                ?: throw IOException("Invalid release JSON structure")

            val tagName = releaseDto.tagName ?: throw IOException("Release tag_name is missing")
            val semVer = SemVer.parse(tagName) ?: throw IOException("Unable to parse SemVer from tag: $tagName")
            val assets = releaseDto.assets ?: emptyList()

            val expectedApkFilename = "MosqueDND-release-v${semVer.major}.${semVer.minor}.${semVer.patch}.apk"
            val apkAsset = assets.firstOrNull { 
                it.name.equals(expectedApkFilename, ignoreCase = true)
            } ?: throw IOException("No matching APK asset found for version $tagName")
            if (apkAsset.size <= 0L) {
                throw IOException("APK release asset has an invalid size")
            }

            val checksumAsset = assets.firstOrNull { it.name.equals("SHA256SUMS", ignoreCase = true) }

            if (!apkAsset.browserDownloadUrl.startsWith("https://", ignoreCase = true)) {
                throw SecurityException("Insecure download URL detected (non-HTTPS)")
            }

            val expectedSha256 = apkAsset.digest?.let(::parseGitHubDigest) ?: run {
                val manifest = checksumAsset
                    ?: throw IOException("Release has no GitHub asset digest or SHA256SUMS manifest")
                if (!manifest.browserDownloadUrl.startsWith("https://", ignoreCase = true)) {
                    throw SecurityException("Insecure checksum download URL (HTTPS required)")
                }
                val checksumRequest = Request.Builder().url(manifest.browserDownloadUrl).build()
                client.newCall(checksumRequest).execute().use { checksumResp ->
                    if (!checksumResp.request.url.isHttps) {
                        throw SecurityException("SHA256SUMS redirected to an insecure URL")
                    }
                    if (!checksumResp.isSuccessful) {
                        throw IOException("Failed to download SHA256SUMS: ${checksumResp.code}")
                    }
                    val checksumText = checksumResp.body?.string() ?: throw IOException("Empty SHA256SUMS")
                    parseChecksum(checksumText, apkAsset.name)
                }
            }

            return ReleaseInfo(
                tagName = tagName,
                semVer = semVer,
                releaseName = releaseDto.name,
                releaseNotes = releaseDto.body,
                htmlUrl = releaseDto.htmlUrl ?: "https://github.com/mehboobrehman/mosquednd/releases/latest",
                apkUrl = apkAsset.browserDownloadUrl,
                apkFilename = apkAsset.name,
                expectedSha256 = expectedSha256,
                apkSize = apkAsset.size
            )
        }
    }

    fun parseChecksum(checksumText: String, targetFilename: String): String {
        val lines = checksumText.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            if (parts.size == 2) {
                val hash = parts[0].trim()
                val filename = parts[1].trim().removePrefix("*").substringAfterLast('/')
                if (filename.equals(targetFilename, ignoreCase = true)) {
                    if (hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                        return hash.lowercase()
                    }
                }
            }
        }
        throw IOException("Checksum for $targetFilename not found in SHA256SUMS")
    }

    fun parseGitHubDigest(digest: String?): String {
        val hash = digest
            ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
            ?.substringAfter(':')
        if (hash == null || hash.length != 64 || hash.any { it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' }) {
            throw IOException("Missing or invalid GitHub SHA-256 asset digest")
        }
        return hash.lowercase()
    }

    suspend fun fetchLatestReleaseSuspend(): ReleaseInfo =
        withContext(Dispatchers.IO) {
            fetchLatestRelease()
        }
}
