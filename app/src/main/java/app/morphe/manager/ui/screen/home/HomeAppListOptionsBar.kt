/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppCategoryViewMode
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.ui.screen.shared.*

/**
 * The button carries both modes, so the sort mode alone only describes it while nothing is filtered.
 */
@Composable
internal fun homeAppListOptionsStateDescription(
    sortMode: HomeAppSortMode,
    filterMode: HomeAppFilterMode
): String = if (filterMode.isActive) {
    stringResource(
        R.string.home_app_list_options_state_description,
        stringResource(sortMode.labelRes),
        stringResource(filterMode.labelRes)
    )
} else {
    stringResource(sortMode.labelRes)
}

/** One way of arranging the home list, its options and the one picked, as a row of the panel. */
private class ListOptionSection<T>(
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
    val current: T,
    val default: T,
    val options: List<SortModeOption<T>>,
    val onSelect: (T) -> Unit
) {
    val currentOption: SortModeOption<T> get() = options.first { it.value == current }
    val isChanged: Boolean get() = current != default
    fun reset() = onSelect(default)
}

/**
 * How the home list is sorted, filtered and grouped: one row for each with the option picked, which
 * opens its options in a menu. Docked under the list rather than over it, so the list rearranges
 * itself above the panel with each change.
 */
@Composable
internal fun HomeAppListOptionsBar(
    visible: Boolean,
    sortMode: HomeAppSortMode,
    filterMode: HomeAppFilterMode,
    groupingMode: HomeAppCategoryViewMode,
    onSortModeChange: (HomeAppSortMode) -> Unit,
    onFilterModeChange: (HomeAppFilterMode) -> Unit,
    onGroupingModeChange: (HomeAppCategoryViewMode) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = listOf(
        ListOptionSection(
            R.string.sort, Icons.AutoMirrored.Outlined.Sort, sortMode, HomeAppSortMode.RECOMMENDED,
            sortModeOptions<HomeAppSortMode>(), onSortModeChange
        ),
        ListOptionSection(
            R.string.filter, Icons.Outlined.FilterList, filterMode, HomeAppFilterMode.ALL,
            sortModeOptions<HomeAppFilterMode>(), onFilterModeChange
        ),
        ListOptionSection(
            R.string.group, Icons.Outlined.Category, groupingMode, HomeAppCategoryViewMode.ALL_APPS,
            sortModeOptions<HomeAppCategoryViewMode>(), onGroupingModeChange
        )
    )
    // Index of the section whose menu is open, if any
    var openSection by remember { mutableStateOf<Int?>(null) }

    DockedPanel(visible = visible, onBack = onClose, modifier = modifier) {
        Column(modifier = Modifier.padding(bottom = Defaults.SpaceSmall)) {
            PanelHeader(title = { PanelTitle(text = stringResource(R.string.home_app_list_options_title)) }) {
                TitleAction(
                    icon = Icons.Outlined.Restore,
                    contentDescription = stringResource(R.string.reset),
                    onClick = { sections.forEach { it.reset() } },
                    style = TitleActionStyle.Accent,
                    enabled = sections.any { it.isChanged }
                )
                TitleAction(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.close),
                    onClick = onClose,
                    style = TitleActionStyle.Neutral
                )
            }
            sections.forEachIndexed { index, section ->
                SectionRow(
                    section = section,
                    menuOpen = openSection == index,
                    onOpenMenu = { openSection = index },
                    onCloseMenu = { openSection = null }
                )
            }
        }
    }
}

/**
 * [section] as a row with the option picked at its end, marked when it is not the default, which
 * opens the section's options in a menu beside it.
 */
@Composable
private fun <T> SectionRow(
    section: ListOptionSection<T>,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onCloseMenu: () -> Unit
) {
    PanelRow(
        title = stringResource(section.titleRes),
        onClick = onOpenMenu,
        icon = section.icon
    ) {
        Text(
            text = section.currentOption.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (section.isChanged) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // All the title leaves of the row, so the values of every row end in line
            modifier = Modifier.weight(1f)
        )
        // The menu hangs off the chevron, so it opens at the row's end, under the value it changes
        Box {
            ExpandChevron(expanded = menuOpen, modifier = Modifier.size(20.dp))
            SortModeMenu(
                expanded = menuOpen,
                onDismissRequest = onCloseMenu,
                current = section.current,
                options = section.options,
                onSelect = section.onSelect
            )
        }
    }
}
