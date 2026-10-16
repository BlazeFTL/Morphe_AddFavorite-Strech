/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.manager.SettingsSection
import app.morphe.manager.ui.screen.home.ManagerChangelogDialog
import app.morphe.manager.ui.screen.settings.advanced.PatchOptionsSection
import app.morphe.manager.ui.screen.settings.advanced.PatcherTuningSection
import app.morphe.manager.ui.screen.settings.advanced.UpdatesSection
import app.morphe.manager.ui.screen.settings.system.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.PatchOptionsViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.*
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * One [category] of the settings as a page of its own, opened from the settings page.
 *
 * @param onStartTour Restarts the onboarding tour from the about page, when no tour is running.
 */
@Composable
fun SettingsCategoryScreen(
    category: SettingsCategory,
    homeViewModel: HomeViewModel,
    onBack: () -> Unit,
    onStartTour: (() -> Unit)? = null
) {
    SubpageScaffold(title = stringResource(category.titleRes), onBack = onBack) {
        when (category) {
            SettingsCategory.PATCHES -> PatchesSettings(homeViewModel)
            SettingsCategory.PATCHER -> PatcherSettings()
            SettingsCategory.INSTALLER -> InstallerSettings()
            SettingsCategory.APPEARANCE -> AppearanceSettings()
            SettingsCategory.UPDATES -> UpdatesSettings(homeViewModel)
            SettingsCategory.BACKGROUND -> BackgroundSettings()
            SettingsCategory.FILES -> FilesSettings()
            SettingsCategory.ABOUT -> AboutSettings(onStartTour)
        }
    }
}

/**
 * Expert mode, with the patch selections it saves, or, without it, the options simple mode patches
 * apps with.
 */
@Composable
private fun PatchesSettings(
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel = koinViewModel(),
    importExportViewModel: ImportExportViewModel = koinViewModel(),
    patchOptionsViewModel: PatchOptionsViewModel = koinViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )
) {
    val useExpertMode by settingsViewModel.prefs.useExpertMode.getAsState()
    var showExpertModeDialog by remember { mutableStateOf(false) }

    // Notify VM on expert mode changes so it can derive showExpertModeNotice
    LaunchedEffect(useExpertMode) {
        settingsViewModel.onExpertModeChanged(useExpertMode)
    }

    if (showExpertModeDialog) {
        ConfirmDialog(
            title = stringResource(R.string.settings_advanced_expert_mode_dialog_title),
            message = stringResource(R.string.settings_advanced_expert_mode_dialog_message),
            primaryText = stringResource(R.string.enable),
            onConfirm = {
                settingsViewModel.setExpertMode(true)
                showExpertModeDialog = false
            },
            onDismiss = { showExpertModeDialog = false }
        )
    }

    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
    ) {
        SectionTitle(
            text = stringResource(R.string.settings_advanced_expert),
            icon = Icons.Outlined.Engineering
        )

        SettingsGroup {
            SettingsSwitchItem(
                checked = useExpertMode,
                onToggle = {
                    if (!useExpertMode) showExpertModeDialog = true
                    else settingsViewModel.setExpertMode(false)
                },
                icon = Icons.Outlined.Psychology,
                title = stringResource(R.string.settings_advanced_expert_mode),
                subtitle = stringResource(R.string.settings_advanced_expert_mode_description)
            )
            if (useExpertMode) {
                PatchSelectionsItem(
                    settingsViewModel = settingsViewModel,
                    importExportViewModel = importExportViewModel
                )
            }
        }

        Crossfade(
            targetState = useExpertMode,
            label = "expert_mode_crossfade"
        ) { expertMode ->
            Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)) {
                if (expertMode) {
                    // Shown once after enabling
                    if (settingsViewModel.showExpertModeNotice) {
                        Notice(
                            icon = Icons.Outlined.Info,
                            text = stringResource(R.string.settings_advanced_patch_options_expert_mode_notice),
                            tone = SemanticTone.Warning
                        )
                    }
                } else {
                    // Expert mode picks options patch by patch, so these apply to simple mode only
                    SectionTitle(
                        text = stringResource(R.string.settings_advanced_patch_options),
                        icon = Icons.Outlined.Tune
                    )

                    PatchOptionsSection(
                        patchOptionsPrefs = patchOptionsViewModel.patchOptionsPrefs,
                        patchOptionsViewModel = patchOptionsViewModel,
                        homeViewModel = homeViewModel
                    )
                }
            }
        }
    }
}

/** How the patcher runs, then where the APKs it patches come from. */
@Composable
private fun PatcherSettings(settingsViewModel: SettingsViewModel = koinViewModel()) {
    Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)) {
        PatcherTuningSection(settingsViewModel = settingsViewModel)
        OriginalApksSection(settingsViewModel = settingsViewModel)
    }
}

/** Updates of the manager and of its sources. */
@Composable
private fun UpdatesSettings(
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    UpdatesSection(
        settingsViewModel = settingsViewModel,
        onManagerPrereleasesToggle = { homeViewModel.triggerUpdateCheck() }
    )
}

/** The installer patched apps go through. */
@Composable
private fun InstallerSettings(settingsViewModel: SettingsViewModel = koinViewModel()) {
    var showInstallerDialog by remember { mutableStateOf(false) }

    if (showInstallerDialog) {
        InstallerSelectionDialogContainer(
            settingsViewModel = settingsViewModel,
            onDismiss = { showInstallerDialog = false }
        )
    }

    SettingsGroup {
        InstallerSection(
            settingsViewModel = settingsViewModel,
            onShowInstallerDialog = { showInstallerDialog = true }
        )
    }
}

/**
 * What the manager keeps on the device, then the backups of it: the signing key, which a lost
 * install cannot recreate, and the manager's settings.
 */
@Composable
private fun FilesSettings(importExportViewModel: ImportExportViewModel = koinViewModel()) {
    val context = LocalContext.current
    val messages = koinInject<UserMessages>()
    val scope = rememberCoroutineScope()
    val isTV = remember { context.isAndroidTv() }
    val wrongCredentialsText = stringResource(R.string.settings_system_import_keystore_wrong_credentials)

    val importKeystoreLauncher = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf("*/*"),
        customPickerMimeTypes = arrayOf(
            "application/x-pkcs12",
            "application/x-java-keystore",
            "application/vnd.morphe.keystore",
        ),
        onResult = { uri -> uri?.let { importExportViewModel.startKeystoreImport(it) } }
    )

    // Replace and Merge only differ for sources and patch selections, so the mode is asked only
    // when one of them is taken
    var pendingSettingsImportUri by remember { mutableStateOf<Uri?>(null) }
    val importSettingsLauncher = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf(JSON_MIMETYPE, TEXT_MIMETYPE),
        customPickerMimeTypes = arrayOf(JSON_MIMETYPE),
        onResult = { uri ->
            uri ?: return@rememberAdaptiveFilePicker
            val sections = importExportViewModel.settingsSections
            if (SettingsSection.SOURCES in sections || SettingsSection.PATCH_SELECTIONS in sections) {
                pendingSettingsImportUri = uri
            } else {
                importExportViewModel.importManagerSettings(uri)
            }
        }
    )

    val exportKeystoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> uri?.let { importExportViewModel.exportKeystore(it) } }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JSON_MIMETYPE)
    ) { uri -> uri?.let { importExportViewModel.exportManagerSettings(it) } }

    if (importExportViewModel.showCredentialsDialog) {
        KeystoreCredentialsDialog(
            onDismiss = importExportViewModel::cancelKeystoreImport,
            initialFormat = importExportViewModel.detectedKeystoreFormat,
            onSubmit = { alias, pass, storePass, format ->
                scope.launch {
                    if (!importExportViewModel.tryKeystoreImport(alias, pass, storePass, format)) {
                        messages.show(wrongCredentialsText)
                    }
                }
            }
        )
    }

    pendingSettingsImportUri?.let { uri ->
        ImportModeDialog(
            titleRes = R.string.settings_system_import_manager_settings_mode_title,
            descriptionRes = R.string.settings_system_import_manager_settings_mode_description,
            onDismiss = { pendingSettingsImportUri = null },
            onSelect = { mode ->
                importExportViewModel.importManagerSettings(uri, mode)
                pendingSettingsImportUri = null
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)) {
        StorageSection()

        ImportExportSection(
            importExportViewModel = importExportViewModel,
            onImportKeystore = { importKeystoreLauncher() },
            onExportKeystore = {
                if (isTV) importExportViewModel.exportKeystoreToDownloads()
                else exportKeystoreLauncher.launch("Morphe.keystore")
            },
            onImportSettings = { importSettingsLauncher() },
            onExportSettings = {
                if (isTV) importExportViewModel.exportManagerSettingsToDownloads()
                else exportSettingsLauncher.launch("morphe_manager_settings.json")
            }
        )
    }
}

/** What keeps the manager working in the background, and what it tells about it. */
@Composable
private fun BackgroundSettings(settingsViewModel: SettingsViewModel = koinViewModel()) {
    var showNotificationsDialog by remember { mutableStateOf(false) }

    if (showNotificationsDialog) {
        NotificationsDialog(
            settingsViewModel = settingsViewModel,
            onDismiss = { showNotificationsDialog = false }
        )
    }

    BackgroundSection(onNotificationsClick = { showNotificationsDialog = true })
}

/** The manager itself: its version, changelog and logs, and the tour. */
@Composable
private fun AboutSettings(
    onStartTour: (() -> Unit)?,
    importExportViewModel: ImportExportViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val isTV = remember { context.isAndroidTv() }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }

    val exportDebugLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(TEXT_MIMETYPE)
    ) { uri -> uri?.let { importExportViewModel.exportDebugLogs(it) } }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showChangelogDialog) {
        ManagerChangelogDialog(onDismiss = { showChangelogDialog = false })
    }

    AboutSection(
        onAboutClick = { showAboutDialog = true },
        onChangelogClick = { showChangelogDialog = true },
        onExportDebugLogs = {
            if (isTV) importExportViewModel.exportDebugLogsToDownloads()
            else exportDebugLogsLauncher.launch(importExportViewModel.debugLogFileName)
        },
        onStartTour = onStartTour
    )
}
