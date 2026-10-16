/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Room at a [ListDialog]'s content ends for the bars floating over them. A scrolling list takes it
 * as padding, so its first and last rows can come out from under them.
 */
val LocalDialogBarInsets = compositionLocalOf { PaddingValues(0.dp) }

/**
 * Room a [ListDialog] keeps at the ends of its scrollbar: a step under the header, and the same
 * step above the screen's bottom edge, as the footer floats over the list rather than ending it.
 */
val LocalDialogScrollbarInsets = compositionLocalOf { PaddingValues(0.dp) }

/** What the header floating over a [ListDialog]'s content blurs, see [ListDialogHeader]. */
internal val LocalDialogHeaderHaze = staticCompositionLocalOf<HazeState?> { null }

/** Gap between a [ListDialog]'s content and the edges of the screen. */
private val ListDialogInset = Defaults.SpaceLarge

/**
 * Fullscreen dialog built around a list. The content runs to the screen's edges under the floating
 * [header] and [footer] or [bottomBar], leaving room for them through [LocalDialogBarInsets]. A
 * dialog whose content stands as one centered block is an [AppDialog].
 *
 * @param header Heads the list, such as a [ListDialogHeader], which blurs what scrolls under it
 *   where the platform can blur and folds its collapsible part away as the list scrolls on.
 * @param footer The dialog's buttons.
 * @param bottomBar Bar standing in for the [footer] while there is one, such as a
 *   [MultiSelectShell] while a selection is being made. It clears the navigation bar itself.
 * @param accentColor Color of the app or source the list is about, see [DialogShell].
 * @param dismissOnClickOutside Whether clicking outside the content dismisses the dialog.
 * @param hideFooterWhileTyping Folds the [footer] away while the keyboard is up, for a list whose
 *   search wants every row of room it can get. A dialog whose buttons act on what is typed leaves
 *   it off, so they stay above the keyboard.
 * @param overlay Drawn over the whole dialog, see [DialogShell].
 * @param onEntered Called once the dialog has finished coming in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListDialog(
    onDismissRequest: () -> Unit,
    header: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    accentColor: Color? = null,
    dismissOnClickOutside: Boolean = false,
    hideFooterWhileTyping: Boolean = false,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    onEntered: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    DialogShell(
        onDismissRequest = onDismissRequest,
        accentColor = accentColor,
        dismissOnClickOutside = dismissOnClickOutside,
        overlay = overlay,
        onEntered = onEntered
    ) {
        CompositionLocalProvider(LocalDialogHorizontalInset provides ListDialogInset) {
            ListDialogLayout(
                header = header,
                footer = footer,
                bottomBar = bottomBar,
                footerFolded = hideFooterWhileTyping && WindowInsets.isImeVisible,
                content = content
            )
        }
    }
}

/**
 * The content of a [ListDialog] under its floating bars, down to the keyboard while it is up. The
 * [bottomBar] and [footer] share one slot, so the content keeps its state as a selection bar comes
 * and goes.
 */
@Composable
private fun ListDialogLayout(
    header: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?,
    bottomBar: (@Composable () -> Unit)?,
    footerFolded: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    val haze = rememberHazeState()
    val collapse = remember { DialogHeaderCollapse() }
    FloatingBarsLayout(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
        topBar = {
            if (header != null) {
                Box(modifier = Modifier.holdsUnfoldedHeight(collapse)) {
                    CompositionLocalProvider(
                        LocalDialogHeaderHaze provides haze,
                        LocalDialogHeaderCollapse provides collapse,
                        content = header
                    )
                }
            }
        },
        bottomBar = {
            when {
                bottomBar != null -> Box(
                    modifier = Modifier
                        .keepsClearOfMessages()
                        .widthIn(max = Defaults.ContentMaxWidth)
                        .fillMaxWidth()
                ) {
                    bottomBar()
                }

                footer != null -> AnimatedVisibility(
                    visible = !footerFolded,
                    enter = Animations.expandFadeEnter,
                    exit = Animations.shrinkFadeExit
                ) {
                    Box(
                        modifier = Modifier
                            .keepsClearOfMessages()
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = Defaults.ContentMaxWidth)
                                .fillMaxWidth()
                                .padding(horizontal = ListDialogInset)
                                // Dims only as wide as the rows, so the scrollbar in the margin stays clear
                                .floatingBarScrim(background)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                                .padding(vertical = Defaults.SpaceLarge)
                        ) {
                            CompositionLocalProvider(LocalDialogButtonBackdrop provides background, content = footer)
                        }
                    }
                }

                // Nothing floats over the content, which still ends clear of the navigation bar
                else -> Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.systemBars))
            }
        }
    ) { headerHeight, bottomHeight ->
        // The list starts a step below the header, as a dialog's content starts below its top
        val topInset = if (header != null) headerHeight + Defaults.SpaceMedium else 0.dp
        // Only a bottom bar hides what scrolls under it, so the scrollbar runs on past a footer
        val scrollbarBottom = if (bottomBar != null) {
            bottomHeight
        } else {
            WindowInsets.systemBars.exclude(WindowInsets.ime).asPaddingValues().calculateBottomPadding() + Defaults.SpaceMedium
        }
        CompositionLocalProvider(
            LocalDialogBarInsets provides PaddingValues(top = topInset, bottom = bottomHeight),
            LocalDialogScrollbarInsets provides PaddingValues(
                top = if (header != null) topInset else Defaults.SpaceMedium,
                bottom = scrollbarBottom
            ),
            LocalDialogHeaderCollapse provides collapse.takeIf { header != null }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // What the header blurs, laid from the screen's top edge when it clears the bar
                    .hazeSource(haze)
                    // The list's scroll folds the header's collapsible part away and back
                    .drivesHeaderCollapse(collapse)
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(
                            if (header != null) WindowInsetsSides.Horizontal
                            else WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                    .consumesTaps(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = Defaults.ContentMaxWidth)
                        .fillMaxSize()
                        .padding(horizontal = ListDialogInset),
                    content = content
                )
            }
        }
    }
}
