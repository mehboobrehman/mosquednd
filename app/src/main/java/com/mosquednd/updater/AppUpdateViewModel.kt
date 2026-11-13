package com.mosquednd.updater

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class UpdateAvailable(val releaseInfo: ReleaseInfo) : UpdateUiState()
    object UpToDate : UpdateUiState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateUiState()
    data class ReadyToInstall(val apkFile: File, val releaseInfo: ReleaseInfo) : UpdateUiState()
    data class Error(val message: String, val canRetry: Boolean = true) : UpdateUiState()
}

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val updateManager = AppUpdateManager(application)

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var currentReleaseInfo: ReleaseInfo? = null
    private var downloadJob: Job? = null

    fun checkForUpdates(silent: Boolean = false) {
        if (_uiState.value is UpdateUiState.Checking || _uiState.value is UpdateUiState.Downloading) return

        viewModelScope.launch {
            if (!silent) {
                _uiState.value = UpdateUiState.Checking
            }
            try {
                val pair = updateManager.checkForUpdate()
                if (pair != null) {
                    val (releaseInfo, isNewer) = pair
                    currentReleaseInfo = releaseInfo
                    if (isNewer) {
                        _uiState.value = UpdateUiState.UpdateAvailable(releaseInfo)
                    } else {
                        if (!silent) {
                            _uiState.value = UpdateUiState.UpToDate
                        } else {
                            _uiState.value = UpdateUiState.Idle
                        }
                    }
                } else {
                    if (!silent) {
                        _uiState.value = UpdateUiState.Error("No release info found")
                    } else {
                        _uiState.value = UpdateUiState.Idle
                    }
                }
            } catch (e: Exception) {
                if (!silent) {
                    _uiState.value = UpdateUiState.Error("Failed to check for updates: ${e.message}")
                } else {
                    _uiState.value = UpdateUiState.Idle
                }
            }
        }
    }

    fun startDownload(releaseInfo: ReleaseInfo? = currentReleaseInfo) {
        val info = releaseInfo ?: return
        downloadJob?.cancel()

        downloadJob = viewModelScope.launch {
            _uiState.value = UpdateUiState.Downloading(0f, 0L, info.apkSize)
            try {
                val apkFile = updateManager.downloadAndVerify(info) { progress, downloaded, total ->
                    _uiState.value = UpdateUiState.Downloading(progress, downloaded, total)
                }
                _uiState.value = UpdateUiState.ReadyToInstall(apkFile, info)
            } catch (e: Exception) {
                _uiState.value = UpdateUiState.Error("Update download/verification failed: ${e.message}")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        currentReleaseInfo?.let {
            _uiState.value = UpdateUiState.UpdateAvailable(it)
        } ?: run {
            _uiState.value = UpdateUiState.Idle
        }
    }

    fun getInstallIntent(apkFile: File) = updateManager.getInstallIntent(apkFile)

    fun dismiss() {
        _uiState.value = UpdateUiState.Idle
    }
}
