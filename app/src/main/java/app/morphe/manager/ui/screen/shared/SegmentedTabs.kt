/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** One segment of a [SegmentedTabs]. */
data class SegmentedTab(
    val label: String,
    val icon: ImageVector
)

private val SegmentInset = 4.dp

/** How fast a swipe has to end to carry on to the next page however little of it was dragged. */
private val SwipeFlingVelocity = 400.dp

/**
 * A few modes of a dialog as a pill split into equally wide segments over a pager of their pages,
 * switched by tapping a segment or by swiping anywhere across the tabs, [below] included.
 *
 * Dialogs render over a translucent background where a thin tab indicator washes out, so the
 * selected mode sits on a solid fill instead, which follows a swipe as it goes.
 *
 * @param selectedIndex Page shown, held by the caller so the rest of the dialog can follow it.
 * @param onSelect Called once a page has settled, whether it was tapped or swiped to.
 * @param below Content every page shares, laid out under the pager rather than repeated in it.
 */
@Composable
fun SegmentedTabs(
    options: List<SegmentedTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = Defaults.ContentPadding,
    below: @Composable ColumnScope.() -> Unit = {},
    page: @Composable (index: Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = selectedIndex) { options.size }
    val scope = rememberCoroutineScope()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val flingVelocity = with(LocalDensity.current) { SwipeFlingVelocity.toPx() }

    // Reported once settled rather than as the page changes hands mid-swipe, so the caller
    // moving the pager below can never pull it out from under a finger
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnSelect(it) }
    }
    LaunchedEffect(selectedIndex) {
        if (pagerState.settledPage != selectedIndex) pagerState.animateScrollToPage(selectedIndex)
    }

    // The pager only hears swipes over the pages, so everything around them drives it from here.
    // A swipe over the pages is taken by the pager first and never reaches this
    val swipeState = rememberDraggableState { delta ->
        pagerState.dispatchRawDelta(if (isRtl) delta else -delta)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .draggable(
                state = swipeState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                    val forward = if (isRtl) velocity else -velocity
                    val target = when {
                        forward > flingVelocity -> floor(position).toInt() + 1
                        forward < -flingVelocity -> ceil(position).toInt() - 1
                        else -> position.roundToInt()
                    }
                    pagerState.animateScrollToPage(target.coerceIn(0, options.lastIndex))
                }
            ),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        SegmentedSelector(
            options = options,
            selectedIndex = pagerState.currentPage,
            indicatorPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
        )

        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            // Pages rarely match in height, so the dialog eases between them rather than jumping
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(tween(Defaults.ANIMATION_DURATION))
        ) { index ->
            page(index)
        }

        below()
    }
}

/**
 * The pill of [SegmentedTabs]. [indicatorPosition] is read while laying out, so the fill tracks
 * a swipe frame by frame without recomposing the segments.
 */
@Composable
private fun SegmentedSelector(
    options: List<SegmentedTab>,
    selectedIndex: Int,
    indicatorPosition: () -> Float,
    onSelect: (Int) -> Unit
) {
    val textColor = LocalDialogTextColor.current
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Defaults.PillShape,
        color = textColor.copy(alpha = 0.06f),
        border = BorderStroke(0.5.dp, textColor.copy(alpha = 0.2f))
    ) {
        BoxWithConstraints(modifier = Modifier.padding(SegmentInset)) {
            val segmentWidth = maxWidth / options.size
            Box(
                modifier = Modifier
                    .offset { IntOffset((segmentWidth.toPx() * indicatorPosition()).roundToInt(), 0) }
                    .size(segmentWidth, Defaults.PillHeightLarge)
                    // Filled and outlined as a selected AppFilterChip is, so both read as picked
                    .background(colors.primaryContainer, Defaults.PillShape)
                    .border(1.dp, colors.primary, Defaults.PillShape)
            )

            Row(modifier = Modifier.selectableGroup()) {
                options.forEachIndexed { index, option ->
                    val isSelected = index == selectedIndex
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) colors.onPrimaryContainer else textColor.copy(alpha = 0.6f),
                        animationSpec = tween(Defaults.ANIMATION_DURATION),
                        label = "segmentContent"
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(Defaults.PillHeightLarge)
                            .clip(Defaults.PillShape)
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = { onSelect(index) }
                            )
                            .padding(horizontal = Defaults.ContentPaddingSmall),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
