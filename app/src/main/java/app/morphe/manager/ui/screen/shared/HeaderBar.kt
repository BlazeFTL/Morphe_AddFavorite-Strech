/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.morphe.manager.R
import kotlin.math.roundToInt

/**
 * The top line every header shares, of a dialog or a page alike: a way back where there is one,
 * the [title], its [actions] and the toggle of its [search] last.
 *
 * Opening the search draws the toggle out into the field along the same line, covering the title
 * and pushing everything ahead of it off the screen, so only the toggle stays to close it again.
 *
 * @param searchLabel What the field searches, as its placeholder and the toggle's description.
 * @param searchEnabled Whether there is anything to search yet. The toggle stays in place while
 *   there is not, so the title does not reflow when a loading list fills in.
 * @param controlsModifier Applied to the actions and the toggle, such as an entrance of their own.
 */
@Composable
fun HeaderBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    search: SearchFieldState? = null,
    searchLabel: String? = null,
    searchEnabled: Boolean = true,
    controlsModifier: Modifier = Modifier
) {
    val label = searchLabel ?: stringResource(R.string.search)
    val open = search?.visible == true
    // A field restored open, such as on coming back to the page, stands open rather than opening again
    val progress = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) { progress.animateTo(if (open) 1f else 0f, SearchMorphSpec) }
    // Composed only while any of it shows, so a closed field holds no focus and takes no taps
    val fieldShown = remember { derivedStateOf { progress.value > 0f } }
    // What the open field covers is left out of what a screen reader goes through
    val covered = if (open) Modifier.clearAndSetSemantics {} else Modifier

    Layout(
        contents = listOf(
            { if (navigation != null) Box(covered) { navigation() } },
            { Box(covered) { title() } },
            {
                if (actions != null) {
                    Row(
                        modifier = controlsModifier.then(covered),
                        horizontalArrangement = Arrangement.spacedBy(ControlGap),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            },
            {
                if (search != null && fieldShown.value) {
                    ListSearchField(
                        value = search.query,
                        onValueChange = { search.query = it },
                        label = label,
                        // Only a field opened here takes the keyboard, not one restored open
                        requestFocus = remember { progress.value < 1f },
                        // Drawn out from the toggle's end, the toggle itself staying over that end
                        modifier = Modifier.graphicsLayer {
                            shape = EndAnchoredPill(
                                collapsedWidth = HeaderControlDefaults.Size.toPx(),
                                progress = progress.value
                            )
                            clip = true
                        },
                        trailing = { Spacer(Modifier.width(HeaderControlDefaults.Size)) }
                    )
                }
            },
            {
                if (search != null) {
                    Row(modifier = controlsModifier) { SearchToggleAction(search, label, enabled = searchEnabled) }
                }
            }
        ),
        modifier = modifier
    ) { (navigationM, titleM, actionsM, fieldM, toggleM), constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val groupGap = GroupGap.roundToPx()
        val controlGap = ControlGap.roundToPx()

        val navigationP = navigationM.firstOrNull()?.measure(loose)
        val actionsP = actionsM.firstOrNull()?.measure(loose)
        val toggleP = toggleM.firstOrNull()?.measure(loose)

        val navigationWidth = navigationP?.let { it.width + groupGap } ?: 0
        val toggleWidth = toggleP?.width ?: 0
        val controlsWidth = (actionsP?.width ?: 0) +
                (if (actionsP != null && toggleP != null) controlGap else 0) + toggleWidth
        val titleWidth = (width - navigationWidth - if (controlsWidth > 0) controlsWidth + groupGap else 0)
            .coerceAtLeast(0)
        val titleP = titleM.firstOrNull()?.measure(Constraints.fixedWidth(titleWidth).copy(maxHeight = constraints.maxHeight))
        val fieldP = fieldM.firstOrNull()?.measure(Constraints.fixedWidth(width).copy(maxHeight = constraints.maxHeight))

        val height = listOfNotNull(navigationP, titleP, actionsP, toggleP, fieldP)
            .maxOfOrNull { it.height }
            ?.coerceIn(constraints.minHeight, constraints.maxHeight) ?: constraints.minHeight

        layout(width, height) {
            val p = progress.value
            // Everything ahead of the toggle moves as one, pushed by the field's edge until it is off the screen
            val shift = -(p * (width - toggleWidth)).roundToInt()
            val fade = 1f - p
            fun centered(h: Int) = (height - h) / 2

            navigationP?.placeRelativeWithLayer(shift, centered(navigationP.height)) { alpha = fade }
            titleP?.placeRelativeWithLayer(navigationWidth + shift, centered(titleP.height)) { alpha = fade }
            actionsP?.placeRelativeWithLayer(
                width - controlsWidth + shift,
                centered(actionsP.height)
            ) { alpha = fade }
            fieldP?.placeRelative(0, centered(fieldP.height))
            toggleP?.placeRelative(width - toggleWidth, centered(toggleP.height))
        }
    }
}

/** Gap between the way back, the title and the controls of a [HeaderBar]. */
private val GroupGap = Defaults.SpaceMedium

/** Gap between the controls of a [HeaderBar], as a row of title actions keeps. */
private val ControlGap = Defaults.SpaceSmall

private val SearchMorphSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * The search field's pill [progress] of the way from the toggle's circle, [collapsedWidth] wide at
 * the field's end, out to the field's full width.
 */
private class EndAnchoredPill(private val collapsedWidth: Float, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val shown = collapsedWidth + (size.width - collapsedWidth) * progress
        val left = if (layoutDirection == LayoutDirection.Ltr) size.width - shown else 0f
        return Outline.Rounded(
            RoundRect(
                left = left,
                top = 0f,
                right = left + shown,
                bottom = size.height,
                cornerRadius = CornerRadius(size.height / 2)
            )
        )
    }
}
