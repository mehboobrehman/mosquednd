package com.mosquednd.whatsnew

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mosquednd.AppVersionInfo
import com.mosquednd.R
import com.mosquednd.updater.AppUpdateInstallHost
import com.mosquednd.updater.AppUpdateViewModel
import com.mosquednd.updater.UpdateUiState
import java.io.File

class WhatsNewBottomSheetFragment : BottomSheetDialogFragment() {
    private val updateViewModel: AppUpdateViewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(requireActivity())[AppUpdateViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
                MaterialTheme {
                    WhatsNewContent(
                        updateState = updateState,
                        onCheckForUpdates = { updateViewModel.checkForUpdates() },
                        onDownloadUpdate = { updateViewModel.startDownload() },
                        onCancelDownload = { updateViewModel.cancelDownload() },
                        onInstallUpdate = { apkFile ->
                            (requireActivity() as AppUpdateInstallHost).installUpdate(apkFile)
                        },
                        onDismiss = {
                            WhatsNewManager.markVersionSeen(requireContext())
                            dismiss()
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val TAG = "WhatsNewBottomSheetFragment"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                WhatsNewBottomSheetFragment().show(fragmentManager, TAG)
            }
        }
    }
}

@Composable
fun WhatsNewContent(
    updateState: UpdateUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallUpdate: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val releaseNotes = remember { ReleaseNoteRepository.getReleaseNotes() }
    val currentVersion = remember { AppVersionInfo.getCurrent().versionName }
    var selectedVersion by remember {
        mutableStateOf(
            ReleaseNoteRepository.getReleaseNoteForVersion(currentVersion) ?: releaseNotes.first()
        )
    }
    var menuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val updateError = (updateState as? UpdateUiState.Error)?.let {
        stringResource(R.string.update_error, it.message)
    }

    LaunchedEffect(updateError) {
        if (updateError != null) {
            snackbarHostState.showSnackbar(updateError)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Header Row with Title and Version Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "What's New in MosqueDND",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedVersion.versionName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Version")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            releaseNotes.forEach { note ->
                                DropdownMenuItem(
                                    text = { Text(note.versionName) },
                                    onClick = {
                                        selectedVersion = note
                                        menuExpanded = false
                                    },
                                    trailingIcon = {
                                        if (selectedVersion.versionName == note.versionName) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = selectedVersion.releaseDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = selectedVersion.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Categories and Items
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                ) {
                    val groupedItems = selectedVersion.items.groupBy { it.category }

                    ReleaseNoteCategory.values().forEach { category ->
                        val items = groupedItems[category]
                        if (items != null) {
                            item {
                                CategoryHeader(category)
                            }
                            items(items) { item ->
                                WhatsNewItemRow(item)
                            }
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                UpdateActions(
                    updateState = updateState,
                    onCheckForUpdates = onCheckForUpdates,
                    onDownloadUpdate = onDownloadUpdate,
                    onCancelDownload = onCancelDownload,
                    onInstallUpdate = onInstallUpdate
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.whats_new_dismiss),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
        )
    }
}

@Composable
private fun UpdateActions(
    updateState: UpdateUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallUpdate: (File) -> Unit
) {
    when (updateState) {
        UpdateUiState.Idle -> {
            OutlinedButton(onClick = onCheckForUpdates, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_check_for_updates))
            }
        }
        UpdateUiState.Checking -> {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.update_checking))
            }
        }
        UpdateUiState.UpToDate -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.update_up_to_date),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = onCheckForUpdates, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_check_again))
                }
            }
        }
        is UpdateUiState.UpdateAvailable -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.update_available, updateState.releaseInfo.tagName),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                updateState.releaseInfo.releaseNotes
                    ?.takeIf { it.isNotBlank() }
                    ?.let { notes ->
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4
                        )
                    }
                Button(onClick = onDownloadUpdate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_download))
                }
            }
        }
        is UpdateUiState.Downloading -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.update_downloading),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (updateState.progress >= 0f) {
                    LinearProgressIndicator(
                        progress = updateState.progress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(
                            R.string.update_download_progress,
                            (updateState.progress.coerceIn(0f, 1f) * 100).toInt()
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = onCancelDownload, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.update_cancel))
                }
            }
        }
        is UpdateUiState.ReadyToInstall -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.update_download_verified),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { onInstallUpdate(updateState.apkFile) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.update_install))
                }
            }
        }
        is UpdateUiState.Error -> {
            OutlinedButton(onClick = onCheckForUpdates, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
fun CategoryHeader(category: ReleaseNoteCategory) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category.icon,
            fontSize = 18.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = category.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun WhatsNewItemRow(item: WhatsNewItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, bottom = 8.dp)
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (item.description != null) {
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
