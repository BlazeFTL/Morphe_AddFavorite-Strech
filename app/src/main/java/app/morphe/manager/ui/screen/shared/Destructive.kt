/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.morphe.manager.ui.theme.isDarkTheme
import app.morphe.manager.util.isDarkBackground

/*
 * A destructive action sits on a faint veil with red only on its content and edge, so it never
 * outweighs the primary action, even on a red card. Every destructive pill, tile and button uses these.
 */

/** Destructive red for dark backgrounds. */
private val DestructiveColorDark = Color(0xFFFF6B6B)

/** Destructive red for light backgrounds. */
private val DestructiveColorLight = Color(0xFFD32F2F)

/** Alpha of the red edge a destructive action carries. */
private const val DESTRUCTIVE_EDGE_ALPHA = 0.35f

/**
 * Destructive content color readable on the current dialog background.
 * Shared with dialog content that marks a destructive choice outside a button.
 */
@Composable
fun dialogDestructiveColor(): Color =
    if (LocalDialogTextColor.current.isDarkBackground()) DestructiveColorLight else DestructiveColorDark

/**
 * The same red on the theme's own surfaces. A dark scheme's error color is a pastel meant for its
 * error container, and on the veil it reads as pink.
 */
@Composable
fun destructiveColor(): Color = if (isDarkTheme()) DestructiveColorDark else DestructiveColorLight

/**
 * Fill of a destructive action: a veil of [ink], the color text takes on the surface below, so it
 * lifts the action off any background without tinting it.
 */
@Composable
fun destructiveFill(ink: Color = MaterialTheme.colorScheme.onBackground): Color =
    ink.copy(alpha = if (ink.isDarkBackground()) 0.08f else 0.1f)

/** Edge of a destructive action drawn in [red]. */
@Composable
fun destructiveEdgeColor(red: Color = destructiveColor()): Color = red.copy(alpha = DESTRUCTIVE_EDGE_ALPHA)
