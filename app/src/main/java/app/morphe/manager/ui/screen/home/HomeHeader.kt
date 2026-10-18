/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.HeaderBar
import app.morphe.manager.ui.screen.shared.MorpheWordmark
import app.morphe.manager.ui.screen.shared.SearchFieldState
import app.morphe.manager.ui.screen.shared.TitleAction
import app.morphe.manager.ui.screen.shared.TitleActionStyle
import app.morphe.manager.ui.screen.shared.autoMirrored
import app.morphe.manager.ui.screen.shared.listOptionsStateDescription
import app.morphe.manager.ui.screen.shared.rememberLatest
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * Heads the apps page with the Morphe wordmark, then offers the list's order and filter, patching
 * an app the list does not hold from its APK, and the search, after the bell bringing back the
 * alerts swiped aside while there are any.
 *
 * @param sortEnabled Whether the list's options can be opened, which a selection under way holds off.
 * @param sortOpen Whether the list's options are docked under the list, which the button then closes.
 */
@Composable
internal fun HomeHeader(
    apps: HomeAppListUi,
    appActions: HomeAppActions,
    search: SearchFieldState,
    alerts: HomeAlerts,
    sortEnabled: Boolean,
    sortOpen: Boolean,
    onSortClick: () -> Unit,
    onPatchFileClick: () -> Unit
) {
    val appName = stringResource(R.string.app_name)
    HeaderBar(
        title = {
            // At the start of the room the controls leave it, as a page's title is
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                MorpheWordmark(
                    modifier = Modifier
                        .height(WordmarkHeight)
                        .semantics {
                            heading()
                            contentDescription = appName
                        }
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        search = search,
        searchLabel = stringResource(R.string.home_search_apps),
        actions = {
            // Comes and goes with what it brings back, as the alerts it stands for do
            val snoozedCount = alerts.snoozedCount
            AnimatedVisibility(visible = snoozedCount > 0) {
                val shownCount = rememberLatest(snoozedCount.takeIf { it > 0 }) ?: 0
                TitleAction(
                    icon = MaterialSymbols.Rounded.Notifications,
                    contentDescription = pluralStringResource(
                        R.plurals.home_alerts_snoozed_count,
                        shownCount,
                        shownCount.toString()
                    ),
                    onClick = alerts::restoreSnoozed,
                    style = TitleActionStyle.Neutral
                )
            }

            val sortState = listOptionsStateDescription(
                apps.sortMode.labelRes,
                apps.filterMode.labelRes.takeIf { apps.filterMode.isActive }
            )
            TitleAction(
                icon = MaterialSymbols.Rounded.Sort.autoMirrored,
                contentDescription = stringResource(R.string.sort),
                onClick = onSortClick,
                // Engaged while the options are docked, and marked while any is off its default
                style = TitleActionStyle.Toggle,
                active = sortOpen,
                marked = homeListOptions(apps, appActions).any { it.isChanged },
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
    )
}

/** Height of the wordmark, its descender included, which sets its letters at about a page title's size. */
private val WordmarkHeight = 26.dp
