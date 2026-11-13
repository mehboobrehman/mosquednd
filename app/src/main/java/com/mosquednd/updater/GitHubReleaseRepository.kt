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
                it.name.equals(expectedApkFilename, ignoreCase = true) || 
                it.name.matches(Regex("MosqueDND-release-v.*\\.apk", RegexOption.IGNORE_CASE)) 
            } ?: throw IOException("No matching APK asset found for version $tagName")

            val checksumAsset = assets.firstOrNull { it.name.equals("SHA256SUMS", ignoreCase = true) }
                ?: throw IOException("No SHA256SUMS checksum asset found in release")

            if (!apkAsset.browserDownloadUrl.startsWith("https://", ignoreCase = true) ||
                !checksumAsset.browserDownloadUrl.startsWith("https://", ignoreCase = true)) {
                throw SecurityException("Insecure download URL detected (non-HTTPS)")
            }

            // Download SHA256SUMS
            val checksumRequest = Request.Builder().url(checksumAsset.browserDownloadUrl).build()
            val expectedSha256 = client.newCall(checksumRequest).execute().use { checksumResp ->
                if (!checksumResp.isSuccessful) {
                    throw IOException("Failed to download SHA256SUMS: ${checksumResp.code}")
                }
                val checksumText = checksumResp.body?.string() ?: throw IOException("Empty SHA256SUMS")
                parseChecksum(checksumText, apkAsset.name)
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
                val filename = parts[1].trim().removePrefix("*")
                if (filename.equals(targetFilename, ignoreCase = true) || 
                    targetFilename.contains(filename, ignoreCase = true) || 
                    filename.contains(targetFilename, ignoreCase = true)) {
                    if (hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                        return hash.lowercase()
                    }
                }
            } else if (parts.size == 1 && parts[0].length == 64) {
                return parts[0].trim().lowercase()
            }
        }
        throw IOException("Checksum for $targetFilename not found in SHA256SUMS")
    }

    suspend fun fetchLatestReleaseSuspend(): ReleaseInfo =
        withContext(Dispatchers.IO) {
            fetchLatestRelease()
        }
}
