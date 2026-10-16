/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.morphe.manager.R
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import app.morphe.manager.ui.theme.headerTitle

/** How a dialog heads itself, shared by the list dialogs and the app details so they read alike. */
object DialogHeaderDefaults {
    val IconSize = 48.dp

    /** Rounding that keeps an app icon's own shape at [IconSize]. */
    val IconCornerRadius = 14.dp
    val IconSpacing = Defaults.SpaceMedium
    val TextSpacing = 2.dp
    val VerticalPadding = Defaults.SpaceMedium

    val titleStyle: TextStyle
        @Composable get() = MaterialTheme.typography.headerTitle
    val subtitleStyle: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium
}

/**
 * Entrance a dialog header plays as it first shows: the icon springs in, the title and subtitle
 * slide in after it and the badges rise last. The header modifiers below read these clocks while
 * drawing, so the entrance never recomposes the header.
 */
@Stable
class DialogHeaderEntrance internal constructor(
    internal val icon: State<Float>,
    internal val text: State<Float>,
    internal val badges: State<Float>
)

@Composable
fun rememberDialogHeaderEntrance(): DialogHeaderEntrance {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val target = if (entered) 1f else 0f

    val icon = animateFloatAsState(
        targetValue = target,
        // Overshoots, as the first thing the eye lands on
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
        label = "header_icon_entrance"
    )
    val text = animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 260, delayMillis = 60, easing = EaseOutCubic),
        label = "header_text_entrance"
    )
    val badges = animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 240, delayMillis = 160, easing = EaseOutBack),
        label = "header_badges_entrance"
    )
    return remember(icon, text, badges) { DialogHeaderEntrance(icon, text, badges) }
}

fun Modifier.headerIconEntrance(entrance: DialogHeaderEntrance): Modifier = graphicsLayer {
    val progress = entrance.icon.value
    val scale = lerp(0.6f, 1f, progress)
    scaleX = scale
    scaleY = scale
    alpha = progress.coerceIn(0f, 1f)
}

fun Modifier.headerTitleEntrance(entrance: DialogHeaderEntrance): Modifier = graphicsLayer {
    slideIn(entrance.text.value)
}

/** Trails the title a little on the same clock. */
fun Modifier.headerSubtitleEntrance(entrance: DialogHeaderEntrance): Modifier = graphicsLayer {
    slideIn(entrance.text.value.startingAt(0.15f))
}

/**
 * For the badges and whatever else sits beside the title. Each of a row of badges starts a third
 * of the way into the one before it, by its [index], so they arrive in sequence.
 */
fun Modifier.headerBadgeEntrance(entrance: DialogHeaderEntrance, index: Int = 0): Modifier = graphicsLayer {
    val progress = entrance.badges.value.startingAt(index * 0.3f)
    translationY = lerp(20f, 0f, progress)
    alpha = progress
}

private fun GraphicsLayerScope.slideIn(progress: Float) {
    translationX = lerp(40f, 0f, progress)
    alpha = progress.coerceIn(0f, 1f)
}

/** This progress rescaled to run from [start] on, so a later element enters off the same clock. */
private fun Float.startingAt(start: Float): Float = ((this - start) / (1f - start)).coerceIn(0f, 1f)

/**
 * Head of a list dialog that names what the list belongs to: its [icon], [title] and a
 * [subtitle] summing the list up, with the list's search field and its toggle where it has one.
 *
 * The header sits on a band like the app details' one. As a [ListDialog]'s header it floats over
 * the list, the band reaching the screen's edges and blurring the rows scrolling under it, and solid
 * where the platform cannot blur. Anywhere else the band sits in line, giving the list an edge to
 * stop at.
 *
 * @param subtitleLoading Shows a shimmer in place of the [subtitle] while the list it sums uploads,
 *   easing into the text once it is there.
 * @param search The list's search field, or null for a list short enough to go without one.
 * @param searchLabel What the field searches, as its label and the toggle's description.
 * @param searchEnabled Whether there is anything to search yet. The toggle stays in place while
 *   there is not, so the title does not reflow when a loading list fills in.
 *
 * @param accentColor Color of the app the list belongs to, which tints the band and the title
 *   actions and badges on it, or null for a neutral band and actions in the theme's palette. The
 *   dialog's own by default, see [ListDialog].
 * @param badges What the whole list shares, in a row under the title as the app details keep theirs,
 *   folding away with the [collapsible] part.
 * @param content More of the list's head, under the title, that stays in place however far the
 *   list scrolls, such as where a file picker is in the folder tree or the tabs between sources.
 * @param collapsible What heads the list but is wanted only now and then, such as actions over
 *   the whole of it. Under the [content], it folds away under the rest of the header together with
 *   the [badges] as the list scrolls on, and comes back as the list scrolls back, see
 *   [DialogHeaderCollapse]. Held in place where the header does not float over a list.
 * @param actions Title actions drawn ahead of the search toggle.
 */
@Composable
fun ListDialogHeader(
    icon: @Composable (Modifier) -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    subtitleLoading: Boolean = false,
    search: SearchFieldState? = null,
    searchLabel: String? = null,
    searchEnabled: Boolean = true,
    accentColor: Color? = LocalAccent.current,
    badges: (@Composable FlowRowScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    collapsible: (@Composable ColumnScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    // An accent read from a picture can land after the header is up, so the band eases into it
    val band by animateColorAsState(
        targetValue = appAccentFill(accentColor),
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "list_header_band"
    )
    val inset = LocalDialogHorizontalInset.current
    val haze = LocalBackdropHaze.current
    val head = @Composable { headModifier: Modifier ->
        HeaderContent(
            icon = icon,
            title = title,
            subtitle = subtitle,
            subtitleLoading = subtitleLoading,
            search = search,
            searchLabel = searchLabel,
            searchEnabled = searchEnabled,
            accentColor = accentColor,
            badges = badges,
            actions = actions,
            content = content,
            collapsible = collapsible,
            modifier = headModifier
        )
    }

    if (haze != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .glassFill(band, BandShape, haze)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.TopCenter
        ) {
            head(
                Modifier
                    .widthIn(max = Defaults.ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = inset)
            )
        }
    } else {
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        head(modifier.fillMaxWidth().headerBand(band, inset, statusBarHeight))
    }
}

/** What a [ListDialogHeader] holds, whichever way its band is drawn. */
@Composable
private fun HeaderContent(
    icon: @Composable (Modifier) -> Unit,
    title: String,
    subtitle: String,
    subtitleLoading: Boolean,
    search: SearchFieldState?,
    searchLabel: String?,
    searchEnabled: Boolean,
    accentColor: Color?,
    badges: (@Composable FlowRowScope.() -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    content: (@Composable ColumnScope.() -> Unit)?,
    collapsible: (@Composable ColumnScope.() -> Unit)?,
    modifier: Modifier
) {
    val entrance = rememberDialogHeaderEntrance()
    val label = searchLabel ?: stringResource(R.string.search)

    Column(modifier = modifier.padding(vertical = DialogHeaderDefaults.VerticalPadding)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DialogHeaderDefaults.IconSpacing)
        ) {
            icon(Modifier.size(DialogHeaderDefaults.IconSize).headerIconEntrance(entrance))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DialogHeaderDefaults.TextSpacing)
            ) {
                // Two lines, so a list named in a phrase rather than a name keeps it whole beside
                // the header's actions
                Text(
                    text = title,
                    style = DialogHeaderDefaults.titleStyle,
                    color = dialogTextColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.headerTitleEntrance(entrance)
                )
                val subtitleStyle = DialogHeaderDefaults.subtitleStyle
                // Eases from the shimmer into the text and from one text to the next as the list it
                // sums up changes, the header resizing along when the new one takes more lines
                AnimatedContent(
                    targetState = subtitle.takeUnless { subtitleLoading },
                    transitionSpec = Animations.fadeCrossfade(),
                    contentAlignment = Alignment.TopStart,
                    label = "list_header_subtitle",
                    modifier = Modifier.headerSubtitleEntrance(entrance)
                ) { text ->
                    if (text == null) {
                        // A line's height, the size of the shortest subtitle it stands in for
                        ShimmerText(
                            widthFraction = 0.5f,
                            height = with(LocalDensity.current) { subtitleStyle.lineHeight.toDp() }
                        )
                    } else {
                        Text(
                            text = text,
                            style = subtitleStyle,
                            color = dialogSecondaryTextColor()
                        )
                    }
                }
            }
            // Only a header with controls lays them out, so one without leaves the title no gap
            if (actions != null || search != null) {
                ProvideAccent(accentColor) {
                    Row(
                        modifier = Modifier.headerBadgeEntrance(entrance),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DialogHeaderDefaults.IconSpacing)
                    ) {
                        actions?.invoke(this)
                        if (search != null) {
                            TitleAction(
                                icon = if (search.visible) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                                contentDescription = label,
                                onClick = { search.toggle() },
                                style = TitleActionStyle.Toggle,
                                active = search.visible,
                                enabled = searchEnabled
                            )
                        }
                    }
                }
            }
        }
        content?.invoke(this)
        if (badges != null || collapsible != null) {
            val collapse = LocalDialogHeaderCollapse.current
            Column(modifier = if (collapse != null) Modifier.foldsWith(collapse) else Modifier) {
                if (badges != null) {
                    // A row that folds its chips out resizes on the list's own spring, as the list's
                    // rows do, without the settle, which would dip below the chips and clip them
                    ProvideAccent(accentColor) {
                        StatusBadgeRow(
                            modifier = Modifier
                                .padding(top = Defaults.SpaceSmall)
                                .animateContentSize(Animations.listSpring(dampingRatio = Spring.DampingRatioNoBouncy))
                                .headerBadgeEntrance(entrance),
                            content = badges
                        )
                    }
                }
                if (collapsible != null) {
                    Column(modifier = Modifier.padding(top = Defaults.SpaceSmall), content = collapsible)
                }
            }
        }
        if (search != null) {
            AnimatedVisibility(
                visible = search.visible,
                enter = Animations.expandFadeEnter,
                exit = Animations.shrinkFadeExit
            ) {
                ListSearchField(
                    value = search.query,
                    onValueChange = { search.query = it },
                    label = label,
                    requestFocus = true,
                    modifier = Modifier.padding(top = Defaults.SpaceMedium)
                )
            }
        }
    }
}

/**
 * [ListDialogHeader] icon for a list that stands for no app or source of its own: [icon] on a disc
 * of [color], the color the header takes on too.
 */
@Composable
fun ListDialogHeaderIcon(icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    val accent = ThemeTraitsDefaults.accentColor(color)
    StatusCircleIcon(
        icon = icon,
        containerColor = accent.copy(alpha = 0.2f),
        contentColor = accent,
        modifier = modifier,
        size = DialogHeaderDefaults.IconSize
    )
}

/** The band's outline, rounded off below like the app details' header. */
private val BandShape = RoundedCornerShape(bottomStart = Defaults.CornerLarge, bottomEnd = Defaults.CornerLarge)

/**
 * Tints the header out to the dialog's edges and up under the status bar, rounded off below like
 * the app details' header. Only the drawing reaches past the header, so its content stays in line
 * with the list under it.
 */
private fun Modifier.headerBand(color: Color, bleed: Dp, statusBarHeight: Dp): Modifier = drawBehind {
    val corner = CornerRadius(Defaults.CornerLarge.toPx())
    val band = RoundRect(
        left = -bleed.toPx(),
        top = -statusBarHeight.toPx(),
        right = size.width + bleed.toPx(),
        bottom = size.height,
        bottomLeftCornerRadius = corner,
        bottomRightCornerRadius = corner
    )
    drawPath(Path().apply { addRoundRect(band) }, color)
}
