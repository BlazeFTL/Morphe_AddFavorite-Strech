/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.IconButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.withToast

/**
 * Reorder mode of a [MultiSelectBar]: whether it is on, and what its pills do. A bar given
 * none offers no reordering.
 */
internal class MultiSelectReorder(
    val isActive: Boolean,
    val onEnter: () -> Unit,
    val onSave: () -> Unit,
    val onReset: () -> Unit,
    val onCancel: () -> Unit
)

/**
 * Everything [MultiSelectBar] renders from state its own actions clear, kept together so it
 * can be frozen as a single value while the bar slides out. Anything that changes as a result
 * of using the bar belongs here rather than being read straight from a parameter.
 */
private data class MultiSelectDisplay(
    val count: Int,
    val total: Int,
    val reorder: MultiSelectReorder?,
    val contextAction: SelectionAction?
)

/** The same, for [CategoryActionBar]. */
private data class CategoryDisplay(
    val title: String?,
    val inReorderMode: Boolean,
    val showEditActions: Boolean
)

/**
 * Animated confirmation bar that slides up from the bottom of the card list
 * when the user is in multi-select mode.
 */
@Composable
internal fun MultiSelectBar(
    selectedCount: Int,
    totalCount: Int,
    visible: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onAction: () -> Unit,
    actionIcon: ImageVector,
    actionContentDescription: String,
    actionDoneMessage: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    reorder: MultiSelectReorder? = null,
    actionColors: IconButtonColors = ActionPillColors.destructive(),
    contextAction: SelectionAction? = null,
    onMoveToCategory: (() -> Unit)? = null,
    onPatchSelected: (() -> Unit)? = null,
    onPatchSources: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val cancelLabel = stringResource(android.R.string.cancel)
    val reorderListLabel = stringResource(R.string.reorder_list)
    val reorderListHint = stringResource(R.string.reorder_list_hint)
    val reorderDone = stringResource(R.string.reorder_done)
    val resetOrderLabel = stringResource(R.string.reset_order)
    val resetOrderDone = stringResource(R.string.reset_order_done)
    val doneLabel = stringResource(R.string.done)
    val moveToCategoryLabel = stringResource(R.string.home_category_move_to)
    val patchSelectedLabel = stringResource(R.string.batch_patch_action)
    val patchSourcesLabel = stringResource(R.string.sources_management_title)
    val primaryColors = ActionPillColors.primary()
    val secondaryColors = ActionPillColors.secondary()

    val selection = rememberWhileVisible(
        visible,
        MultiSelectDisplay(
            count = selectedCount,
            total = totalCount,
            reorder = reorder,
            contextAction = contextAction
        )
    )

    MultiSelectShell(visible = visible, modifier = modifier) {
        AnimatedContent(
            targetState = selection.reorder?.isActive == true,
            transitionSpec = Animations.fadeCrossfade(200),
            label = "multibar_mode"
        ) { inReorder ->
            val activeReorder = selection.reorder
            if (inReorder && activeReorder != null) {
                ActionBarColumn {
                    ActionBarCaption(text = reorderListHint)
                    ActionPillRow(fill = true) {
                        ActionPillButton(
                            onClick = activeReorder.onReset,
                            icon = Icons.Outlined.Restore,
                            contentDescription = resetOrderLabel,
                            tooltip = resetOrderLabel,
                            confirmation = resetOrderDone
                        )
                        ActionPillButton(
                            onClick = activeReorder.onCancel,
                            icon = Icons.Outlined.Close,
                            contentDescription = cancelLabel,
                            tooltip = cancelLabel
                        )
                        ActionPillButton(
                            onClick = context.withToast(reorderDone, activeReorder.onSave),
                            icon = Icons.Outlined.Check,
                            contentDescription = doneLabel,
                            tooltip = doneLabel
                        )
                    }
                }
            } else {
                val hasSelection = selection.count > 0
                SelectionActionBar(
                    selectedCount = selection.count,
                    totalCount = selection.total,
                    onSelectAll = onSelectAll,
                    onDeselectAll = onDeselectAll,
                    onCancel = onCancel,
                    actions = buildList {
                        if (onPatchSelected != null) {
                            add(
                                SelectionAction(
                                    icon = Icons.Outlined.AutoFixHigh,
                                    label = patchSelectedLabel,
                                    onClick = onPatchSelected,
                                    enabled = hasSelection,
                                    colors = primaryColors
                                )
                            )
                        }
                        if (onPatchSources != null) {
                            // Next to "Patch selected" rather than next to "Hide": both answer
                            // what patching these apps does, while "Hide" is about this screen
                            add(
                                SelectionAction(
                                    icon = Icons.Outlined.Source,
                                    label = patchSourcesLabel,
                                    onClick = onPatchSources,
                                    enabled = hasSelection,
                                    colors = secondaryColors
                                )
                            )
                        }
                        if (onMoveToCategory != null) {
                            add(
                                SelectionAction(
                                    icon = Icons.Outlined.FolderOpen,
                                    label = moveToCategoryLabel,
                                    onClick = onMoveToCategory,
                                    enabled = hasSelection
                                )
                            )
                        }
                        selection.contextAction?.let { add(it.copy(enabled = it.enabled && hasSelection)) }
                        add(
                            SelectionAction(
                                icon = actionIcon,
                                label = actionContentDescription,
                                onClick = context.withToast(actionDoneMessage, onAction),
                                enabled = hasSelection,
                                colors = actionColors
                            )
                        )
                    },
                    controls = listOfNotNull(
                        selection.reorder?.let {
                            SelectionAction(
                                icon = Icons.Outlined.Reorder,
                                label = reorderListLabel,
                                onClick = it.onEnter,
                                enabled = hasSelection
                            )
                        }
                    )
                )
            }
        }
    }
}

/**
 * Slide-up bar for the currently long-pressed category header.
 */
@Composable
internal fun CategoryActionBar(
    activeCategoryTitle: String?,
    visible: Boolean,
    isReorderMode: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onEnterReorder: () -> Unit,
    onExitReorder: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    showEditActions: Boolean = true
) {
    val cancelLabel = stringResource(android.R.string.cancel)
    val renameLabel = stringResource(R.string.rename)
    val deleteLabel = stringResource(R.string.delete)
    val reorderListLabel = stringResource(R.string.reorder_list)
    val reorderListHint = stringResource(R.string.reorder_list_hint)
    val doneLabel = stringResource(R.string.done)
    val destructiveColors = ActionPillColors.destructive()

    val category = rememberWhileVisible(
        visible,
        CategoryDisplay(
            title = activeCategoryTitle,
            inReorderMode = isReorderMode,
            showEditActions = showEditActions
        )
    )

    MultiSelectShell(visible = visible, modifier = modifier) {
        AnimatedContent(
            targetState = category.inReorderMode,
            transitionSpec = Animations.fadeCrossfade(200),
            label = "category_bar_mode"
        ) { inReorder ->
            ActionBarColumn {
                if (inReorder) {
                    ActionBarCaption(text = reorderListHint)
                    ActionPillRow(fill = true) {
                        ActionPillButton(
                            onClick = onExitReorder,
                            icon = Icons.Outlined.Check,
                            contentDescription = doneLabel,
                            tooltip = doneLabel
                        )
                    }
                } else {
                    val title = category.title
                    if (title != null) {
                        ActionBarCaption(text = title, maxLines = 1)
                    }
                    ActionPillRow(fill = true) {
                        if (category.showEditActions) {
                            ActionPillButton(
                                onClick = onRename,
                                icon = Icons.Outlined.Edit,
                                contentDescription = renameLabel,
                                tooltip = renameLabel
                            )
                        }
                        ActionPillButton(
                            onClick = onEnterReorder,
                            icon = Icons.Outlined.Reorder,
                            contentDescription = reorderListLabel,
                            tooltip = reorderListLabel
                        )
                        if (category.showEditActions) {
                            ActionPillButton(
                                onClick = onDelete,
                                icon = Icons.Outlined.Delete,
                                contentDescription = deleteLabel,
                                tooltip = deleteLabel,
                                colors = destructiveColors
                            )
                        }
                        ActionPillButton(
                            onClick = onCancel,
                            icon = Icons.Outlined.Close,
                            contentDescription = cancelLabel,
                            tooltip = cancelLabel
                        )
                    }
                }
            }
        }
    }
}
