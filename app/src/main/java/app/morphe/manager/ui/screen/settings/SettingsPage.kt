/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*

/**
 * The settings page of the main screen: every [SettingsCategory] in its group, each opening a page
 * of its own.
 *
 * @param bottomBarHeight Height of the navigation floating over the page, which it ends clear of.
 * @param onCategoryPositioned Where each category's row sits, for the tour to point at.
 */
@Composable
fun SettingsPage(
    onOpenCategory: (SettingsCategory) -> Unit,
    bottomBarHeight: Dp,
    modifier: Modifier = Modifier,
    onCategoryPositioned: ((SettingsCategory, Rect) -> Unit)? = null
) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollFade(scrollState)
                .verticalScroll(scrollState)
                .padding(pageContentPadding(bottom = bottomBarHeight + Defaults.SpaceLarge)),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            PageHeader(title = stringResource(R.string.settings))

            SettingsCategory.entries.groupBy { it.group }.forEach { (group, categories) ->
                SectionTitle(text = stringResource(group.titleRes), icon = group.icon)
                SettingsGroup {
                    categories.forEach { category ->
                        SettingsItem(
                            onClick = { onOpenCategory(category) },
                            title = stringResource(category.titleRes),
                            subtitle = stringResource(category.summaryRes),
                            icon = category.icon,
                            modifier = onCategoryPositioned?.let { onPositioned ->
                                Modifier.onGloballyPositioned { onPositioned(category, it.boundsInWindow()) }
                            } ?: Modifier
                        )
                    }
                }
            }
        }

        ListScrollbar(scrollState = scrollState, modifier = Modifier.scrollbarSystemBarsPadding())
    }
}
