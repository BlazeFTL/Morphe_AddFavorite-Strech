/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

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
