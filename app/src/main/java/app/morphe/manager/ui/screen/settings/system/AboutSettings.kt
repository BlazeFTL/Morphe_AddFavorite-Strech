/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.ui.screen.home.ManagerChangelogDialog
import app.morphe.manager.ui.screen.settings.SettingsSubpage
import app.morphe.manager.ui.screen.settings.SubpageItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.util.TEXT_MIMETYPE
import app.morphe.manager.util.isAndroidTv
import app.morphe.manager.util.isolateLtr
import org.koin.androidx.compose.koinViewModel

/**
 * The manager itself, closing the settings page as a section of its own: its version, which opens
 * [AboutDialog], over the row opening its changelog, credits and help, see [AboutSettings].
 */
@Composable
fun AboutGroup(onOpenSubpage: (SettingsSubpage) -> Unit) {
    val logo = rememberMorpheLogoBitmap()
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    DialogHost(visible = showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    SettingsSection(
        title = stringResource(R.string.settings_system_about),
        icon = Icons.Outlined.Info
    ) {
        SettingsGroup {
            SettingsItem(
                onClick = { showAboutDialog = true },
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.version) + " " + BuildConfig.VERSION_NAME.isolateLtr(),
                leadingContent = {
                    logo?.let {
                        Icon(
                            bitmap = it,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Defaults.IconSize)
                        )
                    }
                }
            )
            SubpageItem(SettingsSubpage.ABOUT, onOpenSubpage)
        }
    }
}

/**
 * What there is to know about the manager past its version, and help with it, on a page of its
 * own, see [SettingsSubpage.ABOUT]: its changelog, credits and licenses, the logs a bug report
 * asks for and the tour.
 *
 * @param onOpenLicenses Opens the open-source licenses the manager is built on.
 * @param onStartTour Restarts the onboarding tour, when no tour is running.
 */
@Composable
fun AboutSettings(
    onOpenLicenses: () -> Unit,
    onStartTour: (() -> Unit)?,
    importExportViewModel: ImportExportViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val isTV = remember { context.isAndroidTv() }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showCreditsDialog by rememberSaveable { mutableStateOf(false) }

    val exportDebugLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(TEXT_MIMETYPE)
    ) { uri -> uri?.let { importExportViewModel.exportDebugLogs(it) } }

    DialogHost(visible = showChangelogDialog) {
        ManagerChangelogDialog(onDismiss = { showChangelogDialog = false })
    }

    DialogHost(visible = showCreditsDialog) {
        CreditsDialog(onDismiss = { showCreditsDialog = false })
    }

    SettingsColumn {
        SettingsSection(
            title = stringResource(R.string.settings_about_section_project),
            icon = Icons.Outlined.Info
        ) {
            SettingsGroup {
                SettingsItem(
                    icon = Icons.AutoMirrored.Outlined.Article,
                    title = stringResource(R.string.changelog),
                    subtitle = stringResource(R.string.changelog_description),
                    onClick = { showChangelogDialog = true }
                )
                SettingsItem(
                    icon = Icons.Outlined.People,
                    title = stringResource(R.string.credits),
                    subtitle = stringResource(R.string.credits_description),
                    onClick = { showCreditsDialog = true }
                )
                SettingsItem(
                    icon = Icons.Outlined.Gavel,
                    title = stringResource(R.string.opensource_licenses),
                    subtitle = stringResource(R.string.opensource_licenses_description),
                    onClick = onOpenLicenses
                )
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_about_section_help),
            icon = Icons.AutoMirrored.Outlined.HelpOutline
        ) {
            SettingsGroup {
                // Logs are what a bug report asks for
                SettingsItem(
                    icon = Icons.Outlined.BugReport,
                    title = stringResource(R.string.settings_system_export_debug_logs),
                    subtitle = stringResource(R.string.settings_system_export_debug_logs_description),
                    onClick = {
                        if (isTV) importExportViewModel.exportDebugLogsToDownloads()
                        else exportDebugLogsLauncher.launch(importExportViewModel.debugLogFileName)
                    }
                )
                if (onStartTour != null) {
                    SettingsItem(
                        icon = Icons.Outlined.Lightbulb,
                        title = stringResource(R.string.onboarding_restart_title),
                        subtitle = stringResource(R.string.onboarding_restart_desc),
                        onClick = onStartTour
                    )
                }
            }
        }
    }
}
