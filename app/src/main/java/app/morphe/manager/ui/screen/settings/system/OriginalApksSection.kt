/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.ApkDownloadHelperContract
import app.morphe.manager.util.isAndroidTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The ways of getting hold of the APK an app is patched from: the file picker and the download
 * helpers installed on the device. Shows nothing where neither is offered.
 */
@Composable
fun OriginalApksSection(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val isTV = remember { context.isAndroidTv() }
    val useCustomFilePicker by settingsViewModel.prefs.useCustomFilePicker.getAsState()
    val trustedApkDownloadHelpers by settingsViewModel.prefs.trustedApkDownloadHelpers.getAsState()
    var showApkDownloadHelpersDialog by remember { mutableStateOf(false) }

    // Keeps the entry hidden until a helper is installed, so it never advertises a third-party app
    var apkDownloadHelpers by remember { mutableStateOf(emptyList<ApkDownloadHelperApp>()) }
    LaunchedEffect(Unit) {
        apkDownloadHelpers = withContext(Dispatchers.IO) {
            ApkDownloadHelperContract.findHelpers(context)
                .distinctBy { it.componentName.packageName }
                .map { ApkDownloadHelperApp(packageName = it.componentName.packageName, label = it.label) }
        }
    }

    if (showApkDownloadHelpersDialog) {
        ApkDownloadHelpersDialog(
            settingsViewModel = settingsViewModel,
            helpers = apkDownloadHelpers,
            onDismiss = { showApkDownloadHelpersDialog = false }
        )
    }

    // TV always uses the custom picker regardless of this toggle, so hide it to avoid confusion
    val showFilePicker = !isTV
    val showApkDownloadHelpers = apkDownloadHelpers.isNotEmpty()
    if (!showFilePicker && !showApkDownloadHelpers) return

    Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)) {
        SectionTitle(
            text = stringResource(R.string.settings_section_original_apks),
            icon = Icons.Outlined.Android
        )

        SettingsGroup {
            if (showFilePicker) {
                SettingsSwitchItem(
                    checked = useCustomFilePicker,
                    onToggle = { settingsViewModel.setUseCustomFilePicker(!useCustomFilePicker) },
                    icon = Icons.Outlined.FolderOpen,
                    title = stringResource(R.string.settings_system_custom_file_picker),
                    subtitle = stringResource(R.string.settings_system_custom_file_picker_description)
                )
            }

            if (showApkDownloadHelpers) {
                SettingsItem(
                    onClick = { showApkDownloadHelpersDialog = true },
                    title = stringResource(R.string.settings_system_apk_download_helper),
                    subtitle = stringResource(
                        R.string.settings_system_apk_download_helper_trusted_count,
                        apkDownloadHelpers.count { it.packageName in trustedApkDownloadHelpers },
                        apkDownloadHelpers.size
                    ),
                    leadingContent = { ThemedIcon(icon = Icons.Outlined.Download) }
                )
            }
        }
    }
}
