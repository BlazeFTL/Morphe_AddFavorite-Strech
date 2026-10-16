/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Gap between the rows of a segmented list, the one a connected group of buttons keeps. */
val SegmentGap = ButtonGroupDefaults.ConnectedSpaceBetween

/**
 * Settings that belong together, each row a segment of one group: rounded on the group's own
 * edges and nearly square where two rows meet, as Material's segmented lists are. Every outermost
 * [SurfaceCard] inside is a row however it is wrapped, so one shown or hidden reshapes the rest.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val registry = remember { SegmentRegistry() }
    CompositionLocalProvider(LocalSegmentRegistry provides registry) {
        Column(modifier = modifier.fillMaxWidth(), content = content)
    }
}

/** The group a [SurfaceCard] stands in as a row, null outside one and inside another card. */
internal val LocalSegmentRegistry = staticCompositionLocalOf<SegmentRegistry?> { null }

/**
 * The rows of a [SettingsGroup] in the order they stand on screen, so each takes the shape of its
 * place among those showing. Positions are only compared, so scrolling never rewrites the order.
 */
@Stable
internal class SegmentRegistry {
    private val tops = mutableMapOf<Any, Float>()
    private var order by mutableStateOf(emptyList<Any>())

    fun isPlaced(row: Any): Boolean = row in order

    fun shape(row: Any): RoundedCornerShape {
        val index = order.indexOf(row)
        return if (index < 0) ConnectedShapes.Card.middle else ConnectedShapes.Card.inColumn(index, order.size)
    }

    fun place(row: Any, top: Float) {
        if (tops.put(row, top) != top) reorder()
    }

    fun remove(row: Any) {
        if (tops.remove(row) != null) reorder()
    }

    private fun reorder() {
        val sorted = tops.entries.sortedBy { it.value }.map { it.key }
        if (sorted != order) order = sorted
    }
}
