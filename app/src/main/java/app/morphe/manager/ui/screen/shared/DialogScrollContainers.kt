/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Gap between the cards of a dialog's list, see [CompactCardSpacing] for compact ones. */
val DialogListSpacing = Defaults.SpaceMedium

/**
 * A dialog's lazy list, fading at whichever end has more to show, with a [ListScrollbar] and a
 * [ScrollToTopButton] set out at the dialog's true edge rather than its content inset.
 *
 * @param pinnedFirstRow Whether the first row sticks to the top, see [verticalScrollFade].
 */
@Composable
fun DialogLazyList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    pinnedFirstRow: Boolean = false,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    val footerInset = LocalDialogBottomInset.current
    val edgeOffset = Modifier.offset(x = LocalDialogHorizontalInset.current)

    Box(modifier = modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScrollFade(state, pinnedFirstRow = pinnedFirstRow),
            contentPadding = contentPadding.plusBottom(footerInset),
            verticalArrangement = verticalArrangement,
            userScrollEnabled = userScrollEnabled,
            content = content
        )

        ListScrollbar(listState = state, modifier = edgeOffset.padding(bottom = footerInset))
        ScrollToTopButton(listState = state, modifier = edgeOffset, extraBottomPadding = footerInset)
    }
}

/**
 * [DialogLazyList] for content laid out in full rather than row by row, such as a few sections of
 * details that are never long enough to need a lazy list.
 */
@Composable
fun DialogScrollColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    val footerInset = LocalDialogBottomInset.current
    val edgeOffset = Modifier.offset(x = LocalDialogHorizontalInset.current)

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScrollFade(state)
                .verticalScroll(state)
                .padding(contentPadding.plusBottom(footerInset)),
            verticalArrangement = verticalArrangement,
            content = content
        )

        ListScrollbar(scrollState = state, modifier = edgeOffset.padding(bottom = footerInset))
        ScrollToTopButton(scrollState = state, modifier = edgeOffset, extraBottomPadding = footerInset)
    }
}

/** These paddings with [extra] more at the bottom, such as room for a bar floating over it. */
@Composable
private fun PaddingValues.plusBottom(extra: Dp): PaddingValues {
    if (extra == 0.dp) return this
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + extra
    )
}
