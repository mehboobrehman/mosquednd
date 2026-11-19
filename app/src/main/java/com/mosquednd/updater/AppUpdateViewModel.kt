package com.mosquednd.updater

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var showCheckResult = false
    private var dismissCancelledDownload = false

    fun checkForUpdates(silent: Boolean = false) {
        if (checkJob?.isActive == true) {
            if (!silent) {
                showCheckResult = true
                _uiState.value = UpdateUiState.Checking
            }
            return
        }
        if (downloadJob?.isActive == true) return
        showCheckResult = !silent
        if (!silent) {
            _uiState.value = UpdateUiState.Checking
        }

        checkJob = viewModelScope.launch {
            try {
                val pair = updateManager.checkForUpdate()
                val (releaseInfo, isNewer) = pair
                currentReleaseInfo = releaseInfo
                if (isNewer) {
                    _uiState.value = UpdateUiState.UpdateAvailable(releaseInfo)
                } else {
                    if (showCheckResult) {
                        _uiState.value = UpdateUiState.UpToDate
                    } else {
                        _uiState.value = UpdateUiState.Idle
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (showCheckResult) {
                    _uiState.value = UpdateUiState.Error("Failed to check for updates: ${e.message}")
                } else {
                    _uiState.value = UpdateUiState.Idle
                }
            } finally {
                checkJob = null
                showCheckResult = false
            }
        }
    }

    fun startDownload(releaseInfo: ReleaseInfo? = currentReleaseInfo) {
        if (downloadJob?.isActive == true) return
        val info = releaseInfo ?: run {
            _uiState.value = UpdateUiState.Error("No release is available to download")
            return
        }

        dismissCancelledDownload = false
        downloadJob = viewModelScope.launch {
            _uiState.value = UpdateUiState.Downloading(0f, 0L, info.apkSize)
            try {
                val apkFile = updateManager.downloadAndVerify(info) { progress, downloaded, total ->
                    _uiState.value = UpdateUiState.Downloading(progress, downloaded, total)
                }
                _uiState.value = UpdateUiState.ReadyToInstall(apkFile, info)
            } catch (e: CancellationException) {
                if (dismissCancelledDownload) {
                    _uiState.value = UpdateUiState.Idle
                } else {
                    currentReleaseInfo?.let {
                        _uiState.value = UpdateUiState.UpdateAvailable(it)
                    } ?: run {
                        _uiState.value = UpdateUiState.Idle
                    }
                }
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.value = UpdateUiState.Error("Update download/verification failed: ${e.message}")
            } finally {
                downloadJob = null
                dismissCancelledDownload = false
            }
        }
    }

    fun cancelDownload(dismissAfterCancel: Boolean = false) {
        dismissCancelledDownload = dismissAfterCancel
        downloadJob?.cancel()
        updateManager.cancelDownload()
    }

    fun dismiss() {
        showCheckResult = false
        _uiState.value = UpdateUiState.Idle
    }
}
