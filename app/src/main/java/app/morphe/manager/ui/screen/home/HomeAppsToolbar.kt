/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.ui.screen.shared.ActionGlassButton
import app.morphe.manager.ui.screen.shared.BottomActionTone
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.ListSearchField
import app.morphe.manager.ui.screen.shared.listOptionsStateDescription

/**
 * What the apps page offers over its list: the search, the list's order and filter, and patching
 * an app the list does not hold, from its APK.
 *
 * @param searchState Null where the list is too short to need searching.
 * @param sortEnabled Whether the list's options can be opened, which a selection under way holds off.
 * @param sortOpen Whether the list's options are docked under the list, which the button then closes.
 * @param onPatchFileClick Null while there is no other app to patch, such as before the sources load.
 */
@Composable
internal fun HomeAppsToolbar(
    searchState: HomeSearchState?,
    showSort: Boolean,
    sortEnabled: Boolean,
    sortOpen: Boolean,
    sortMode: HomeAppSortMode,
    filterMode: HomeAppFilterMode,
    onSortClick: () -> Unit,
    onPatchFileClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    // The glass buttons fill whatever room they are given, so each is held to a circle as tall as
    // the field, which takes the rest of the row
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (searchState != null) {
            ListSearchField(
                value = searchState.query,
                onValueChange = searchState.onQueryChange,
                label = stringResource(R.string.home_search_apps),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { searchState.onFocusChange(it.isFocused) },
                glass = true
            )
        } else {
            // Keeps the actions at the end of the row
            Spacer(modifier = Modifier.weight(1f))
        }

        if (showSort) {
            ActionGlassButton(
                onClick = onSortClick,
                icon = Icons.AutoMirrored.Outlined.Sort,
                modifier = Modifier.width(ToolbarHeight),
                height = ToolbarHeight,
                contentDescription = stringResource(R.string.sort),
                stateDescription = listOptionsStateDescription(sortMode.labelRes, filterMode.labelRes.takeIf { filterMode.isActive }),
                tone = when {
                    sortOpen -> BottomActionTone.Accent
                    filterMode.isActive -> BottomActionTone.Highlight
                    else -> BottomActionTone.Neutral
                },
                enabled = sortEnabled
            )
        }

        if (onPatchFileClick != null) {
            ActionGlassButton(
                onClick = onPatchFileClick,
                icon = Icons.Outlined.FileOpen,
                modifier = Modifier.width(ToolbarHeight),
                height = ToolbarHeight,
                contentDescription = stringResource(R.string.home_other_apps_action),
                tone = BottomActionTone.Accent
            )
        }
    }
}

/** Height the row's buttons share with its search field, the least a text field is drawn at. */
private val ToolbarHeight = TextFieldDefaults.MinHeight
