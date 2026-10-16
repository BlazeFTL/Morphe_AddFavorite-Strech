/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/** Corner where two buttons of a connected group meet, nearly square so they read as one piece. */
private val ConnectedInnerCorner = CornerSize(4.dp)

/**
 * Shapes of the buttons of a connected group: [outer] on the group's own edges and a nearly
 * square corner where two buttons meet. Space the buttons by `ButtonGroupDefaults.ConnectedSpaceBetween`.
 */
@Immutable
class ConnectedShapes private constructor(outer: CornerSize) {
    val leading = RoundedCornerShape(
        topStart = outer,
        bottomStart = outer,
        topEnd = ConnectedInnerCorner,
        bottomEnd = ConnectedInnerCorner
    )
    val middle = RoundedCornerShape(ConnectedInnerCorner)
    val trailing = RoundedCornerShape(
        topStart = ConnectedInnerCorner,
        bottomStart = ConnectedInnerCorner,
        topEnd = outer,
        bottomEnd = outer
    )

    companion object {
        /** For buttons rounded as cards are, such as the result actions of the patcher. */
        val Card = ConnectedShapes(CornerSize(Defaults.CardCornerRadius))

        /** For pills, see [ActionPillButton]. */
        val Pill = ConnectedShapes(CornerSize(50))
    }
}
