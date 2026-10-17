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
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
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
    val search = rememberSearchFieldState()
    val settingsSearch = remember(search) { SettingsSearch { search.query } }
    val searchLabel = stringResource(R.string.settings_search)
    // Registered inside the page so it outranks the main screen's own back handling
    SearchFieldBackHandler(search)

    PinnedHeaderPage(
        header = {
            PageHeader(title = stringResource(R.string.settings)) {
                SearchToggleAction(search, searchLabel)
            }
            HeaderSearchField(search, searchLabel)
        },
        modifier = modifier
    ) { headerHeight ->
        CompositionLocalProvider(
            LocalSettingsTour provides onboardingState,
            LocalSettingsSearch provides settingsSearch
        ) {
            // A row the tour turns to comes out from under the header and the navigation
            ProvideBringIntoViewInsets(top = headerHeight, bottom = bottomBarHeight) {
                SettingsBlocks(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollFade(scrollState)
                        .verticalScroll(scrollState)
                        .padding(
                            start = horizontal,
                            top = headerHeight + Defaults.SpaceMedium,
                            end = horizontal,
                            bottom = bottomBarHeight + Defaults.SpaceLarge
                        )
                ) {
                    PatchingSection(settingsViewModel, onOpenSubpage)
                    PatcherTuningSection(settingsViewModel)
                    OriginalApksSection(settingsViewModel)
                    ManagerSection(settingsViewModel, homeViewModel, onOpenSubpage)
                    BackgroundSection(settingsViewModel)
                    FilesSection()
                    AboutSection(onStartTour)
                    if (settingsSearch.isActive && settingsSearch.shown == 0) {
                        EmptyState(
                            message = stringResource(R.string.search_no_results),
                            icon = Icons.Outlined.SearchOff
                        )
                    }
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
 * Stacks the blocks of the page [Defaults.SpaceLarge] apart, closing up over any a search has left
 * empty, which a column spaced by its arrangement would still leave a gap for.
 */
@Composable
private fun SettingsBlocks(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = Defaults.SpaceLarge.roundToPx()
        val placeables = measurables
            .map { it.measure(constraints.copy(minHeight = 0)) }
            .filter { it.height > 0 }
        val height = placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
        // Held to the least height it is given, which a short page would otherwise be centered in
        layout(constraints.maxWidth, height.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            placeables.forEach { placeable ->
                placeable.place(0, y)
                y += placeable.height + gap
            }
        }
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

    // Shown once after enabling, and not as a match for a search
    if (useExpertMode && settingsViewModel.showExpertModeNotice && LocalSettingsSearch.current?.isActive != true) {
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
        modifier = modifier,
        keywords = subpage.keywordRes.map { stringResource(it) }
    )
}
