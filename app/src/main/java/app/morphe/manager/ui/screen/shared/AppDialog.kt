/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.morphe.manager.ui.theme.isDarkTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** The primary text color of dialog content, read through [dialogTextColor]. */
val LocalDialogTextColor = compositionLocalOf { Color.Unspecified }

/** The secondary/hint text color of dialog content, read through [dialogSecondaryTextColor]. */
val LocalDialogSecondaryTextColor = compositionLocalOf { Color.Unspecified }

/**
 * Text color of the dialog or sheet this is drawn in, or of the theme's surfaces anywhere else,
 * since the components dialogs use are shared with plain screens.
 */
@Composable
@ReadOnlyComposable
fun dialogTextColor(): Color =
    LocalDialogTextColor.current.takeOrElse { MaterialTheme.colorScheme.onSurface }

/** Secondary counterpart of [dialogTextColor], dimmed the way a dialog dims its own. */
@Composable
@ReadOnlyComposable
fun dialogSecondaryTextColor(): Color =
    LocalDialogSecondaryTextColor.current.takeOrElse { MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) }

/** Horizontal inset of the dialog's content, for offsetting a caller-managed [ListScrollbar] out to the true dialog edge. */
val LocalDialogHorizontalInset = compositionLocalOf { 0.dp }

/**
 * Counts the [AppDialog]s and [ListDialog]s currently on screen. Each one paints an opaque
 * full-screen background, so whatever the activity draws behind it is invisible and free to stand
 * still until it closes.
 */
object FullscreenDialogs {
    private val openCount = mutableIntStateOf(0)

    val anyOpen: Boolean
        get() = openCount.intValue > 0

    internal fun onOpened() {
        openCount.intValue++
    }

    internal fun onClosed() {
        openCount.intValue--
    }
}

/**
 * Fullscreen dialog whose title, description, content and footer stand together as one centered
 * block. A dialog built around a list it scrolls through is a [ListDialog] instead.
 *
 * @param onDismissRequest Called when user dismisses the dialog.
 * @param title Optional title displayed at the top.
 * @param description What the dialog asks or explains, leading its content in one style for every
 *   dialog. Plain text or an [AnnotatedString] for one with emphasis.
 * @param titleTrailingContent Optional actions displayed after the title, laid out in a row.
 * @param footer Optional footer content, under the content.
 * @param accentColor Color of the app or source the dialog is about, see [DialogShell].
 * @param dismissOnClickOutside Whether clicking outside dismisses the dialog.
 * @param scrollable Whether to wrap content in verticalScroll and draw a [ListScrollbar] and [ScrollToTopButton] over it.
 * Set to false for content that scrolls on its own, such as a lazy list. Default is true.
 * @param backdrop Drawn over the dialog's own background and under its content, for a dialog
 * that previews something full screen, such as the background picker.
 * @param content Dialog content, left out by a dialog its [description] says everything for.
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    description: CharSequence? = null,
    titleTrailingContent: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    accentColor: Color? = null,
    dismissOnClickOutside: Boolean = false,
    scrollable: Boolean = true,
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    DialogShell(
        onDismissRequest = onDismissRequest,
        accentColor = accentColor,
        dismissOnClickOutside = dismissOnClickOutside,
        backdrop = backdrop
    ) {
        CompactDialogContent(
            title = title,
            description = description,
            titleTrailingContent = titleTrailingContent,
            footer = footer,
            scrollable = scrollable,
            content = content
        )
    }
}

/**
 * What every fullscreen dialog shares: its own window in the theme's background, a host for
 * messages, the entrance and exit, and the text colors and [accentColor] of its content.
 *
 * @param accentColor Color of the app or source the dialog is about. Its header, content and
 *   buttons all take it on through [LocalAccent], so the dialog reads as one surface in that color.
 *   Null leaves them in the theme's palette, or in whatever color surrounds the dialog.
 * @param overlay Drawn over the whole dialog, header, footer and system bars included, such as a
 *   [ContentOverlay] covering it while it is busy.
 * @param onEntered Called once the dialog has finished coming in.
 */
@Composable
internal fun DialogShell(
    onDismissRequest: () -> Unit,
    accentColor: Color?,
    dismissOnClickOutside: Boolean,
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    onEntered: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isDarkTheme = isDarkTheme()
    var visible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        FullscreenDialogs.onOpened()

        onDispose {
            FullscreenDialogs.onClosed()
        }
    }

    LaunchedEffect(Unit) {
        visible = true
        // Notify caller once the enter animation has completed
        if (onEntered != null) {
            delay(Defaults.ANIMATION_DURATION.toLong().milliseconds)
            onEntered()
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        DialogWindowEffect(isDarkTheme)

        // A window of its own, so messages raised while it is open are shown in it
        MessageHost(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(
                    if (dismissOnClickOutside) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { onDismissRequest() }
                        }
                    } else Modifier
                )
        ) {
            backdrop?.invoke(this)

            AnimatedVisibility(
                visible = visible,
                enter = Animations.dialogEnter,
                exit = Animations.dialogExit,
                modifier = Modifier.fillMaxSize()
            ) {
                val textColor = if (isDarkTheme) Color.White else Color.Black
                val dialogContent = @Composable {
                    CompositionLocalProvider(
                        // A window of its own, which cannot draw what the one it opened from blurs
                        LocalBackdropHaze provides null,
                        LocalSettingsSearch provides null,
                        LocalSegmentRegistry provides null,
                        LocalDialogTextColor provides textColor,
                        LocalDialogSecondaryTextColor provides textColor.copy(alpha = 0.7f),
                        LocalContentColor provides textColor
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Over the page rather than in its list, so the list's insets stay behind
                            WithoutBringIntoViewInsets(content)
                            overlay?.invoke(this)
                        }
                    }
                }
                // Provided only when there is one, so a dialog opened from a colored one keeps its color
                if (accentColor != null) ProvideAccent(accentColor, dialogContent) else dialogContent()
            }
        }
    }
}

/** Takes in taps on a dialog's content, so they do not reach a tap outside that dismisses it. */
internal fun Modifier.consumesTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/** Whether this is drawn in a dialog's own window rather than in the activity's under it. */
@Composable
fun isInDialogWindow(): Boolean = LocalView.current.parent is DialogWindowProvider

/**
 * Strips a dialog window down to its content and points the system bars at [isDarkTheme].
 *
 * A dialog gets a window of its own, with an insets controller of its own, so what the activity
 * set in its theme never reaches it. Left alone, the bar icons keep the platform default and go
 * invisible against a light dialog.
 */
@Composable
private fun DialogWindowEffect(isDarkTheme: Boolean) {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window

    SideEffect {
        dialogWindow?.let {
            // Remove standard system backgrounds/window shadows
            it.setDimAmount(0f)
            it.setBackgroundDrawableResource(android.R.color.transparent)

            val insets = WindowCompat.getInsetsController(it, it.decorView)
            insets.isAppearanceLightStatusBars = !isDarkTheme
            insets.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}

/**
 * Fullscreen semi-transparent overlay dialog. Blocks all interaction behind it.
 * Handles its own fade enter/exit animation via [Animations].
 */
@Composable
fun Overlay(
    visible: Boolean,
    backgroundAlpha: Float = 0.75f,
    content: @Composable BoxScope.() -> Unit
) {
    // Drawn over the theme background, so the bars read against the same thing an AppDialog gives
    // them even though this one lets some of the screen behind show through
    val isDarkTheme = isDarkTheme()

    AnimatedVisibility(
        visible = visible,
        enter = Animations.overlayEnter,
        exit = Animations.overlayExit
    ) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false
            )
        ) {
            DialogWindowEffect(isDarkTheme)

            MessageHost(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = backgroundAlpha))
                        .pointerInput(Unit) { detectTapGestures { } },
                    contentAlignment = Alignment.Center,
                    content = content
                )
            }
        }
    }
}

/**
 * Semi-transparent overlay within a [Box] parent. Blocks all interaction and fades in/out.
 * Must be called inside a [BoxScope] (e.g. as the last child of a Box).
 */
@Composable
fun BoxScope.ContentOverlay(
    visible: Boolean,
    backgroundAlpha: Float = 0.8f,
    content: @Composable BoxScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.matchParentSize(),
        enter = Animations.overlayEnter,
        exit = Animations.overlayExit
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * The title, description, content and footer of an [AppDialog], centered together between the
 * system bars, or between the top and the keyboard while it is up. The title floats over the top of
 * the content, so content too long for the screen scrolls on under it, as a list dialog's does.
 */
@Composable
private fun CompactDialogContent(
    title: String?,
    description: CharSequence?,
    titleTrailingContent: (@Composable RowScope.() -> Unit)?,
    footer: (@Composable () -> Unit)?,
    scrollable: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    // Horizontal inset is applied per-section below rather than to the whole, so the content stays
    // full width and its ListScrollbar can sit flush with the dialog edge
    val horizontalPadding = DialogInset
    val haze = rememberHazeState()
    // Scrolling is also what carries a focused field up into the room the keyboard leaves above it
    val scrollState = if (scrollable) rememberScrollState() else null

    CompositionLocalProvider(LocalDialogHorizontalInset provides horizontalPadding) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The keyboard is one more thing the dialog has to fit above, and its inset already
                // spans the navigation bar, so the two are taken together rather than stacked
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
                .padding(vertical = Defaults.SpaceLarge)
                .consumesTaps(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Defaults.ContentMaxWidth)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FloatingBarsLayout(
                    modifier = Modifier.weight(1f, fill = false),
                    wrapContentHeight = true,
                    topBar = {
                        if (title != null) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                // Bare while the dialog fits, the band laid down once its content
                                // scrolls on under the title
                                HeaderBand(
                                    glass = appAccentFill(LocalAccent.current),
                                    shape = HeaderBandShape,
                                    scrolledUnder = { scrollState?.canScrollBackward == true },
                                    haze = haze
                                )
                                DialogTitle(
                                    title = title,
                                    titleTrailingContent = titleTrailingContent,
                                    horizontalPadding = horizontalPadding
                                )
                            }
                        }
                    }
                ) { titleHeight, _ ->
                    val inset = Modifier.padding(start = horizontalPadding, top = titleHeight, end = horizontalPadding)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // What the title blurs as the content scrolls on under it
                            .hazeSource(haze)
                            .then(
                                if (scrollState != null) {
                                    // Ahead of the padding, so the fade spans the dialog's full
                                    // width and clips nothing that reaches into the padding
                                    Modifier
                                        .verticalScrollFade(scrollState)
                                        .verticalScroll(scrollState)
                                        .then(inset)
                                } else {
                                    inset
                                }
                            )
                    ) {
                        if (description != null) DialogDescription(description, centered = titleTrailingContent == null)
                        content()
                    }

                    if (scrollState != null) {
                        ListScrollbar(scrollState = scrollState, modifier = Modifier.padding(top = titleHeight))
                        ScrollToTopButton(scrollState = scrollState)
                    }
                }

                if (footer != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = horizontalPadding, end = horizontalPadding, top = Defaults.SpaceLarge)
                    ) {
                        footer()
                    }
                }
            }
        }
    }
}

/** Gap between a dialog's content and the edges of the screen, an [AppDialog]'s and a [ListDialog]'s alike. */
internal val DialogInset = Defaults.SpaceLarge

/** The title an [AppDialog] opens with, and the actions beside it where it has any. */
@Composable
private fun DialogTitle(
    title: String,
    titleTrailingContent: (@Composable RowScope.() -> Unit)?,
    horizontalPadding: Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, bottom = Defaults.SpaceSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dialogs whose title tracks a state (download, install, result) swap it in step with
        // their content instead of snapping a new string in
        CrossfadeText(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = if (titleTrailingContent != null) TextAlign.Start else TextAlign.Center,
            color = dialogTextColor(),
            modifier = Modifier.weight(1f),
            label = "dialogTitle"
        )
        if (titleTrailingContent != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
                verticalAlignment = Alignment.CenterVertically,
                content = titleTrailingContent
            )
        }
    }
}

/** What an [AppDialog] asks or explains, leading its content in one style for every dialog. */
@Composable
private fun DialogDescription(description: CharSequence, centered: Boolean) {
    Text(
        text = description as? AnnotatedString ?: AnnotatedString(description.toString()),
        style = MaterialTheme.typography.bodyLarge,
        color = dialogSecondaryTextColor(),
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Defaults.SpaceLarge)
    )
}
