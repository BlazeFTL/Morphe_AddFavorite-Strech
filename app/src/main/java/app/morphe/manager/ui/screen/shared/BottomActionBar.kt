/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

private val BarMaxWidth = 540.dp
private const val EnterFadeMillis = 200

/** Emphasis of a [BottomActionButton], resolved through [GlassButtonDefaults]. */
enum class BottomActionTone {
    Neutral,
    Accent,
    Highlight,
    Destructive
}

/** Content receiver of [BottomActionBar]: a [RowScope] plus the state its buttons need. */
class BottomActionBarScope internal constructor(
    rowScope: RowScope,
    val showLabels: Boolean,
    internal val lookaheadScope: LookaheadScope?
) : RowScope by rowScope

/**
 * Centered, width-capped row of [BottomActionButton]s, weighted equally and animated as the set
 * changes. [labels] lists one entry per button so the bar decides once whether they all fit.
 */
@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    content: @Composable BottomActionBarScope.() -> Unit
) {
    val reduceMotion = rememberAccessibilityEnabled()

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = BarMaxWidth)
                .fillMaxWidth()
        ) {
            val showLabels = labelsFit(labels, maxWidth)
            LookaheadScope {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The gap between buttons and the gap below them are one rhythm, so the
                        // bar reads as evenly spaced rather than wider in one direction
                        .padding(bottom = Defaults.ItemSpacing)
                        .padding(horizontal = Defaults.ContentPadding),
                    horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomActionBarScope(
                        rowScope = this,
                        showLabels = showLabels,
                        lookaheadScope = if (reduceMotion) null else this@LookaheadScope
                    ).content()
                }
            }
        }
    }
}

/**
 * Single button of a [BottomActionBar]. A hidden label falls back to a tooltip;
 * [showProgress] swaps the icon for a spinner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomActionBarScope.BottomActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    text: String? = null,
    showLabel: Boolean = false,
    tone: BottomActionTone = BottomActionTone.Neutral,
    enabled: Boolean = true,
    showProgress: Boolean = false,
    contentDescription: String? = null,
    stateDescription: String? = null
) {
    val colors = tone.colors()
    val loadingLabel = stringResource(R.string.loading)

    val label = contentDescription ?: text
    val accessibleLabel = remember(label, showProgress, loadingLabel) {
        when {
            label == null -> null
            showProgress -> "$label, $loadingLabel"
            else -> label
        }
    }

    // A button joining a live bar has no previous bounds to grow from, so it fades in while its
    // neighbours give way; leaving is instant, the row simply closes over it
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered || lookaheadScope == null) 1f else 0f,
        animationSpec = tween(EnterFadeMillis),
        label = "bottom_action_enter"
    )

    val button: @Composable (Modifier) -> Unit = { outerModifier ->
        GlassButton(
            label = text.orEmpty(),
            selected = false,
            onClick = onClick,
            modifier = outerModifier
                .fillMaxWidth()
                .semantics {
                    if (stateDescription != null) {
                        this.stateDescription = stateDescription
                    }
                    if (showProgress) {
                        liveRegion = LiveRegionMode.Polite
                    }
                },
            icon = icon,
            enabled = enabled,
            showProgress = showProgress,
            contentDescription = accessibleLabel,
            containerColor = colors.container.dim(enabled),
            contentColor = colors.content.dim(enabled),
            border = BorderStroke(1.dp, colors.border.dim(enabled)),
            role = Role.Button,
            pressScale = true,
            hapticFeedback = true,
            showLabel = showLabel
        )
    }

    // The weight/positioning modifier must land on this Box so Row still sees it as the direct
    // child; TooltipBox applies its own modifier to an inner wrapper, which Row can't see
    Box(
        modifier = modifier
            .then(lookaheadScope?.let { Modifier.animateBounds(it) } ?: Modifier)
            .graphicsLayer { alpha = enterAlpha }
    ) {
        // Only surface a tooltip when the label itself is hidden; otherwise the two would repeat
        if (!showLabel && text != null) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(text) } },
                state = rememberTooltipState(),
                modifier = Modifier.fillMaxWidth()
            ) {
                button(Modifier)
            }
        } else {
            button(Modifier.fillMaxWidth())
        }
    }
}

/** Whether every one of [labels] fits its slot of a bar [barWidth] wide. */
@Composable
private fun labelsFit(labels: List<String>, barWidth: Dp): Boolean {
    if (labels.isEmpty()) return false

    val measurer = rememberTextMeasurer()
    val style = GlassButtonDefaults.labelStyle
    val density = LocalDensity.current

    return remember(labels, barWidth, style, density, measurer) {
        val slotWidth = (barWidth - Defaults.ContentPadding * 2 -
                Defaults.ItemSpacing * (labels.size - 1)) / labels.size
        val labelWidth = slotWidth - GlassButtonDefaults.LabelInset
        labelWidth > 0.dp && labels.all { label ->
            val measured = with(density) { measurer.measure(label, style).size.width.toDp() }
            measured <= labelWidth
        }
    }
}

@Immutable
private data class BottomActionColors(
    val container: Color,
    val content: Color,
    val border: Color
)

@Composable
private fun BottomActionTone.colors(): BottomActionColors {
    val scheme = MaterialTheme.colorScheme
    // Every tone but Neutral borrows the selected treatment of the tab bar, so an emphasized
    // action reads at the same weight as the active settings tab
    return when (this) {
        BottomActionTone.Neutral -> BottomActionColors(
            container = GlassButtonDefaults.containerColor(),
            content = GlassButtonDefaults.contentColor(),
            border = GlassButtonDefaults.borderColor()
        )

        BottomActionTone.Accent -> BottomActionColors(
            container = GlassButtonDefaults.containerColor(selected = true),
            content = GlassButtonDefaults.contentColor(selected = true),
            border = GlassButtonDefaults.borderColor(selected = true)
        )

        BottomActionTone.Highlight -> BottomActionColors(
            container = GlassButtonDefaults.containerColor(scheme.tertiaryContainer, selected = true),
            content = GlassButtonDefaults.contentColor(scheme.onTertiaryContainer, selected = true),
            border = GlassButtonDefaults.borderColor(scheme.tertiary, selected = true)
        )

        BottomActionTone.Destructive -> BottomActionColors(
            container = GlassButtonDefaults.containerColor(scheme.errorContainer, selected = true),
            content = GlassButtonDefaults.contentColor(scheme.onErrorContainer, selected = true),
            border = GlassButtonDefaults.borderColor(scheme.error, selected = true)
        )
    }
}

/** Halves the alpha of a resolved glass color while the button is disabled. */
private fun Color.dim(enabled: Boolean): Color =
    if (enabled) this else copy(alpha = alpha * 0.5f)
