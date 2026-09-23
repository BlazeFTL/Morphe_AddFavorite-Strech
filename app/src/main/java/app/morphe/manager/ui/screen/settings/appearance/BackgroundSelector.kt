/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.screen.shared.backgrounds.LocalBackdropInDialog
import app.morphe.manager.ui.viewmodel.RandomInterval

/**
 * Settings row naming the current background, opening [BackgroundPickerDialog] on tap.
 */
@Composable
fun BackgroundSettingsItem(
    selectedBackground: BackgroundType,
    selectedInterval: RandomInterval,
    onClick: () -> Unit
) {
    val name = stringResource(selectedBackground.displayNameResId)
    SettingsItem(
        onClick = onClick,
        title = stringResource(R.string.settings_appearance_background),
        subtitle = if (selectedBackground == BackgroundType.RANDOM) {
            "$name · ${stringResource(selectedInterval.labelResId)}"
        } else name,
        leadingContent = { ThemedIcon(icon = backgroundIcon(selectedBackground)) }
    )
}

/**
 * Background animation picker. A pick applies at once and plays behind the dialog, so the dialog
 * stays open to try another.
 * Includes a RANDOM option that reveals an interval selector when active.
 *
 * @param resolvedRandomBackground The background RANDOM currently stands for, previewed while it
 *        is the pick.
 */
@Composable
fun BackgroundPickerDialog(
    selectedBackground: BackgroundType,
    onBackgroundSelected: (BackgroundType) -> Unit,
    selectedInterval: RandomInterval,
    onIntervalSelected: (RandomInterval) -> Unit,
    onDismiss: () -> Unit,
    resolvedRandomBackground: BackgroundType?,
    enableParallax: Boolean,
    matrixUnlocked: Boolean = false
) {
    val windowSize = rememberWindowSize()
    val columns = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 3
        WindowWidthSizeClass.Medium -> 4
        WindowWidthSizeClass.Expanded -> 5
    }

    // All types except RANDOM - shown in the main grid, minus the hidden ones still to be found
    val gridTypes = BackgroundType.entries.filter {
        it != BackgroundType.RANDOM && (matrixUnlocked || it !in BackgroundType.HIDDEN)
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_appearance_background),
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        backdrop = {
            CompositionLocalProvider(LocalBackdropInDialog provides true) {
                AnimatedBackground(
                    type = selectedBackground,
                    resolvedType = resolvedRandomBackground,
                    enableParallax = enableParallax
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionGrid(items = gridTypes, columns = columns) { bgType, itemModifier ->
                ModernIconOptionCard(
                    selected = selectedBackground == bgType,
                    onClick = { onBackgroundSelected(bgType) },
                    icon = backgroundIcon(bgType),
                    label = stringResource(bgType.displayNameResId),
                    modifier = itemModifier
                )
            }

            // RANDOM option - full-width compact card at the bottom
            CompactOptionCard(
                selected = selectedBackground == BackgroundType.RANDOM,
                onClick = { onBackgroundSelected(BackgroundType.RANDOM) },
                icon = Icons.Outlined.Shuffle,
                label = stringResource(R.string.settings_appearance_background_random),
                modifier = Modifier.fillMaxWidth()
            )

            // Interval selector - visible only when RANDOM is active
            AnimatedVisibility(
                visible = selectedBackground == BackgroundType.RANDOM,
                enter = Animations.expandFadeEnter,
                exit = Animations.shrinkFadeExit
            ) {
                // Vertical tiles so the interval labels never truncate
                OptionGrid(
                    items = RandomInterval.entries,
                    columns = RandomInterval.entries.size
                ) { interval, itemModifier ->
                    ModernIconOptionCard(
                        selected = selectedInterval == interval,
                        onClick = { onIntervalSelected(interval) },
                        icon = intervalIcon(interval),
                        label = stringResource(interval.labelResId),
                        modifier = itemModifier
                    )
                }
            }
        }
    }
}

private fun backgroundIcon(type: BackgroundType): ImageVector = when (type) {
    BackgroundType.CIRCLES   -> Icons.Outlined.Circle
    BackgroundType.RINGS     -> Icons.Outlined.RadioButtonUnchecked
    BackgroundType.MESH      -> Icons.Outlined.Grid3x3
    BackgroundType.SPACE     -> Icons.Outlined.AutoAwesome
    BackgroundType.SHAPES    -> Icons.Outlined.Pentagon
    BackgroundType.SNOW      -> Icons.Outlined.AcUnit
    BackgroundType.GRID      -> Icons.Outlined.Apps
    BackgroundType.PARTICLES -> Icons.Outlined.BubbleChart
    BackgroundType.MATRIX    -> Icons.Outlined.Code
    BackgroundType.NONE      -> Icons.Outlined.VisibilityOff
    BackgroundType.RANDOM    -> Icons.Outlined.Shuffle
}

private fun intervalIcon(interval: RandomInterval): ImageVector = when (interval) {
    RandomInterval.ON_LAUNCH    -> Icons.Outlined.PlayCircleOutline
    RandomInterval.DAILY        -> Icons.Outlined.Today
    RandomInterval.EVERY_3_DAYS -> Icons.Outlined.DateRange
}
