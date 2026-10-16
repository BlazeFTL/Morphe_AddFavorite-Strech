/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/** Corner on the edges of a connected group, as round as any other button. */
private val OuterCorner = CornerSize(50)

/** Corner where two buttons of a connected group meet, nearly square so they read as one piece. */
private val InnerCorner = CornerSize(4.dp)

/**
 * Shapes of the buttons of a connected group: fully rounded on the group's own edges and nearly
 * square where two buttons meet. Space the buttons by `ButtonGroupDefaults.ConnectedSpaceBetween`.
 */
object ConnectedShapes {
    val Leading = RoundedCornerShape(
        topStart = OuterCorner,
        bottomStart = OuterCorner,
        topEnd = InnerCorner,
        bottomEnd = InnerCorner
    )
    val Middle = RoundedCornerShape(InnerCorner)
    val Trailing = RoundedCornerShape(
        topStart = InnerCorner,
        bottomStart = InnerCorner,
        topEnd = OuterCorner,
        bottomEnd = OuterCorner
    )
    val Top = RoundedCornerShape(
        topStart = OuterCorner,
        topEnd = OuterCorner,
        bottomStart = InnerCorner,
        bottomEnd = InnerCorner
    )
    val Bottom = RoundedCornerShape(
        topStart = InnerCorner,
        topEnd = InnerCorner,
        bottomStart = OuterCorner,
        bottomEnd = OuterCorner
    )

    /** Shape of the button at [index] of a row of [count], a plain pill for one on its own. */
    fun inRow(index: Int, count: Int): RoundedCornerShape = when {
        count == 1 -> Defaults.PillShape
        index == 0 -> Leading
        index == count - 1 -> Trailing
        else -> Middle
    }

    /** Shape of the button at [index] of a column of [count], a plain pill for one on its own. */
    fun inColumn(index: Int, count: Int): RoundedCornerShape = when {
        count == 1 -> Defaults.PillShape
        index == 0 -> Top
        index == count - 1 -> Bottom
        else -> Middle
    }
}

/**
 * [target] with its corners easing over from the shape it had before, so a button that is picked,
 * or moves to another place in its group, rounds out rather than jumping.
 */
@Composable
fun animateCornersAsState(target: CornerBasedShape): CornerBasedShape {
    val transition = remember { CornerTransition(target) }
    LaunchedEffect(target) {
        if (target == transition.to) return@LaunchedEffect
        // Set together before the next frame, so an interrupted change carries on from where it is
        transition.from = transition.current()
        transition.to = target
        transition.progress = 0f
        animate(0f, 1f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) { value, _ ->
            transition.progress = value
        }
    }
    return transition.current()
}

private class CornerTransition(shape: CornerBasedShape) {
    var from by mutableStateOf(shape)
    var to by mutableStateOf(shape)
    var progress by mutableFloatStateOf(1f)

    fun current(): CornerBasedShape = when (progress) {
        0f -> from
        1f -> to
        else -> to.copy(
            topStart = LerpCornerSize(from.topStart, to.topStart, progress),
            topEnd = LerpCornerSize(from.topEnd, to.topEnd, progress),
            bottomEnd = LerpCornerSize(from.bottomEnd, to.bottomEnd, progress),
            bottomStart = LerpCornerSize(from.bottomStart, to.bottomStart, progress)
        )
    }
}

/** A corner [fraction] of the way from [start] to [stop], resolved against the shape it rounds. */
private data class LerpCornerSize(val start: CornerSize, val stop: CornerSize, val fraction: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        lerp(start.toPx(shapeSize, density), stop.toPx(shapeSize, density), fraction)
}
