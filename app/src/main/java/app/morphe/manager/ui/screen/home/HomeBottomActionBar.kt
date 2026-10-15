/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.ui.screen.shared.*

/**
 * Section 5: Bottom action bar.
 * Sources | Search (optional) | Sort (optional) | Settings on a floating pill, with the "Other
 * apps" action as a round button beside it while that action is offered.
 */
@Composable
fun HomeBottomActionBar(
    modifier: Modifier = Modifier,
    onBundlesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isExpertModeEnabled: Boolean = false,
    showSearchButton: Boolean = false,
    showSortButton: Boolean = false,
    showOtherAppsButton: Boolean = false,
    sortMode: HomeAppSortMode = HomeAppSortMode.MANUAL,
    filterMode: HomeAppFilterMode = HomeAppFilterMode.ALL,
    searchActive: Boolean = false,
    onSearchClick: () -> Unit = {},
    onSortClick: () -> Unit = {},
    onOtherAppsClick: () -> Unit = {},
    onSourcesPositioned: ((Rect) -> Unit)? = null,
    onSettingsPositioned: ((Rect) -> Unit)? = null
) {
    val sourcesLabel = stringResource(R.string.sources)
    // One word like its neighbors, since the pill drops every label once they cannot all fit.
    // The fuller wording stays for screen readers
    val searchLabel = stringResource(R.string.search)
    val searchAppsLabel = stringResource(R.string.home_search_apps)
    val searchExpandedLabel = stringResource(R.string.expanded)
    val searchCollapsedLabel = stringResource(R.string.collapsed)
    val sortLabel = stringResource(R.string.sort)
    val sortStateLabel = homeAppListOptionsStateDescription(sortMode, filterMode)
    val settingsLabel = stringResource(R.string.settings)
    val expertModeLabel = stringResource(R.string.settings_advanced_expert_mode)
    val otherAppsLabel = stringResource(R.string.home_other_apps_action)

    val items = listOfNotNull(
        FloatingPillItem(
            icon = Icons.Outlined.Source,
            label = sourcesLabel,
            onClick = onBundlesClick,
            modifier = onSourcesPositioned?.let { onPositioned ->
                Modifier.onGloballyPositioned { onPositioned(it.boundsInWindow()) }
            } ?: Modifier
        ),
        FloatingPillItem(
            icon = if (searchActive) Icons.Outlined.SearchOff else Icons.Outlined.Search,
            label = searchLabel,
            onClick = onSearchClick,
            contentDescription = searchAppsLabel,
            stateDescription = if (searchActive) searchExpandedLabel else searchCollapsedLabel
        ).takeIf { showSearchButton },
        FloatingPillItem(
            icon = Icons.AutoMirrored.Outlined.Sort,
            label = sortLabel,
            onClick = onSortClick,
            tone = if (filterMode.isActive) BottomActionTone.Highlight else BottomActionTone.Neutral,
            stateDescription = sortStateLabel
        ).takeIf { showSortButton },
        // Settings, with the expert mode indicator
        FloatingPillItem(
            icon = if (isExpertModeEnabled) Icons.Outlined.Engineering else Icons.Outlined.Settings,
            label = settingsLabel,
            onClick = onSettingsClick,
            contentDescription = if (isExpertModeEnabled) "$settingsLabel, $expertModeLabel" else null,
            modifier = onSettingsPositioned?.let { onPositioned ->
                Modifier.onGloballyPositioned { onPositioned(it.boundsInWindow()) }
            } ?: Modifier
        )
    )

    FloatingPillBar(items = items, modifier = modifier) {
        AnimatedRowPill(visible = showOtherAppsButton) {
            FloatingPillRoundAction(
                icon = Icons.Outlined.FileOpen,
                label = otherAppsLabel,
                onClick = onOtherAppsClick
            )
        }
    }
}
