/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Has a scrolling list that runs on under bars at its ends bring a focused row, such as a search
 * field, out from under them rather than up to the list's own edge, [top] and [bottom] in from it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvideBringIntoViewInsets(top: Dp, bottom: Dp, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val spec = remember(top, bottom, density) {
        InsetBringIntoViewSpec(with(density) { top.toPx() }, with(density) { bottom.toPx() })
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/** The platform's own scroll into view, measured within the part of the list the bars leave clear. */
private class InsetBringIntoViewSpec(private val top: Float, private val bottom: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        PlatformSpec.calculateScrollDistance(offset - top, size, (containerSize - top - bottom).coerceAtLeast(size))

    private companion object {
        val PlatformSpec = object : BringIntoViewSpec {}
    }
}
