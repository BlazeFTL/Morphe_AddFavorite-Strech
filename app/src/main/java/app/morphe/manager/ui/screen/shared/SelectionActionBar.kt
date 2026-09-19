/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.theme.MonochromeThemeDefaults

/**
 * Metrics of the [MultiSelectShell] surface. The bar floats over the content it belongs to, so
 * layouts underneath have to keep that content clear of it; the values they pad by live here
 * rather than being restated at each call site, where nothing would keep them in step.
 */
object MultiSelectBarDefaults {
    /** Padding the shell keeps above and below its surface. */
    val SurfacePadding = 8.dp

    /**
     * Height of a bar carrying a caption line and one row of pills. A bar with a second row is
     * taller, so layouts that can measure the bar go by its real height instead.
     */
    val Height = 100.dp

    /** Bottom padding a scrolling list needs for its last item to clear a bar [barHeight] tall. */
    fun listClearance(barHeight: Dp): Dp = barHeight - SurfacePadding

    /** Clearance for floating controls, which keep a little more air between them and the bar. */
    fun controlClearance(barHeight: Dp): Dp = barHeight - SurfacePadding / 2
}

/** Most pills a [SelectionActionBar] keeps on one row before its actions get a row of their own. */
private const val SINGLE_ROW_PILL_LIMIT = 4

/**
 * Slide-up surface used to host a multi-select action row. Keeps the surface, elevation
 * and enter/exit animations consistent between the home multi-select bar and the saved-APK
 * dialog footer.
 */
@Composable
fun MultiSelectShell(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = Animations.springSlideUpEnter,
        exit = Animations.springSlideDownExit,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MultiSelectBarDefaults.SurfacePadding),
            shape = RoundedCornerShape(16.dp),
            color = MonochromeThemeDefaults.surfaceColor(MaterialTheme.colorScheme.surfaceContainerHigh),
            shadowElevation = 8.dp,
            tonalElevation = 4.dp,
            content = content
        )
    }
}

private class LastVisibleValue<T>(var value: T)

/**
 * Returns [value] while [visible] is true, and the last value seen before that afterward.
 *
 * Action handlers clear the selection in the same pass that hides the bar, so a row rendered
 * from live state loses buttons and zeroes its counter while it is still sliding out.
 *
 * This holds only while both writes land in one snapshot. A handler that hides the bar and
 * clears its state in separate frames has nothing left to freeze by the time [visible] flips.
 */
@Composable
fun <T> rememberWhileVisible(visible: Boolean, value: T): T {
    val holder = remember { LastVisibleValue(value) }
    if (visible) holder.value = value
    return holder.value
}

/**
 * Caption line over one or more [ActionPillRow]s, padded to sit in a [MultiSelectShell].
 */
@Composable
fun ActionBarColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding, vertical = Defaults.ItemSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** The caption of an [ActionBarColumn]: a counter, a hint, or the title of what the bar acts on. */
@Composable
fun ActionBarCaption(text: String, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * One action a [SelectionActionBar] offers. The [label] is also the pill's content description
 * and tooltip, and keys it within the bar, so no two actions of one bar share a label.
 */
@Immutable
data class SelectionAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val colors: IconButtonColors? = null
)

/**
 * Counter line ("N selected") over the pills of a selection: SelectAll (or DeselectAll), the
 * caller's [actions] and [controls], and Cancel. Meant to be placed inside a [MultiSelectShell],
 * and padded to sit in one.
 *
 * [actions] act on the selected items, [controls] shape the selection itself, as reordering
 * does. A few pills share one row; past that, the actions take a row of their own over the
 * controls, so no pill is squeezed below its natural width.
 */
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    actions: List<SelectionAction>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onDeselectAll: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    controls: List<SelectionAction> = emptyList()
) {
    val selectAllLabel = stringResource(R.string.select_all)
    val selectAllDone = stringResource(R.string.select_all_done)
    val deselectAllLabel = stringResource(R.string.deselect_all)
    val deselectAllDone = stringResource(R.string.deselect_all_done)
    val cancelLabel = stringResource(android.R.string.cancel)
    val selectedLabel = stringResource(R.string.selected).lowercase()
    val allSelected = totalCount in 1..selectedCount
    val canToggleToDeselect = allSelected && onDeselectAll != null
    val selectionToggleLabel = if (canToggleToDeselect) deselectAllLabel else selectAllLabel
    val selectionToggleDone = if (canToggleToDeselect) deselectAllDone else selectAllDone

    val selectionToggle: @Composable () -> Unit = {
        ActionPillButton(
            onClick = { if (canToggleToDeselect) onDeselectAll() else onSelectAll() },
            icon = if (canToggleToDeselect) Icons.Outlined.RemoveDone else Icons.Outlined.DoneAll,
            contentDescription = selectionToggleLabel,
            tooltip = selectionToggleLabel,
            confirmation = selectionToggleDone,
            enabled = canToggleToDeselect || selectedCount < totalCount
        )
    }
    val cancel: @Composable () -> Unit = {
        if (onCancel != null) {
            ActionPillButton(
                onClick = onCancel,
                icon = Icons.Outlined.Close,
                contentDescription = cancelLabel,
                tooltip = cancelLabel
            )
        }
    }
    val pillCount = actions.size + controls.size + if (onCancel != null) 2 else 1

    ActionBarColumn(modifier = modifier.animateContentSize()) {
        AnimatedContent(
            targetState = selectedCount,
            transitionSpec = Animations.compactCounterTransitionSpec,
            label = "selected_count"
        ) { count ->
            ActionBarCaption(text = "$count $selectedLabel")
        }

        if (subtitle != null) {
            AnimatedContent(
                targetState = subtitle,
                transitionSpec = Animations.compactCounterTransitionSpec,
                label = "selection_subtitle"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }

        if (pillCount <= SINGLE_ROW_PILL_LIMIT) {
            ActionPillRow(fill = true) {
                selectionToggle()
                SelectionPills(actions)
                SelectionPills(controls)
                cancel()
            }
        } else {
            ActionPillRow(fill = true) { SelectionPills(actions) }
            ActionPillRow(fill = true) {
                selectionToggle()
                SelectionPills(controls)
                cancel()
            }
        }
    }
}

@Composable
private fun SelectionPills(actions: List<SelectionAction>) {
    actions.forEach { action ->
        // Keyed so a pill keeps its own state when an action ahead of it comes or goes
        key(action.label) {
            ActionPillButton(
                onClick = action.onClick,
                icon = action.icon,
                contentDescription = action.label,
                tooltip = action.label,
                enabled = action.enabled,
                colors = action.colors ?: ActionPillColors.neutral()
            )
        }
    }
}
