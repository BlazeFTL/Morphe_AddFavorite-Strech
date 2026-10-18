/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.annotation.SuppressLint
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val ButtonPadding: Dp = 16.dp
private val DefaultScrollThresholdDp: Dp = 600.dp

/** How long the button stays once a scroll back up comes to rest. */
private val RestingHideDelay = 3.seconds

/**
 * Floating "scroll to top" button for a LazyColumn, shown while the user scrolls back up from
 * further down. Place inside a Box that overlays the list.
 */
@Composable
fun BoxScope.ScrollToTopButton(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    firstVisibleItemThreshold: Int = 3,
    extraBottomPadding: Dp = 0.dp
) {
    val awayFromTop by remember(listState, firstVisibleItemThreshold) {
        derivedStateOf { listState.firstVisibleItemIndex >= firstVisibleItemThreshold }
    }
    ScrollToTopButtonImpl(
        scrollable = listState,
        awayFromTop = awayFromTop,
        scrollToTop = { listState.animateScrollToTop() },
        modifier = modifier.align(Alignment.BottomEnd),
        extraBottomPadding = extraBottomPadding
    )
}

/**
 * Floating "scroll to top" button for a Column with verticalScroll, shown while the user scrolls
 * back up from further down. Place inside a Box that overlays the content.
 */
@Composable
fun BoxScope.ScrollToTopButton(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    scrollThreshold: Dp = DefaultScrollThresholdDp,
    extraBottomPadding: Dp = 0.dp
) {
    val thresholdPx = with(LocalDensity.current) { scrollThreshold.toPx().toInt() }
    val awayFromTop by remember(scrollState, thresholdPx) {
        derivedStateOf { scrollState.value >= thresholdPx }
    }
    ScrollToTopButtonImpl(
        scrollable = scrollState,
        awayFromTop = awayFromTop,
        scrollToTop = { scrollState.animateScrollTo(0) },
        modifier = modifier.align(Alignment.BottomEnd),
        extraBottomPadding = extraBottomPadding
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

/**
 * [RestingHideDelay] stretched to how long the user asked for controls to stay up in the system's
 * accessibility settings, as someone using switches or voice needs longer to reach the button.
 */
@SuppressLint("WrongConstant")
private fun restingHideDelay(accessibility: AccessibilityManager?): Duration {
    if (accessibility == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return RestingHideDelay
    return accessibility.getRecommendedTimeoutMillis(
        RestingHideDelay.inWholeMilliseconds.toInt(),
        AccessibilityManager.FLAG_CONTENT_CONTROLS
    ).milliseconds
}

@Composable
private fun ScrollToTopButtonImpl(
    scrollable: ScrollableState,
    awayFromTop: Boolean,
    scrollToTop: suspend () -> Unit,
    modifier: Modifier = Modifier,
    extraBottomPadding: Dp = 0.dp
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

    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    // The button sits over the trailing edge of the rows, where their switches and arrows are, so
    // it is offered only once the user heads back up and leaves again when they stop there to read
    var resting by remember(scrollable) { mutableStateOf(false) }
    LaunchedEffect(scrollable, accessibility) {
        snapshotFlow { scrollable.isScrollInProgress }.collectLatest { scrolling ->
            resting = false
            if (!scrolling) {
                // Read on each rest, so a change to the setting applies without leaving the screen
                delay(restingHideDelay(accessibility))
                resting = true
            }
        }
    }
    // A screen reader reaches it by focus rather than mid-swipe, so there it stays put
    val visible = awayFromTop && (a11y || (scrollable.lastScrolledBackward && !resting))

    AnimatedVisibility(
        visible = visible,
        enter = if (a11y) Animations.fadeIn else Animations.fabEnter,
        exit = if (a11y) Animations.fadeOut else Animations.fabExit,
        modifier = modifier.padding(
            end = ButtonPadding,
            bottom = ButtonPadding + extraBottomPadding
        )
    ) {
        // The see-through glass of the patcher's action bar, as round as it is tall
        ActionGlassButton(
            onClick = onClick,
            icon = Icons.Rounded.KeyboardArrowUp,
            contentDescription = label,
            modifier = Modifier.size(Defaults.GlassButtonHeight)
        )
    }
}
