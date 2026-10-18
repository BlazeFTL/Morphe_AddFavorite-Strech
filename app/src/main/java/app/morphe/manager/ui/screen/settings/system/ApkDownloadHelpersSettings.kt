/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.settings.SettingsSubpage
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ApkDownloadHelperApp
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.PM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The APK download helpers installed on the device, as [SettingsViewModel] last found them, looked
 * up again each time the caller comes into composition.
 */
@Composable
fun rememberApkDownloadHelpers(settingsViewModel: SettingsViewModel): List<ApkDownloadHelperApp> {
    val helpers by settingsViewModel.apkDownloadHelpers.collectAsStateWithLifecycle()
    LaunchedEffect(settingsViewModel) { settingsViewModel.refreshApkDownloadHelpers() }
    return helpers
}

/**
 * The installed APK download helpers with where they came from, letting the user decide which of
 * them may be offered when an original APK is needed, on a page of their own, see
 * [SettingsSubpage.APK_DOWNLOAD_HELPERS]. A choice made here is saved at once.
 */
@Composable
fun ApkDownloadHelpersSettings(
    settingsViewModel: SettingsViewModel = koinViewModel(),
    pm: PM = koinInject()
) {
    val helpers = rememberApkDownloadHelpers(settingsViewModel)
    val trustedPackages by settingsViewModel.prefs.trustedApkDownloadHelpers.getAsState()
    val unknownInstaller = stringResource(R.string.settings_system_apk_download_helper_installed_by_unknown)

    // Version and install source are what tell two helpers apart, so they are worth a PackageManager round trip
    var details by remember { mutableStateOf(emptyMap<String, HelperDetails>()) }
    LaunchedEffect(helpers) {
        details = withContext(Dispatchers.IO) {
            helpers.associate { helper ->
                val installer = pm.getInstallerPackageName(helper.packageName)
                helper.packageName to HelperDetails(
                    versionName = pm.getPackageInfo(helper.packageName)?.versionName,
                    installerLabel = installer?.let { pm.getApplicationInfo(it) }
                        ?.loadLabel(pm.application.packageManager)?.toString()
                        ?: installer
                )
            }
        }
    }

    SettingsColumn {
        Notice(
            text = stringResource(R.string.settings_system_apk_download_helper_dialog_description),
            tone = SemanticTone.Neutral,
            icon = Icons.Outlined.Info
        )

        SettingsGroup {
            helpers.forEach { helper ->
                val trusted = helper.packageName in trustedPackages
                val info = details[helper.packageName]
                val installedBy = info?.installerLabel?.let {
                    stringResource(R.string.settings_system_apk_download_helper_installed_by, it)
                } ?: unknownInstaller

                SettingsSwitchItem(
                    checked = trusted,
                    onToggle = { settingsViewModel.setApkDownloadHelperTrusted(helper.packageName, !trusted) },
                    leadingContent = {
                        AppIcon(
                            packageName = helper.packageName,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                    },
                    title = helper.label,
                    // One fact per line, so a long package name never drags the version into a wrap
                    subtitle = listOfNotNull(
                        info?.versionName?.let { "v$it" },
                        helper.packageName,
                        installedBy.takeIf { info != null }
                    ).joinToString("\n")
                )
            }
        }

        Notice(
            text = stringResource(R.string.settings_system_apk_download_helper_warning),
            tone = SemanticTone.Warning,
            icon = Icons.Outlined.Warning
        )
    }
}

private data class HelperDetails(
    val versionName: String?,
    val installerLabel: String?
)
