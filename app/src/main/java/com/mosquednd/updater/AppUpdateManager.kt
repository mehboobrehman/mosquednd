package com.mosquednd.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AppUpdateManager(
    private val context: Context,
    private val client: OkHttpClient = com.mosquednd.net.NetworkClientProvider.httpClient,
    private val repository: GitHubReleaseRepository = GitHubReleaseRepository(client)
) {
    private val updatesDir: File by lazy {
        File(context.cacheDir, "updates").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun checkForUpdate(): Pair<ReleaseInfo, Boolean>? = withContext(Dispatchers.IO) {
        val releaseInfo = repository.fetchLatestRelease()
        val currentVersionStr = com.mosquednd.BuildConfig.APP_VERSION
        val currentSemVer = SemVer.parse(currentVersionStr) ?: SemVer(1, 0, 0)
        
        val isNewer = releaseInfo.semVer > currentSemVer
        return@withContext Pair(releaseInfo, isNewer)
    }

    suspend fun downloadAndVerify(
        releaseInfo: ReleaseInfo,
        onProgress: (Float, Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (!releaseInfo.apkUrl.startsWith("https://", ignoreCase = true)) {
            throw SecurityException("Insecure APK download URL (HTTPS required)")
        }

        val targetFile = File(updatesDir, releaseInfo.apkFilename)
        val tempFile = File(updatesDir, "${releaseInfo.apkFilename}.tmp")

        if (tempFile.exists()) tempFile.delete()

        val request = Request.Builder().url(releaseInfo.apkUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed with HTTP ${response.code}: ${response.message}")
            }
            val body = response.body ?: throw IOException("Empty response body during APK download")
            val contentLength = body.contentLength()

            FileOutputStream(tempFile).use { fos ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesDownloaded = 0L
                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1) {
                        fos.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        if (contentLength > 0) {
                            val progress = bytesDownloaded.toFloat() / contentLength.toFloat()
                            onProgress(progress, bytesDownloaded, contentLength)
                        } else {
                            onProgress(-1f, bytesDownloaded, -1L)
                        }
                        bytesRead = input.read(buffer)
                    }
                    fos.flush()
                }
            }
        }

        // Verify SHA-256 Checksum
        val checksumResult = AppUpdateSecurity.verifyChecksum(tempFile, releaseInfo.expectedSha256)
        if (checksumResult is AppUpdateSecurity.SecurityResult.Error) {
            tempFile.delete()
            throw SecurityException(checksumResult.message)
        }

        // Verify Signing Certificate
        val signatureResult = AppUpdateSecurity.verifySignatures(context, tempFile)
        if (signatureResult is AppUpdateSecurity.SecurityResult.Error) {
            tempFile.delete()
            throw SecurityException(signatureResult.message)
        }

        // Atomic finalize: rename tmp to target
        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            throw IOException("Failed to atomically finalize update APK file")
        }

        return@withContext targetFile
    }

    fun getInstallIntent(apkFile: File): Intent {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
