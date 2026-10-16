/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * How far the collapsible part of a header floating over a full-height [AppDialog] has folded away
 * under the rest of it, following the list's scroll: it folds as the list moves on and unfolds as
 * soon as it moves back, so what it holds is a short scroll up away wherever the list is.
 *
 * The header keeps the room it takes unfolded, so the list's insets hold still while it folds and
 * the list's first row always comes out from under the whole of it.
 */
@Stable
class DialogHeaderCollapse {
    /** Pixels folded away, from none up to the collapsible part's height. */
    var folded by mutableFloatStateOf(0f)
        private set

    // The collapsible part's height, as last measured. Read only while scrolling, so not observed
    private var range = 0

    /** Moves [folded] along with the scroll of whatever list it is attached to. */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            folded = (folded - available.y).coerceIn(0f, range.toFloat())
            // The header floats over the list, so the list scrolls the whole way regardless
            return Offset.Zero
        }
    }

    /**
     * Unfolds the whole of the collapsible part, for a list moved back to its start by other means
     * than a scroll, such as a page or a filter changing, which would otherwise open under a header
     * still folded from before.
     */
    suspend fun unfold() {
        animate(folded, 0f, animationSpec = tween(Defaults.ANIMATION_DURATION)) { value, _ -> folded = value }
    }

    internal fun measured(height: Int) {
        range = height
        if (folded > height) folded = height.toFloat()
    }
}

/** The collapse of the header over the dialog this is drawn in, null where nothing folds. */
val LocalDialogHeaderCollapse = staticCompositionLocalOf<DialogHeaderCollapse?> { null }

/** Lets [collapse] follow the scroll of a list laid out within this. */
internal fun Modifier.drivesHeaderCollapse(collapse: DialogHeaderCollapse): Modifier =
    nestedScroll(collapse.connection)

/** The collapsible part of a header: shortened by what [collapse] has folded, sliding up under the rest. */
internal fun Modifier.foldsWith(collapse: DialogHeaderCollapse): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    collapse.measured(placeable.height)
    val height = (placeable.height - collapse.folded.roundToInt()).coerceAtLeast(0)
    layout(placeable.width, height) {
        placeable.place(0, height - placeable.height)
    }
}

/**
 * A header taking up the room it would unfold whatever [collapse] has folded, so what lays the
 * list out under it sees one height throughout. The room left over is empty and lets touches through.
 */
internal fun Modifier.holdsUnfoldedHeight(collapse: DialogHeaderCollapse): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height + collapse.folded.roundToInt()) {
        placeable.place(0, 0)
    }
}
