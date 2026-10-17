/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import dev.chrisbanes.haze.HazeState

/**
 * Whether what a header floats over has scrolled on under it, which is when the header lays its
 * band down. At rest at the top the header stands on the screen bare, like the rest of it.
 */
@Stable
class HeaderBackdrop {
    private var source by mutableStateOf<ScrollableState?>(null)

    /** Whether the content runs on under the header, true while nothing is tracked. */
    val scrolledUnder: Boolean get() = source?.canScrollBackward ?: true

    internal fun track(state: ScrollableState) {
        source = state
    }

    internal fun release(state: ScrollableState) {
        if (source === state) source = null
    }
}

/** The backdrop of the header floating over the content this is drawn in, null where none does. */
val LocalHeaderBackdrop = staticCompositionLocalOf<HeaderBackdrop?> { null }

/**
 * Has the header over this content follow [state], the scroll of the list it heads. A pager hands
 * over the open page's state, which takes over from the page left.
 */
@Composable
internal fun TrackHeaderBackdrop(state: ScrollableState) {
    val backdrop = LocalHeaderBackdrop.current ?: return
    DisposableEffect(backdrop, state) {
        backdrop.track(state)
        onDispose { backdrop.release(state) }
    }
}

/**
 * A header's band of [glass] in [shape], blurring [haze], faded in while [LocalHeaderBackdrop] has
 * content under it and out at rest. Laid behind the header's content, filling it.
 */
@Composable
fun BoxScope.HeaderBand(glass: Color, shape: Shape, haze: HazeState? = LocalBackdropHaze.current) {
    val backdrop = LocalHeaderBackdrop.current
    val shown by animateFloatAsState(
        targetValue = if (backdrop?.scrolledUnder != false) 1f else 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "header_band"
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { alpha = shown }
            .glassFill(glass, shape, haze)
    )
}
