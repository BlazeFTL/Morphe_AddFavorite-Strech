/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * One action of a [FloatingPillBar].
 *
 * @param selected Set for a tab: whether it is the one on screen, which the pill's indicator
 *   slides under. Null for a plain action.
 * @param modifier Applied to the action's slot, for callers that track where it sits.
 */
@Immutable
class FloatingPillItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val tone: BottomActionTone = BottomActionTone.Neutral,
    val selected: Boolean? = null,
    val contentDescription: String? = null,
    val stateDescription: String? = null,
    val modifier: Modifier = Modifier
)

/**
 * Screen-level actions or tabs on a pill floating over the bottom of the content, which scrolls
 * on beneath it to the edge of the screen. [trailing] sits beside the pill, for a single action
 * that stands apart, see [FloatingPillRoundAction].
 *
 * The bar takes the navigation bar inset itself, so the content under it should not, and should
 * leave room for the bar's height at the end of its scroll, which [FloatingPillBarLayout] hands it.
 */
@Composable
fun FloatingPillBar(
    items: List<FloatingPillItem>,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = rememberWindowSize().contentPadding,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val background = MaterialTheme.colorScheme.background

    // Content scrolling under the bar dims gradually toward the bottom edge of the screen rather
    // than being cut off, while staying visible around the pill
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 1f to background.copy(alpha = 0.9f)))
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = Defaults.ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .padding(bottom = Defaults.ItemSpacing),
            // No spacing of its own: a trailing action brings its gap along, see [AnimatedRowPill]
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                // As wide as its actions need up to the room there is, so a few of them sit on a
                // compact pill in the middle rather than spread across the whole screen
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = PillInset * 2 + PillSlotMaxWidth * items.size)
                    .fillMaxWidth()
                    .height(FloatingPillHeight),
                shape = CircleShape,
                color = floatingPillColor(GlassButtonDefaults.containerColor()),
                border = CardBorder.of(GlassButtonDefaults.borderColor()),
                shadowElevation = FloatingPillElevation
            ) {
                BoxWithConstraints(modifier = Modifier.padding(horizontal = PillInset)) {
                    val weights = pillLabelWeights(items.map { it.label }, maxWidth)
                    // Horizontal span of each slot, which the selection indicator travels between
                    val slots = remember { mutableStateMapOf<Int, IntRange>() }

                    SelectionIndicator(
                        span = slots[items.indexOfFirst { it.selected == true }],
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEachIndexed { index, item ->
                            PillAction(
                                item = item,
                                weight = weights?.get(index) ?: 1f,
                                showLabel = weights != null,
                                modifier = Modifier.onPlaced { coordinates ->
                                    val left = coordinates.positionInParent().x.roundToInt()
                                    slots[index] = left until left + coordinates.size.width
                                }
                            )
                        }
                    }
                }
            }

            trailing()
        }
    }
}

/**
 * A pill of a row shown while [visible], scaling in and out together with the [gap] before it.
 * A row's own spacing would hold that gap until the pill is gone and then drop it at once.
 */
@Composable
fun AnimatedRowPill(visible: Boolean, gap: Dp = FloatingPillSpacing, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = Animations.pillEnter, exit = Animations.pillExit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(gap))
            content()
        }
    }
}

/** A round action standing beside the pill of a [FloatingPillBar], labeled by its tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingPillRoundAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: BottomActionTone = BottomActionTone.Accent
) {
    val colors = tone.colors()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            onClick = rememberHapticClick(onClick),
            modifier = Modifier
                .size(FloatingPillHeight)
                .pressScale(interactionSource, label = "floating_round_press_scale")
                .semantics { contentDescription = label },
            shape = CircleShape,
            color = floatingPillColor(colors.container),
            contentColor = colors.content,
            border = CardBorder.of(colors.border),
            shadowElevation = FloatingPillElevation,
            interactionSource = interactionSource
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(Defaults.IconSize)
                )
            }
        }
    }
}

/**
 * Lays [content] out under a [bar] floating over its bottom edge. The bar's height reaches the
 * content in the same pass, so the content ends clear of it from the first frame.
 */
@Composable
fun FloatingPillBarLayout(
    bar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(barHeight: Dp) -> Unit
) {
    val currentContent = rememberUpdatedState(content)
    val contentSlot = remember { HeightKeyedContent(currentContent) }

    SubcomposeLayout(modifier) { constraints ->
        val barPlaceables = subcompose(FloatingPillBarSlot.Bar, bar)
            .map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val barHeight = barPlaceables.maxOfOrNull { it.height } ?: 0
        val contentPlaceables = subcompose(FloatingPillBarSlot.Content, contentSlot.at(barHeight.toDp()))
            .map { it.measure(constraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            contentPlaceables.forEach { it.place(0, 0) }
            barPlaceables.forEach {
                it.place((constraints.maxWidth - it.width) / 2, constraints.maxHeight - it.height)
            }
        }
    }
}

private enum class FloatingPillBarSlot { Bar, Content }

/**
 * Content slot kept as the same lambda until the bar's height moves, since a subcomposition
 * handed a new lambda recomposes on every measure. The content is read from state.
 */
private class HeightKeyedContent(private val content: State<@Composable BoxScope.(Dp) -> Unit>) {
    private var height: Dp? = null
    private var slot: @Composable () -> Unit = {}

    fun at(height: Dp): @Composable () -> Unit {
        if (height != this.height) {
            this.height = height
            slot = { Box(Modifier.fillMaxSize()) { content.value(this, height) } }
        }
        return slot
    }
}

/**
 * Fill behind the selected tab, sliding over to the next one and taking its width rather than
 * jumping there. Filled and outlined as a selected segment or chip is, so all read as picked.
 */
@Composable
private fun SelectionIndicator(span: IntRange?, modifier: Modifier = Modifier) {
    span ?: return
    val left by animateIntAsState(span.first, Animations.listSpring(), label = "pill_indicator_left")
    val width by animateIntAsState(span.last + 1 - span.first, Animations.listSpring(), label = "pill_indicator_width")
    val density = LocalDensity.current
    val colors = BottomActionTone.Accent.colors()

    Box(
        modifier = modifier
            .offset { IntOffset(left, 0) }
            .width(with(density) { width.toDp() })
            .height(PillActionHeight)
            .background(colors.container, CircleShape)
            .border(SelectedEdgeWidth, colors.border, CircleShape)
    )
}

/** A glass tint laid on the screen background, so content scrolling under it stays hidden. */
@Composable
private fun floatingPillColor(glass: Color): Color =
    glass.compositeOver(MaterialTheme.colorScheme.background)

/**
 * One action of the pill: its icon over a short label, in a [weight] share of the pill. A tone
 * other than neutral fills the slot, while a selected tab sits on the shared indicator instead.
 * Without its label the action explains itself through a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.PillAction(
    item: FloatingPillItem,
    weight: Float,
    showLabel: Boolean,
    modifier: Modifier = Modifier
) {
    val tone = if (item.selected == true) BottomActionTone.Accent else item.tone
    val colors = tone.colors()
    val filled = item.selected == null && tone != BottomActionTone.Neutral
    val contentColor by animateColorAsState(colors.content, label = "pill_action_content")
    val description = item.contentDescription ?: item.label
    val interactionSource = remember { MutableInteractionSource() }

    val action: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(PillActionHeight)
                .pressScale(interactionSource, label = "pill_action_press_scale")
                .clip(CircleShape)
                .then(
                    if (filled) {
                        Modifier
                            .background(colors.container, CircleShape)
                            .border(SelectedEdgeWidth, colors.border, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = if (item.selected != null) Role.Tab else Role.Button,
                    onClick = rememberHapticClick(item.onClick)
                )
                .semantics {
                    contentDescription = description
                    item.stateDescription?.let { stateDescription = it }
                    item.selected?.let { selected = it }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            if (showLabel) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(horizontal = PillLabelPadding)
                        .clearAndSetSemantics {}
                )
            }
        }
    }

    // The weight must land on this Box so Row still sees it as the direct child; TooltipBox
    // applies its own modifier to an inner wrapper, which Row can't see
    Box(modifier = item.modifier.then(modifier).weight(weight)) {
        if (showLabel) {
            action()
        } else {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(description) } },
                state = rememberTooltipState()
            ) {
                action()
            }
        }
    }
}

/**
 * Shares of the pill's [width] for actions labelled [labels]: equal while every label fits its
 * equal share, sized to the labels while they fit together, and null once they cannot all fit,
 * which leaves the actions to their icons rather than cutting any label short.
 */
@Composable
private fun pillLabelWeights(labels: List<String>, width: Dp): List<Float>? {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current

    return remember(labels, width, style, density, measurer) {
        if (labels.isEmpty()) return@remember null
        val needed = labels.map { label ->
            with(density) { measurer.measure(label, style, maxLines = 1).size.width.toDp() } +
                PillLabelPadding * 2
        }
        val total = needed.fold(0.dp) { sum, it -> sum + it }
        when {
            needed.all { it <= width / labels.size } -> labels.map { 1f }
            // Room left over is shared evenly, so a short label is not squeezed against a long one
            total <= width -> needed.map { (it + (width - total) / labels.size).value }
            else -> null
        }
    }
}

private val FloatingPillHeight = 64.dp
private val FloatingPillSpacing = 6.dp
private val FloatingPillElevation = 3.dp
private val PillActionHeight = 52.dp
private val PillInset = 6.dp
private val PillSlotMaxWidth = 96.dp
private val PillLabelPadding = 4.dp
private val SelectedEdgeWidth = 1.dp
