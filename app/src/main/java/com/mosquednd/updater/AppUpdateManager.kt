package com.mosquednd.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume

class AppUpdateManager(
    private val context: Context,
    private val client: OkHttpClient = com.mosquednd.net.NetworkClientProvider.httpClient,
    private val repository: GitHubReleaseRepository = GitHubReleaseRepository(client)
) {
    @Volatile
    private var activeDownloadCall: Call? = null

    private val updatesDir: File by lazy {
        File(context.cacheDir, "updates").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun checkForUpdate(): Pair<ReleaseInfo, Boolean> = withContext(Dispatchers.IO) {
        val releaseInfo = repository.fetchLatestRelease()
        val currentVersionStr = com.mosquednd.BuildConfig.APP_VERSION
        val currentSemVer = SemVer.parse(currentVersionStr)
            ?: throw IOException("Invalid application version: $currentVersionStr")
        
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
        if (!releaseInfo.apkFilename.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.apk", RegexOption.IGNORE_CASE))) {
            throw SecurityException("Invalid APK filename")
        }
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            throw IOException("Unable to create update cache directory")
        }

        val targetFile = File(updatesDir, releaseInfo.apkFilename)
        val tempFile = File(updatesDir, "${releaseInfo.apkFilename}.tmp")

        if (tempFile.exists() && !tempFile.delete()) {
            throw IOException("Unable to clear the previous incomplete update download")
        }

        var downloadComplete = false
        try {
            val request = Request.Builder().url(releaseInfo.apkUrl).build()
            val call = client.newCall(request)
            activeDownloadCall = call
            try {
                executeCancellable(call).use { response ->
                    if (!response.request.url.isHttps) {
                        throw SecurityException("APK download redirected to an insecure URL")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("Download failed with HTTP ${response.code}: ${response.message}")
                    }
                    val body = response.body ?: throw IOException("Empty response body during APK download")
                    val contentLength = body.contentLength()

                    FileOutputStream(tempFile).use { fos ->
                        var bytesDownloaded = 0L
                        var lastReportedPercent = -1
                        var lastReportedBytes = 0L
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val bytesRead = input.read(buffer)
                                if (bytesRead == -1) break
                                bytesDownloaded += bytesRead
                                if (releaseInfo.apkSize > 0 && bytesDownloaded > releaseInfo.apkSize) {
                                    throw IOException("Downloaded APK exceeds the published asset size")
                                }
                                fos.write(buffer, 0, bytesRead)
                                val progress = if (contentLength > 0) {
                                    (bytesDownloaded.toFloat() / contentLength.toFloat()).coerceIn(0f, 0.99f)
                                } else {
                                    -1f
                                }
                                val shouldReport = if (contentLength > 0) {
                                    val percent = (progress * 100).toInt()
                                    if (percent > lastReportedPercent || bytesDownloaded == contentLength) {
                                        lastReportedPercent = percent
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    bytesDownloaded - lastReportedBytes >= 256 * 1024L
                                }
                                if (shouldReport) {
                                    lastReportedBytes = bytesDownloaded
                                    onProgress(progress, bytesDownloaded, contentLength)
                                }
                            }
                        }
                        fos.flush()
                        currentCoroutineContext().ensureActive()
                        if (releaseInfo.apkSize > 0 && bytesDownloaded != releaseInfo.apkSize) {
                            throw IOException("Downloaded APK size does not match the published asset")
                        }
                    }
                }
            } finally {
                activeDownloadCall = null
            }

            val checksumResult = AppUpdateSecurity.verifyChecksum(tempFile, releaseInfo.expectedSha256)
            if (checksumResult is AppUpdateSecurity.SecurityResult.Error) {
                throw SecurityException(checksumResult.message)
            }
            currentCoroutineContext().ensureActive()

            val signatureResult = AppUpdateSecurity.verifySignatures(context, tempFile)
            if (signatureResult is AppUpdateSecurity.SecurityResult.Error) {
                throw SecurityException(signatureResult.message)
            }
            currentCoroutineContext().ensureActive()
            onProgress(1f, releaseInfo.apkSize, releaseInfo.apkSize)

            if (targetFile.exists() && !targetFile.delete()) {
                throw IOException("Unable to replace the previous update APK")
            }
            if (!tempFile.renameTo(targetFile)) {
                throw IOException("Failed to atomically finalize update APK file")
            }
            downloadComplete = true
            targetFile
        } finally {
            activeDownloadCall = null
            if (!downloadComplete) {
                tempFile.delete()
            }
        }
    }

    fun cancelDownload() {
        activeDownloadCall?.cancel()
    }

    private suspend fun executeCancellable(call: Call): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _ -> response.close() }
                }
            })
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
