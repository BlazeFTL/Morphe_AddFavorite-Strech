/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.ListSearchField
import app.morphe.manager.ui.screen.shared.TitleAction
import app.morphe.manager.ui.screen.shared.TitleActionStyle
import app.morphe.manager.ui.screen.shared.autoMirrored
import app.morphe.manager.ui.screen.shared.listOptionsStateDescription
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * What the apps page offers over its list: the search, the list's order and filter, and patching
 * an app the list does not hold, from its APK.
 *
 * @param sortEnabled Whether the list's options can be opened, which a selection under way holds off.
 * @param sortOpen Whether the list's options are docked under the list, which the button then closes.
 * @param optionsChanged Whether any of the list's options is off its default, which marks the button.
 */
@Composable
internal fun HomeAppsToolbar(
    searchState: HomeSearchState,
    sortEnabled: Boolean,
    sortOpen: Boolean,
    sortMode: HomeAppSortMode,
    filterMode: HomeAppFilterMode,
    optionsChanged: Boolean,
    onSortClick: () -> Unit,
    onPatchFileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The same controls as a header's, so the page reads as headed like the dialogs it opens
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ListSearchField(
            value = searchState.query,
            onValueChange = searchState.onQueryChange,
            label = stringResource(R.string.home_search_apps),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { searchState.onFocusChange(it.isFocused) }
        )

        val sortState = listOptionsStateDescription(sortMode.labelRes, filterMode.labelRes.takeIf { filterMode.isActive })
        TitleAction(
            icon = MaterialSymbols.Rounded.Sort.autoMirrored,
            contentDescription = stringResource(R.string.sort),
            onClick = onSortClick,
            // Engaged while the options are docked, and marked while any is off its default
            style = TitleActionStyle.Toggle,
            active = sortOpen,
            marked = optionsChanged,
            enabled = sortEnabled,
            modifier = Modifier.semantics { stateDescription = sortState }
        )

        TitleAction(
            icon = MaterialSymbols.Rounded.File_open,
            contentDescription = stringResource(R.string.home_other_apps_action),
            onClick = onPatchFileClick,
            style = TitleActionStyle.Accent
        )
    }
}
