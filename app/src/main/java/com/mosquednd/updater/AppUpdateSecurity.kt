package com.mosquednd.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object AppUpdateSecurity {

    sealed class SecurityResult {
        object Success : SecurityResult()
        data class Error(val message: String) : SecurityResult()
    }

    fun verifyChecksum(apkFile: File, expectedSha256: String?): SecurityResult {
        if (expectedSha256.isNullOrBlank()) {
            return SecurityResult.Error("Missing expected SHA-256 checksum (SHA256SUMS required)")
        }
        if (!apkFile.exists()) {
            return SecurityResult.Error("Downloaded APK file does not exist")
        }

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(apkFile).use { fis ->
                val buffer = ByteArray(8192)
                var read = fis.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = fis.read(buffer)
                }
            }
            val computedHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!computedHash.equals(expectedSha256.trim(), ignoreCase = true)) {
                return SecurityResult.Error("SHA-256 checksum mismatch! Expected: $expectedSha256, Computed: $computedHash")
            }
            return SecurityResult.Success
        } catch (e: Exception) {
            return SecurityResult.Error("Exception computing SHA-256: ${e.message}")
        }
    }

    fun verifySignatures(context: Context, apkFile: File): SecurityResult {
        val packageManager = context.packageManager
        val packageName = context.packageName

        try {
            val installedSignatures = getAppSignatures(packageManager, packageName)
            if (installedSignatures.isEmpty()) {
                return SecurityResult.Error("Unable to retrieve installed app signatures")
            }

            val apkPackageInfo = packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_SIGNATURES
                }
            ) ?: return SecurityResult.Error("Failed to parse downloaded APK package info")

            val apkSignatures = getPackageSignatures(apkPackageInfo)
            if (apkSignatures.isEmpty()) {
                return SecurityResult.Error("Downloaded APK has no signatures")
            }

            val installedSet = installedSignatures.map { it.toCharsString() }.toSet()
            val apkSet = apkSignatures.map { it.toCharsString() }.toSet()

            if (installedSet != apkSet) {
                return SecurityResult.Error("Signing certificate mismatch between installed app and update APK!")
            }

            return SecurityResult.Success
        } catch (e: Exception) {
            return SecurityResult.Error("Signature verification failed: ${e.message}")
        }
    }

    private fun getAppSignatures(pm: PackageManager, packageName: String): Array<out android.content.pm.Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = pkgInfo.signingInfo
            if (signingInfo != null) {
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                emptyArray()
            }
        } else {
            @Suppress("DEPRECATION")
            val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            pkgInfo.signatures ?: emptyArray()
        }
    }

    private fun getPackageSignatures(pkgInfo: PackageInfo): Array<out android.content.pm.Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            if (signingInfo != null) {
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                emptyArray()
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures ?: emptyArray()
        }
    }
}
