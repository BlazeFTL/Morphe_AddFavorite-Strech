/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.home.OnboardingState
import app.morphe.manager.ui.screen.settings.advanced.PatcherTuningSection
import app.morphe.manager.ui.screen.settings.advanced.UpdatesItems
import app.morphe.manager.ui.screen.settings.system.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

/** Rows of the settings page the onboarding tour points at. */
enum class SettingsTourTarget { EXPERT_MODE, INSTALLER, FILE_PICKER, APPEARANCE }

/** The tour while it runs, which the rows it points at report to. */
private val LocalSettingsTour = staticCompositionLocalOf<OnboardingState?> { null }

/**
 * Reports where the row sits to the running tour, and scrolls it into view once the tour turns to
 * [target]. Does nothing while no tour runs.
 */
@Composable
internal fun Modifier.settingsTourTarget(target: SettingsTourTarget): Modifier {
    val tour = LocalSettingsTour.current ?: return this
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(tour.settingsTarget) {
        if (tour.settingsTarget == target) requester.bringIntoView()
    }
    return bringIntoViewRequester(requester)
        .onGloballyPositioned { tour.settingsTargetBounds[target] = it.boundsInWindow() }
}

/**
 * The settings page of the main screen: every setting in its group, with the [SettingsSubpage]s
 * too long for a group each opening a page of its own.
 *
 * @param onStartTour Restarts the onboarding tour, when no tour is running.
 * @param bottomBarHeight Height of the navigation floating over the page, which it ends clear of.
 * @param onboardingState Set while the tour runs, which points at some of the rows.
 */
@Composable
fun SettingsPage(
    homeViewModel: HomeViewModel,
    onOpenSubpage: (SettingsSubpage) -> Unit,
    onStartTour: (() -> Unit)?,
    bottomBarHeight: Dp,
    modifier: Modifier = Modifier,
    onboardingState: OnboardingState? = null,
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val scrollState = rememberScrollState()
    val horizontal = rememberWindowSize().contentPadding

    PinnedHeaderPage(
        header = { PageHeader(title = stringResource(R.string.settings)) },
        modifier = modifier
    ) { headerHeight ->
        CompositionLocalProvider(LocalSettingsTour provides onboardingState) {
            // A row the tour turns to comes out from under the header and the navigation
            ProvideBringIntoViewInsets(top = headerHeight, bottom = bottomBarHeight) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollFade(scrollState)
                        .verticalScroll(scrollState)
                        .padding(
                            start = horizontal,
                            top = headerHeight + Defaults.SpaceMedium,
                            end = horizontal,
                            bottom = bottomBarHeight + Defaults.SpaceLarge
                        ),
                    verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
                ) {
                    PatchingSection(settingsViewModel, onOpenSubpage)
                    PatcherTuningSection(settingsViewModel)
                    OriginalApksSection(settingsViewModel)
                    ManagerSection(settingsViewModel, homeViewModel, onOpenSubpage)
                    BackgroundSection(settingsViewModel)
                    FilesSection()
                    AboutSection(onStartTour)
                }
            }
        }

        ListScrollbar(
            scrollState = scrollState,
            modifier = Modifier.padding(
                top = headerHeight + Defaults.SpaceMedium,
                bottom = bottomBarHeight + Defaults.SpaceMedium
            )
        )
        ScrollToTopButton(scrollState = scrollState, extraBottomPadding = bottomBarHeight)
    }
}

/**
 * What apps are patched with and installed through: expert mode, then the patch selections it
 * saves or, without it, the options simple mode patches apps with, and the installer.
 */
@Composable
private fun PatchingSection(
    settingsViewModel: SettingsViewModel,
    onOpenSubpage: (SettingsSubpage) -> Unit,
    importExportViewModel: ImportExportViewModel = koinViewModel()
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

    SectionTitle(
        text = stringResource(R.string.settings_section_patching),
        icon = Icons.Outlined.AutoFixHigh
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
            subtitle = stringResource(R.string.settings_advanced_expert_mode_description),
            modifier = Modifier.settingsTourTarget(SettingsTourTarget.EXPERT_MODE)
        )
        if (useExpertMode) {
            PatchSelectionsItem(
                settingsViewModel = settingsViewModel,
                importExportViewModel = importExportViewModel
            )
        } else {
            SubpageItem(SettingsSubpage.PATCH_OPTIONS, onOpenSubpage)
        }
        InstallerItem(
            settingsViewModel = settingsViewModel,
            modifier = Modifier.settingsTourTarget(SettingsTourTarget.INSTALLER)
        )
    }

    // Shown once after enabling
    if (useExpertMode && settingsViewModel.showExpertModeNotice) {
        Notice(
            icon = Icons.Outlined.Info,
            text = stringResource(R.string.settings_advanced_patch_options_expert_mode_notice),
            tone = SemanticTone.Warning
        )
    }
}

/** The manager itself: how it looks, and how it updates. */
@Composable
private fun ManagerSection(
    settingsViewModel: SettingsViewModel,
    homeViewModel: HomeViewModel,
    onOpenSubpage: (SettingsSubpage) -> Unit
) {
    SectionTitle(
        text = stringResource(R.string.settings_group_manager),
        icon = Icons.Outlined.Settings
    )

    SettingsGroup {
        SubpageItem(
            subpage = SettingsSubpage.APPEARANCE,
            onOpen = onOpenSubpage,
            modifier = Modifier.settingsTourTarget(SettingsTourTarget.APPEARANCE)
        )
        UpdatesItems(
            settingsViewModel = settingsViewModel,
            onManagerPrereleasesToggle = { homeViewModel.triggerUpdateCheck() }
        )
    }
}

/** A row opening [subpage]. */
@Composable
private fun SubpageItem(
    subpage: SettingsSubpage,
    onOpen: (SettingsSubpage) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsItem(
        onClick = { onOpen(subpage) },
        title = stringResource(subpage.titleRes),
        subtitle = stringResource(subpage.summaryRes),
        icon = subpage.icon,
        modifier = modifier
    )
}
