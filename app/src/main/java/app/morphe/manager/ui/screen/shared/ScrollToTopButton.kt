/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

private val ButtonSize: Dp = 44.dp
private val ButtonPadding: Dp = 16.dp
private val DefaultScrollThresholdDp: Dp = 600.dp

/**
 * Floating "scroll to top" button for a LazyColumn.
 * Auto-hides when the list is near the top; place inside a Box that overlays the list.
 *
 * @param haze The list it floats over, which it then blurs, or null to draw it near opaque.
 */
@Composable
fun BoxScope.ScrollToTopButton(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    firstVisibleItemThreshold: Int = 3,
    extraBottomPadding: Dp = 0.dp,
    haze: HazeState? = null
) {
    val visible by remember(listState, firstVisibleItemThreshold) {
        derivedStateOf { listState.firstVisibleItemIndex >= firstVisibleItemThreshold }
    }
    ScrollToTopButtonImpl(
        visible = visible,
        scrollToTop = { listState.animateScrollToTop() },
        modifier = modifier.align(Alignment.BottomEnd),
        extraBottomPadding = extraBottomPadding,
        haze = haze
    )
}

/**
 * Floating "scroll to top" button for a Column with verticalScroll.
 * Auto-hides when content is near the top; place inside a Box that overlays the content.
 *
 * @param haze The content it floats over, which it then blurs, or null to draw it near opaque.
 */
@Composable
fun BoxScope.ScrollToTopButton(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    scrollThreshold: Dp = DefaultScrollThresholdDp,
    extraBottomPadding: Dp = 0.dp,
    haze: HazeState? = null
) {
    val thresholdPx = with(LocalDensity.current) { scrollThreshold.toPx().toInt() }
    val visible by remember(scrollState, thresholdPx) {
        derivedStateOf { scrollState.value >= thresholdPx }
    }
    ScrollToTopButtonImpl(
        visible = visible,
        scrollToTop = { scrollState.animateScrollTo(0) },
        modifier = modifier.align(Alignment.BottomEnd),
        extraBottomPadding = extraBottomPadding,
        haze = haze
    )
}

/**
 * Scrolls the list back to its start in one motion. [LazyListState.animateScrollToItem] travels by
 * estimated row sizes until the first row shows and then starts over for the rest of it. The
 * scroll position alone says where the list is, so from far off it jumps to a set distance from the
 * start and animates that exact distance back.
 */
private suspend fun LazyListState.animateScrollToTop() {
    if (firstVisibleItemIndex == 0) {
        animateScrollBy(-firstVisibleItemScrollOffset.toFloat())
        return
    }

    // How far down the list sits, estimated from the rows in view and held to a screenful, so the
    // jump stays within the rows the animation then passes over
    val layout = layoutInfo
    val rows = layout.visibleItemsInfo
    val averageRow = rows.sumOf { it.size } / rows.size.coerceAtLeast(1) + layout.mainAxisItemSpacing
    val distance = minOf(
        layout.viewportSize.height,
        averageRow * firstVisibleItemIndex + firstVisibleItemScrollOffset
    )
    scrollToItem(0, distance)
    animateScrollBy(-distance.toFloat())
}

@Composable
private fun ScrollToTopButtonImpl(
    visible: Boolean,
    scrollToTop: suspend () -> Unit,
    modifier: Modifier = Modifier,
    extraBottomPadding: Dp = 0.dp,
    haze: HazeState? = null
) {
    val scope = rememberCoroutineScope()
    // A scroll made in code bypasses the header, so it is unfolded alongside
    val headerCollapse = LocalDialogHeaderCollapse.current
    val onClick: () -> Unit = {
        scope.launch { scrollToTop() }
        if (headerCollapse != null) scope.launch { headerCollapse.unfold() }
    }
    val a11y = rememberAccessibilityEnabled()
    val label = stringResource(R.string.accessibility_scroll_to_top)

    AnimatedVisibility(
        visible = visible,
        enter = if (a11y) Animations.fadeIn else Animations.fabEnter,
        exit = if (a11y) Animations.fadeOut else Animations.fabExit,
        modifier = modifier.padding(
            end = ButtonPadding,
            bottom = ButtonPadding + extraBottomPadding
        )
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val shape = rememberPressShape(interactionSource, CircleShape)
        val surface = MaterialTheme.colorScheme.surface
        Surface(
            onClick = rememberHapticClick(onClick),
            shape = shape,
            // Blurs the rows of text it floats over where it can, else stays nearly opaque over them
            color = if (haze != null) Color.Transparent else surface.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            // Edged in the dialog's color, as its outlined buttons are
            border = CardBorder.of(
                LocalAccent.current?.let { appAccentBorder(it) }
                    ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            ),
            interactionSource = interactionSource,
            modifier = Modifier
                .size(ButtonSize)
        ) {
            // Inside rather than on the Surface, which widens to the minimum touch target past its outline
            Box(
                modifier = if (haze != null) Modifier.fillMaxSize().glassFill(surface, shape, haze) else Modifier,
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = label,
                    modifier = Modifier.size(Defaults.IconSize)
                )
            }
        }
    }
}
