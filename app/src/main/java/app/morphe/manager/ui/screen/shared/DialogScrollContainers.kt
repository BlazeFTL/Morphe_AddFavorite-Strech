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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Gap between the cards of a dialog's list, see [CompactCardSpacing] for compact ones. */
val DialogListSpacing = Defaults.SpaceMedium

/**
 * A dialog's lazy list, fading at whichever end has more to show, with [DialogScrollIndicators]
 * over it. Its ends leave room for the bars floating over a [ListDialog], see [LocalDialogBarInsets].
 */
@Composable
fun DialogLazyList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    val barInsets = LocalDialogBarInsets.current
    val haze = rememberHazeState()
    val rowResizing = remember { ListRowResizing() }

    Box(modifier = modifier) {
        CompositionLocalProvider(LocalListRowResizing provides rowResizing) {
            LazyColumn(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .hazeSource(haze)
                    .verticalScrollFade(state),
                contentPadding = contentPadding + barInsets,
                verticalArrangement = verticalArrangement,
                userScrollEnabled = userScrollEnabled,
                content = content
            )
        }

        DialogScrollIndicators(listState = state, haze = haze)
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
    val barInsets = LocalDialogBarInsets.current
    val haze = rememberHazeState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hazeSource(haze)
                .verticalScrollFade(state)
                .verticalScroll(state)
                .padding(contentPadding + barInsets),
            verticalArrangement = verticalArrangement,
            content = content
        )

        DialogScrollIndicators(scrollState = state, haze = haze)
    }
}

/**
 * A dialog list's [ListScrollbar] and [ScrollToTopButton], set out at the dialog's true edge rather
 * than its content inset and clear of the bars floating over a [ListDialog].
 *
 * @param haze The list, for the button to blur. A list of its own rather than the dialog's, which
 *   the button is drawn as part of.
 */
@Composable
fun BoxScope.DialogScrollIndicators(listState: LazyListState, haze: HazeState? = null) {
    val edgeOffset = Modifier.offset(x = LocalDialogHorizontalInset.current)
    ListScrollbar(listState = listState, modifier = edgeOffset.padding(LocalDialogScrollbarInsets.current))
    ScrollToTopButton(
        listState = listState,
        modifier = edgeOffset,
        extraBottomPadding = LocalDialogBarInsets.current.calculateBottomPadding(),
        haze = haze
    )
}

/** [DialogScrollIndicators] for content laid out in full, see [DialogScrollColumn]. */
@Composable
fun BoxScope.DialogScrollIndicators(scrollState: ScrollState, haze: HazeState? = null) {
    val edgeOffset = Modifier.offset(x = LocalDialogHorizontalInset.current)
    ListScrollbar(scrollState = scrollState, modifier = edgeOffset.padding(LocalDialogScrollbarInsets.current))
    ScrollToTopButton(
        scrollState = scrollState,
        modifier = edgeOffset,
        extraBottomPadding = LocalDialogBarInsets.current.calculateBottomPadding(),
        haze = haze
    )
}

/** These paddings and [other] together, such as room for the bars floating over a list. */
@Composable
private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + other.calculateStartPadding(direction),
        top = calculateTopPadding() + other.calculateTopPadding(),
        end = calculateEndPadding(direction) + other.calculateEndPadding(direction),
        bottom = calculateBottomPadding() + other.calculateBottomPadding()
    )
}
