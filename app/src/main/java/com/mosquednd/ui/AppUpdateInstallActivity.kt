package com.mosquednd.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.mosquednd.R
import com.mosquednd.updater.AppUpdateInstallHost
import com.mosquednd.updater.AppUpdateManager
import java.io.File

abstract class AppUpdateInstallActivity : AppCompatActivity(), AppUpdateInstallHost {

    companion object {
        private const val TAG = "AppUpdateInstall"
        private const val STATE_PENDING_INSTALL_FILE = "pending_update_install_file"
    }

    private var pendingInstallFile: File? = null

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apkFile = pendingInstallFile
        pendingInstallFile = null
        if (apkFile == null) return@registerForActivityResult

        if (!apkFile.isFile) {
            showUpdateInstallError(R.string.update_install_file_missing)
        } else if (canInstallPackages()) {
            launchApkInstaller(apkFile)
        } else {
            showUpdateInstallError(R.string.update_install_permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingInstallFile = savedInstanceState
            ?.getString(STATE_PENDING_INSTALL_FILE)
            ?.let(::File)
    }

    override fun installUpdate(apkFile: File) {
        if (!apkFile.isFile) {
            showUpdateInstallError(R.string.update_install_file_missing)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPackages()) {
            pendingInstallFile = apkFile
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            try {
                installPermissionLauncher.launch(settingsIntent)
            } catch (e: ActivityNotFoundException) {
                pendingInstallFile = null
                showUpdateInstallError(R.string.update_install_permission_denied)
                Log.e(TAG, "Unknown-app installation settings are unavailable", e)
            }
            return
        }

        launchApkInstaller(apkFile)
    }

    protected abstract fun showUpdateInstallError(messageRes: Int)

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    private fun launchApkInstaller(apkFile: File) {
        try {
            startActivity(AppUpdateManager(applicationContext).getInstallIntent(apkFile))
        } catch (e: ActivityNotFoundException) {
            showUpdateInstallError(R.string.update_installer_unavailable)
            Log.e(TAG, "No package installer available", e)
        } catch (e: IllegalArgumentException) {
            showUpdateInstallError(R.string.update_install_file_missing)
            Log.e(TAG, "Unable to create update installer intent", e)
        } catch (e: SecurityException) {
            showUpdateInstallError(R.string.update_install_permission_denied)
            Log.e(TAG, "Unable to launch update installer", e)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingInstallFile?.let { outState.putString(STATE_PENDING_INSTALL_FILE, it.absolutePath) }
        super.onSaveInstanceState(outState)
    }
}
