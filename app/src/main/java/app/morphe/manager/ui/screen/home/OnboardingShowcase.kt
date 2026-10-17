/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.settings.SettingsTourTarget
import app.morphe.manager.ui.screen.shared.ActionPillButton
import app.morphe.manager.ui.screen.shared.ActionPillColors
import app.morphe.manager.ui.screen.shared.Animations
import app.morphe.manager.ui.screen.shared.CardBorder
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.MainPage
import app.morphe.manager.ui.screen.shared.PanelHeader
import app.morphe.manager.ui.screen.shared.PanelSubtitle
import app.morphe.manager.ui.screen.shared.PanelTitle
import app.morphe.manager.ui.screen.shared.TitleAction
import app.morphe.manager.ui.screen.shared.TitleActionStyle
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import kotlin.math.roundToInt

/** One coach-mark step: resource IDs for text, a lambda that returns the current target bounds,
 *  and an optional callback fired when this step becomes active (e.g. to navigate a pager). */
data class StepDef(
    val titleRes: Int,
    val descRes: Int,
    val getBounds: () -> Rect?,
    val onShow: (() -> Unit)? = null
)

/**
 * Where the tour's spotlight targets sit in the window, and the hooks it moves the main screen
 * with. Handed to the screen only while the tour runs.
 */
class OnboardingState {
    var firstAppCardBounds by mutableStateOf<Rect?>(null)
    var swipeActive by mutableStateOf(false)
    val navigationBounds = mutableStateMapOf<MainPage, Rect>()
    var sourcesPatchesBounds by mutableStateOf<Rect?>(null)
    var sourcesVersionBounds by mutableStateOf<Rect?>(null)
    var sourcesPrereleaseBounds by mutableStateOf<Rect?>(null)
    val settingsTargetBounds = mutableStateMapOf<SettingsTourTarget, Rect>()
    /** The settings row the current step is about, which the settings page scrolls to. */
    var settingsTarget by mutableStateOf<SettingsTourTarget?>(null)
    /** The page the current step is on, which the main screen turns to. */
    var page by mutableStateOf<MainPage?>(null)
    var onScrollToFirstSource: (() -> Unit)? = null
    var onScrollToPrerelease: (() -> Unit)? = null
}

/**
 * Generic full-screen coach-marks overlay.
 * Accepts any list of [StepDef] so it can be reused across different screens and sheets.
 */
@Composable
fun OnboardingShowcase(
    steps: List<StepDef>,
    onComplete: () -> Unit,
    onSkip: () -> Unit = onComplete
) {
    if (steps.isEmpty()) return
    var step by remember { mutableIntStateOf(0) }

    BackHandler {
        if (step > 0) step -= 1 else onSkip()
    }
    val stepDef = steps[step]
    val bounds = stepDef.getBounds()

    LaunchedEffect(step) { steps[step].onShow?.invoke() }

    val pulse = rememberInfiniteTransition(label = "obs_pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "obs_scale"
    )

    // Track the overlay's own position in the window so we can convert window-space
    // bounds (from boundsInWindow) to canvas-local coordinates. This is needed when
    // the overlay does not start at the window origin, e.g. inside a Dialog window.
    var selfOffset by remember { mutableStateOf(Offset.Zero) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(visible = visible, enter = Animations.overlayEnter) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val wb = coords.boundsInWindow()
                    selfOffset = Offset(wb.left, wb.top)
                }
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
        ) {
            val density = LocalDensity.current
            val screenW = constraints.maxWidth
            val screenH = constraints.maxHeight
            // Card shows above the target when the target is in the lower half of the screen
            val isBottomHalf = bounds != null && (bounds.center.y - selfOffset.y) > screenH * 0.5f

            val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

            var cardSize by remember { mutableStateOf(IntSize.Zero) }

            val gap = with(density) { 16.dp.roundToPx() }
            val hPad = with(density) { 24.dp.roundToPx() }
            val navPx = with(density) { navBarPadding.roundToPx() }
            val statusPx = with(density) { statusBarPadding.roundToPx() }
            // Cap card width so it always fits within horizontal padding on any screen width
            val maxCardWidth = (screenW - 2 * hPad).coerceAtMost(with(density) { 380.dp.roundToPx() })
            val cw = cardSize.width.takeIf { it > 0 } ?: maxCardWidth
            val ch = cardSize.height.takeIf { it > 0 } ?: with(density) { 160.dp.roundToPx() }

            val targetX = if (bounds != null) {
                ((bounds.center.x - selfOffset.x) - cw / 2f).roundToInt()
                    .coerceIn(hPad, (screenW - cw - hPad).coerceAtLeast(hPad))
            } else (screenW - cw) / 2

            val targetY = if (bounds != null) {
                if (isBottomHalf)
                    // Show card just above the spotlight target
                    (bounds.top - selfOffset.y - ch - gap).roundToInt()
                        .coerceAtLeast(statusPx + gap)
                else
                    // Show card just below the spotlight target
                    (bounds.bottom - selfOffset.y + gap).roundToInt()
                        .coerceAtMost(screenH - ch - navPx - gap)
            } else ((screenH - ch) / 2).coerceAtLeast(statusPx)

            val cardOffsetX by animateIntAsState(
                targetValue = targetX,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "obs_card_x"
            )
            val cardOffsetY by animateIntAsState(
                targetValue = targetY,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "obs_card_y"
            )

            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                drawRect(Color.Black.copy(alpha = 0.72f))

                if (bounds != null) {
                    val pad = 12.dp.toPx()
                    val cx = bounds.center.x - selfOffset.x
                    val cy = bounds.center.y - selfOffset.y
                    val hw = (bounds.width / 2 + pad) * pulseScale
                    val hh = (bounds.height / 2 + pad) * pulseScale
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(cx - hw, cy - hh),
                        size = Size(hw * 2, hh * 2),
                        cornerRadius = CornerRadius(Defaults.CornerLargeIncreased.toPx()),
                        blendMode = BlendMode.Clear
                    )
                }
            }

            OnboardingCard(
                title = stringResource(stepDef.titleRes),
                description = stringResource(stepDef.descRes),
                step = step + 1,
                totalSteps = steps.size,
                onNext = { if (step < steps.size - 1) step++ else onComplete() },
                onPrevious = if (step > 0) { { step -= 1 } } else null,
                onSkip = onSkip,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(cardOffsetX, cardOffsetY) }
                    .widthIn(max = with(density) { maxCardWidth.toDp() })
                    .onGloballyPositioned { cardSize = it.size }
            )
        }
    }
}

@Composable
private fun OnboardingCard(
    title: String,
    description: String,
    step: Int,
    totalSteps: Int,
    onNext: () -> Unit,
    onPrevious: (() -> Unit)?,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Drawn and headed like a selection panel, the other surface that floats over the home screen
    Surface(
        modifier = modifier.widthIn(max = 380.dp),
        shape = RoundedCornerShape(Defaults.CornerLargeIncreased),
        color = ThemeTraitsDefaults.surfaceColor(MaterialTheme.colorScheme.background),
        shadowElevation = 8.dp,
        border = CardBorder.neutral
    ) {
        Column(modifier = Modifier.padding(bottom = Defaults.SpaceLarge)) {
            PanelHeader(
                title = {
                    Column {
                        PanelTitle(text = title)
                        PanelSubtitle(text = stringResource(R.string.onboarding_step_of, step, totalSteps))
                    }
                }
            ) {
                TitleAction(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.skip),
                    onClick = onSkip,
                    style = TitleActionStyle.Neutral
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Defaults.SpaceLarge)
            )
            Spacer(Modifier.height(Defaults.SpaceLarge))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Defaults.SpaceLarge),
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
            ) {
                if (onPrevious != null) {
                    val back = stringResource(R.string.back)
                    ActionPillButton(
                        onClick = onPrevious,
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = back,
                        label = back,
                        large = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                val isLast = step == totalSteps
                val next = stringResource(if (isLast) R.string.done else R.string.next)
                ActionPillButton(
                    onClick = onNext,
                    icon = if (isLast) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = next,
                    label = next,
                    large = true,
                    colors = ActionPillColors.primary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
