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

/** Controls outer padding and inset behavior of [AppDialog]. */
enum class DialogPadding {
    /** 32dp at the sides and 16dp above and below, with system bar insets. */
    Normal,
    /** 16dp all round, with system bar insets. */
    Compact
}

/**
 * Fullscreen dialog whose title, description, content and footer stand together as one centered
 * block. A dialog built around a list it scrolls through is a [ListDialog] instead.
 *
 * @param onDismissRequest Called when user dismisses the dialog.
 * @param title Optional title displayed at the top.
 * @param description What the dialog asks or explains, set under the [title] in one style for
 *   every dialog and held in place with it while the content below scrolls. Plain text or an
 *   [AnnotatedString] for one with emphasis.
 * @param titleTrailingContent Optional actions displayed after the title, laid out in a row.
 * @param footer Optional footer content, under the content.
 * @param accentColor Color of the app or source the dialog is about, see [DialogShell].
 * @param dismissOnClickOutside Whether clicking outside dismisses the dialog.
 * @param scrollable Whether to wrap content in verticalScroll and draw a [ListScrollbar] and [ScrollToTopButton] over it.
 * Set to false for content that scrolls on its own, such as a lazy list. Default is true.
 * @param padding Outer padding mode. Default is [DialogPadding.Normal].
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
    padding: DialogPadding = DialogPadding.Normal,
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
            padding = padding,
            content = content
        )
    }
}

/**
 * What every fullscreen dialog shares: a window of its own drawn over the screen in the theme's
 * background, the messages raised while it is open, its entrance and exit, and the text colors
 * and [accentColor] its content takes on.
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
                        LocalDialogTextColor provides textColor,
                        LocalDialogSecondaryTextColor provides textColor.copy(alpha = 0.7f),
                        LocalContentColor provides textColor
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            content()
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
 * system bars, or between the top and the keyboard while it is up.
 */
@Composable
private fun CompactDialogContent(
    title: String?,
    description: CharSequence?,
    titleTrailingContent: (@Composable RowScope.() -> Unit)?,
    footer: (@Composable () -> Unit)?,
    scrollable: Boolean,
    padding: DialogPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    // Horizontal inset is applied per-section below rather than to the whole, so the content stays
    // full width and its ListScrollbar can sit flush with the dialog edge
    val horizontalPadding = if (padding == DialogPadding.Compact) {
        Defaults.SpaceLarge
    } else {
        Defaults.SpaceExtraExtraLarge
    }
    // The system bars already set the dialog off the screen's edges, so above and below it keeps
    // only the gap its rows keep from each other, whatever it gives its sides. Compact mode zeroes
    // its top padding when there is no title to fill it, leaving the room to a header of its own
    val topPadding = if (padding == DialogPadding.Compact && title == null) 0.dp else Defaults.SpaceLarge

    CompositionLocalProvider(LocalDialogHorizontalInset provides horizontalPadding) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The keyboard is one more thing the dialog has to fit above, and its inset already
                // spans the navigation bar, so the two are taken together rather than stacked
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
                .padding(top = topPadding, bottom = Defaults.SpaceLarge)
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
                DialogTitle(
                    title = title,
                    description = description,
                    titleTrailingContent = titleTrailingContent,
                    horizontalPadding = horizontalPadding
                )

                // Content area, wrapped in a Box so ListScrollbar can anchor to the true dialog
                // edge while the scrollable column keeps its own horizontal inset. Scrolling is
                // also what carries a focused field up into the room the keyboard leaves above it
                val scrollState = if (scrollable) rememberScrollState() else null
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (scrollState != null) {
                                    // Ahead of the padding, so the fade spans the dialog's full
                                    // width and clips nothing that reaches into the padding
                                    Modifier
                                        .verticalScrollFade(scrollState)
                                        .padding(horizontal = horizontalPadding)
                                        .verticalScroll(scrollState)
                                } else {
                                    Modifier.padding(horizontal = horizontalPadding)
                                }
                            ),
                        content = content
                    )

                    if (scrollState != null) {
                        ListScrollbar(scrollState = scrollState)
                        ScrollToTopButton(scrollState = scrollState)
                    }
                }

                if (footer != null) {
                    Box(
                        modifier = Modifier
                            .keepsClearOfMessages()
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

/** The title and description an [AppDialog] opens with, each only where there is one. */
@Composable
private fun DialogTitle(
    title: String?,
    description: CharSequence?,
    titleTrailingContent: (@Composable RowScope.() -> Unit)?,
    horizontalPadding: Dp
) {
    val titleAlign = if (titleTrailingContent != null) TextAlign.Start else TextAlign.Center
    if (title != null) {
        // The headline's tall line box already adds space below the text, so a small gap reads as
        // much as the one above the footer
        val titleBottom = if (description != null) Defaults.SpaceExtraSmall else Defaults.SpaceSmall
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = horizontalPadding, end = horizontalPadding, bottom = titleBottom),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dialogs whose title tracks a state (download, install, result) swap it in step with
            // their content instead of snapping a new string in
            CrossfadeText(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = titleAlign,
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

    if (description != null) {
        Text(
            text = description as? AnnotatedString ?: AnnotatedString(description.toString()),
            style = MaterialTheme.typography.bodyLarge,
            color = dialogSecondaryTextColor(),
            textAlign = titleAlign,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = horizontalPadding, end = horizontalPadding, bottom = Defaults.SpaceLarge)
        )
    }
}
