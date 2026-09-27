/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.morphe.manager.util.requiresLightContent

/**
 * Color of the header a [TitleAction] sits in, as [usableAppAccent] gives it, so its circle reads
 * as part of that header rather than the surrounding theme. Null keeps the theme's palette.
 */
val LocalTitleActionAccent = compositionLocalOf<Color?> { null }

/** Visual style of a [TitleAction]. */
enum class TitleActionStyle {
    /** Flat [IconButton] with the surrounding text tint. Use for info and reset actions */
    Plain,
    /** Tonal circle in the primary palette. Use for standing actions such as add or sort */
    Accent,
    /** Tonal circle in the error palette. Use for bulk destructive actions */
    Destructive,
    /** Neutral tonal circle that fills with the primary palette while active */
    Toggle,
    /** Toggle for headers whose other actions are already [Accent], so it lifts a further step */
    AccentToggle
}

/**
 * Icon action rendered in the title row of an [AppDialog] or [AppBottomSheet]. Uniforms the
 * button styles used across headers so callers only pick an icon and a semantic style.
 *
 * @param active Whether a [TitleActionStyle.Toggle] or [TitleActionStyle.AccentToggle] is engaged.
 * @param enabled Whether the action can be used. A header keeps its actions in place while they
 * are out of reach, so the title never shifts as they come and go.
 */
@Composable
fun TitleAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TitleActionStyle = TitleActionStyle.Plain,
    active: Boolean = false,
    enabled: Boolean = true
) {
    // Pinned to the container the button already draws, otherwise it reserves the 48dp touch
    // target around it and doubles the gap the title row asks for
    val sizedModifier = modifier.size(IconButtonDefaults.smallContainerSize())

    // An accented header tints its circles a step over its band, as it does its badges, and fills
    // an engaged toggle with the accent outright so the state reads as plainly as on the theme
    val accent = LocalTitleActionAccent.current

    // Null marks the flat variant, which draws no circle at all
    val containerColor = when (style) {
        TitleActionStyle.Plain -> null
        TitleActionStyle.Accent -> accent?.copy(alpha = 0.3f) ?: MaterialTheme.colorScheme.primaryContainer
        // Kept in the error palette on any header, where the red warns rather than decorates
        TitleActionStyle.Destructive -> MaterialTheme.colorScheme.errorContainer
        TitleActionStyle.Toggle -> if (active) {
            accent ?: MaterialTheme.colorScheme.primaryContainer
        } else {
            accent?.copy(alpha = 0.18f) ?: MaterialTheme.colorScheme.surfaceVariant
        }

        TitleActionStyle.AccentToggle -> if (active) {
            accent ?: MaterialTheme.colorScheme.primary
        } else {
            accent?.copy(alpha = 0.3f) ?: MaterialTheme.colorScheme.primaryContainer
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressedModifier = sizedModifier.pressScale(
        interactionSource = interactionSource,
        enabled = enabled,
        label = "title_action_press_scale"
    )

    if (containerColor == null) {
        IconButton(
            onClick = onClick,
            modifier = pressedModifier,
            enabled = enabled,
            interactionSource = interactionSource
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(Defaults.IconSize),
                tint = LocalDialogTextColor.current.copy(alpha = if (enabled) 1f else Defaults.DISABLED_ALPHA)
            )
        }
    } else {
        // A see-through tint takes the header's text color, as the badges beside it do, and a
        // solid accent whichever of black and white stands out of it
        val contentColor = when {
            accent == null || style == TitleActionStyle.Destructive -> contentColorFor(containerColor)
            containerColor.alpha < 1f -> MaterialTheme.colorScheme.onBackground
            containerColor.requiresLightContent() -> Color.White
            else -> Color.Black
        }

        FilledTonalIconButton(
            onClick = onClick,
            modifier = pressedModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                // Scaled rather than set, so a tint that is already see-through fades further
                disabledContainerColor = containerColor.copy(alpha = containerColor.alpha * Defaults.DISABLED_ALPHA),
                disabledContentColor = LocalDialogTextColor.current.copy(alpha = Defaults.DISABLED_ALPHA)
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(Defaults.IconSize)
            )
        }
    }
}
