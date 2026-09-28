/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.PatchSourceRef
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_BUILD
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_DEVICE
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_SOURCE
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstallViewModel.InstallState
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import app.morphe.manager.util.contrastingContent

/**
 * Snapshot of patched-app metadata shown in the error dialog.
 */
data class PatcherErrorInfo(
    val appName: String,
    val packageName: String,
    val appVersion: String,
    val patchCount: Int,
    val bundles: List<PatchSourceRef>,
    /** Null where the setting the run used is no longer known, as in a batch run. */
    val stripsNativeLibs: Boolean?
)

/**
 * Log lines the error dialog already spells out field by field in its diagnostics card, dropped
 * from the log it falls back to so the same values are not read twice.
 */
private val SummarisedLogPrefixes = listOf(
    LOG_WORKER_PREFIX_BUILD,
    LOG_WORKER_PREFIX_DEVICE,
    LOG_WORKER_PREFIX_SOURCE
)

/** Enum for patcher states. */
enum class PatcherState {
    IN_PROGRESS,
    SUCCESS,
    FAILED
}

/**
 * State holder for Patcher Screen.
 * Manages patching progress, dialogs, and installation flow.
 */
@Stable
class PatcherScreenState(
    val viewModel: PatcherViewModel
) {
    // Error handling
    var showErrorDialog by mutableStateOf(false)
    var errorMessage by mutableStateOf("")
    var errorInfo by mutableStateOf<PatcherErrorInfo?>(null)
    var hasPatchingError by mutableStateOf(false)

    /**
     * The message shown in the error dialog. If [errorMessage] is blank or generic, falls back
     * to the patching log so the user always sees actionable information.
     */
    val effectiveErrorMessage: String
        get() {
            if (errorMessage.isNotBlank()) return errorMessage
            val logText = viewModel.patchRun.logs
                .filterNot { (_, message) -> SummarisedLogPrefixes.any { message.startsWith(it) } }
                .joinToString("\n") { (level, msg) -> "[$level] $msg" }
            return logText.ifBlank { errorMessage }
        }

    // Cancel dialog
    var showCancelDialog by mutableStateOf(false)

    // Computed states
    val patcherSucceeded: Boolean?
        get() = viewModel.patcherSucceeded.value

    val currentPatcherState: PatcherState
        get() = when (patcherSucceeded) {
            null -> PatcherState.IN_PROGRESS
            true -> PatcherState.SUCCESS
            else -> PatcherState.FAILED
        }
}

/**
 * Remember patcher state with proper lifecycle.
 */
@Composable
fun rememberPatcherScreenState(
    viewModel: PatcherViewModel
): PatcherScreenState {
    return remember(viewModel) {
        PatcherScreenState(viewModel)
    }
}

/** Error and conflict are the two install states the success screen paints as a failure. */
private val InstallState.failed get() = this is InstallState.Error || this is InstallState.Conflict

/**
 * Patching success screen.
 */
@Composable
fun PatchingSuccess(
    packageName: String,
    version: String?,
    patchCount: Int,
    sources: List<PatchSourceRef>,
    installState: InstallState,
    installedPackageName: String?,
    usingMountInstall: Boolean,
    excludedPatches: List<String> = emptyList(),
    isExpertMode: Boolean = false,
    showBackToGameHint: Boolean = false,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onIgnoreSignatureMismatch: () -> Unit,
    onOpen: () -> Unit,
    onHomeClick: () -> Unit,
    onLogsClick: () -> Unit,
    onSaveClick: () -> Unit,
    isSaving: Boolean
) {
    val windowSize = rememberWindowSize()
    val failed = installState.failed
    // In the app's color, as the install button under it is, or the theme where there is none
    val accent = LocalAccent.current ?: MaterialTheme.colorScheme.primary

    SuccessLayout(
        windowSize = windowSize,
        header = {
            SuccessIcon(
                icon = if (failed) Icons.Default.Close else Icons.Default.Check,
                iconTint = if (failed) MaterialTheme.colorScheme.error else accent,
                glowColor = if (failed) MaterialTheme.colorScheme.error else accent,
                windowSize = windowSize,
                packageName = packageName
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing)
            ) {
                SuccessStatusText(installState, installedPackageName, windowSize)
                SuccessAppSummary(packageName, version, patchCount, sources)
            }
        },
        details = {
            SuccessInstructionsText(installState, installedPackageName, usingMountInstall)
            SuccessNotice(
                text = (installState as? InstallState.Error)?.message,
                tone = SemanticTone.Error,
                icon = Icons.Outlined.ErrorOutline
            )
            SuccessNotice(
                text = stringResource(R.string.patcher_conflict_hint).takeIf { installState is InstallState.Conflict },
                tone = SemanticTone.Error,
                icon = Icons.Outlined.Warning
            )
            SuccessNotice(
                text = stringResource(R.string.patcher_patches_excluded_for_installer, excludedPatches.joinToString())
                    .takeIf { excludedPatches.isNotEmpty() && installState is InstallState.Ready },
                tone = SemanticTone.Neutral,
                icon = Icons.Outlined.Info
            )
        },
        actions = {
            InstallActions(
                installState = installState,
                usingMountInstall = usingMountInstall,
                onInstall = onInstall,
                onUninstall = onUninstall,
                onIgnoreSignatureMismatch = onIgnoreSignatureMismatch,
                onOpen = onOpen
            )
        },
        bottomBar = { horizontalPadding ->
            BackToGameCallout(visible = showBackToGameHint && !failed)
            PatcherBottomActionBar(
                horizontalPadding = horizontalPadding,
                showCancelButton = false,
                showLogsButton = isExpertMode,
                showSaveButton = true,
                onLogsClick = onLogsClick,
                onHomeClick = onHomeClick,
                onSaveClick = onSaveClick,
                isSaving = isSaving
            )
        }
    )
}

/**
 * Lays the success screen out: one column over the bar in portrait, or the [header] over the bar
 * beside the [details] and [actions] in landscape, so each part is written once.
 */
@Composable
private fun SuccessLayout(
    windowSize: WindowSize,
    header: @Composable ColumnScope.() -> Unit,
    details: @Composable ColumnScope.() -> Unit,
    actions: @Composable () -> Unit,
    bottomBar: @Composable ColumnScope.(horizontalPadding: Dp) -> Unit
) {
    val itemSpacing = windowSize.itemSpacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        if (isLandscape()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = windowSize.contentPadding),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing * 3)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.CenterVertically),
                        content = header
                    )
                    bottomBar(0.dp)
                }
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.CenterVertically)
                ) {
                    details()
                    actions()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = windowSize.contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(itemSpacing * 3, Alignment.CenterVertically)
            ) {
                header()
                details()
                actions()
            }
            bottomBar(Defaults.ContentPadding)
        }
    }
}

/** How long the ring marking the result takes to spread and fade. */
private const val PULSE_MILLIS = 1100

/**
 * One ring in [color] spreading from a circle [from] across and fading, drawn past this element's
 * bounds. It marks each result once rather than moving for as long as the screen shows.
 */
@Composable
private fun Modifier.resultPulse(color: Color, from: Dp): Modifier {
    // Skipped where accessibility services ask for less motion
    val reduceMotion = rememberAccessibilityEnabled()
    val progress = remember { Animatable(1f) }
    LaunchedEffect(color) {
        if (reduceMotion) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(PULSE_MILLIS, easing = FastOutSlowInEasing))
    }

    // Read while drawing, so the ring spreads without recomposing what it lies under
    return drawBehind {
        val t = progress.value
        if (t >= 1f) return@drawBehind
        val startRadius = from.toPx() / 2f
        drawCircle(
            color = color.copy(alpha = 0.5f * (1f - t)),
            radius = startRadius * (1f + 0.8f * t),
            style = Stroke(width = (1.dp + 5.dp * (1f - t)).toPx())
        )
    }
}

/**
 * Status icon marked by a [resultPulse] in [glowColor]. With a [packageName] it is the patched app's
 * own icon carrying [icon] as a badge.
 */
@Composable
private fun SuccessIcon(
    icon: ImageVector,
    iconTint: Color,
    glowColor: Color,
    windowSize: WindowSize,
    packageName: String? = null
) {
    val compact = windowSize.widthSizeClass == WindowWidthSizeClass.Compact
    // An app icon carries its own picture, so it is drawn larger than a bare glyph to read as one
    val iconSize = when {
        packageName != null -> if (compact) 112.dp else 96.dp
        else -> if (compact) 80.dp else 64.dp
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(iconSize * 1.4f)
            .resultPulse(glowColor, from = iconSize)
    ) {
        if (packageName == null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = iconTint
            )
        } else {
            Box(modifier = Modifier.size(iconSize)) {
                AppIcon(
                    packageName = packageName,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 6.dp)
                        .size(iconSize * 0.4f)
                        .background(iconTint, CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize * 0.24f),
                        tint = iconTint.contrastingContent()
                    )
                }
            }
        }
    }
}

/** Name, version, patch count and sources of what was patched, under the screen's title. */
@Composable
private fun SuccessAppSummary(packageName: String, version: String?, patchCount: Int, sources: List<PatchSourceRef>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppLabel(
            packageName = packageName,
            style = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        )
        Text(
            text = listOfNotNull(
                version?.let { "v$it" },
                pluralStringResource(R.plurals.patch_count, patchCount, patchCount.toString())
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (sources.isNotEmpty()) {
            Text(
                text = sources.joinToString(", ") { listOfNotNull(it.name, it.version).joinToString(" ") },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Success screen status text.
 */
@Composable
private fun SuccessStatusText(
    installState: InstallState,
    installedPackageName: String?,
    windowSize: WindowSize
) {
    AnimatedContent(
        targetState = titleFor(installState, installedPackageName),
        transitionSpec = Animations.fadeCrossfade(500),
        label = "title_animation"
    ) { titleRes ->
        Text(
            text = stringResource(titleRes),
            style = if (windowSize.widthSizeClass == WindowWidthSizeClass.Compact) {
                MaterialTheme.typography.headlineLarge
            } else {
                MaterialTheme.typography.headlineMedium
            },
            fontWeight = FontWeight.Bold,
            color = if (installState.failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Success screen instructions text.
 */
@Composable
private fun SuccessInstructionsText(
    installState: InstallState,
    installedPackageName: String?,
    usingMountInstall: Boolean
) {
    AnimatedContent(
        targetState = subtitleFor(installState, installedPackageName, usingMountInstall),
        transitionSpec = Animations.fadeCrossfade(500),
        label = "subtitle_animation"
    ) { subtitleRes ->
        if (subtitleRes != 0) {
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Callout pointing at the button that leads back to the mini-game this screen took the place of.
 * It sits on the button because the way back is not obvious from a label reading "Logs".
 */
@Composable
private fun BackToGameCallout(visible: Boolean) {
    if (!visible) return

    BottomActionCallout(
        text = stringResource(R.string.patcher_back_to_game_hint, stringResource(R.string.logs)),
        // Logs comes first in a bar of Logs, Home and Save
        slot = 0,
        slots = 3,
        icon = Icons.Outlined.SportsEsports
    )
}

/** A [Notice] explaining the install state, easing in and out as [text] comes and goes. */
@Composable
private fun SuccessNotice(text: String?, tone: SemanticTone, icon: ImageVector) {
    AnimatedVisibility(
        visible = text != null,
        enter = Animations.fadeIn,
        exit = Animations.fadeOut
    ) {
        // Kept through the exit, so the notice fades with what it said rather than going blank
        var shown by remember { mutableStateOf(text.orEmpty()) }
        if (text != null) shown = text
        Notice(text = shown, tone = tone, icon = icon)
    }
}

/**
 * Install action button, with the signature bypass offered below it on devices that can use it.
 */
@Composable
private fun InstallActions(
    installState: InstallState,
    usingMountInstall: Boolean,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onIgnoreSignatureMismatch: () -> Unit,
    onOpen: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
    ) {
        InstallActionButton(
            installState = installState,
            usingMountInstall = usingMountInstall,
            onInstall = onInstall,
            onUninstall = onUninstall,
            onOpen = onOpen
        )

        AnimatedVisibility(
            visible = (installState as? InstallState.Conflict)?.canIgnoreSignatureMismatch == true,
            enter = Animations.fadeIn,
            exit = Animations.fadeOut
        ) {
            AppDialogOutlinedButton(
                text = stringResource(R.string.install_ignore_signature),
                onClick = onIgnoreSignatureMismatch
            )
        }
    }
}

/**
 * Styled installation action button.
 */
@Composable
private fun InstallActionButton(
    installState: InstallState,
    usingMountInstall: Boolean,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isInstalling = installState is InstallState.Installing
    val isInstalled = installState is InstallState.Installed
    val conflictPackageName = (installState as? InstallState.Conflict)?.packageName

    // Solid in the app's color, the one the screen wears, or the theme where there is none
    val accent = LocalAccent.current ?: MaterialTheme.colorScheme.primary
    val buttonColors = if (installState.failed) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    } else {
        ButtonDefaults.buttonColors(containerColor = accent, contentColor = appAccentContent(accent))
    }

    Button(
        onClick = {
            when {
                isInstalled -> onOpen()
                conflictPackageName != null -> onUninstall(conflictPackageName)
                else -> onInstall()
            }
        },
        enabled = !isInstalling,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(Defaults.CardCornerRadius),
        colors = buttonColors,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
    ) {
        if (isInstalling) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(
                    if (usingMountInstall) R.string.mounting_ellipsis
                    else R.string.installing_ellipsis
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        } else {
            ThemedIcon(
                icon = when {
                    isInstalled -> Icons.AutoMirrored.Outlined.Launch
                    conflictPackageName != null -> Icons.Default.DeleteForever
                    usingMountInstall -> Icons.Outlined.Link
                    else -> Icons.Outlined.InstallMobile
                },
                tint = LocalContentColor.current
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(
                    when {
                        isInstalled -> R.string.open
                        conflictPackageName != null -> R.string.uninstall
                        usingMountInstall -> R.string.mount
                        else -> R.string.install
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Get title resource based on state.
 */
private fun titleFor(installState: InstallState, installedPackageName: String?): Int = when {
    installState is InstallState.Installing -> R.string.installing_ellipsis
    installedPackageName != null || installState is InstallState.Installed -> R.string.patcher_success_title
    installState is InstallState.Conflict -> R.string.patcher_conflict_title
    installState is InstallState.Error -> R.string.patcher_install_error_title
    else -> R.string.patcher_complete_title
}

/**
 * Get subtitle resource based on state.
 */
private fun subtitleFor(
    installState: InstallState,
    installedPackageName: String?,
    usingMountInstall: Boolean
): Int = when {
    installState is InstallState.Installing -> R.string.patcher_installing_subtitle
    installedPackageName != null || installState is InstallState.Installed -> R.string.patcher_success_subtitle
    installState is InstallState.Conflict -> R.string.patcher_conflict_subtitle
    installState is InstallState.Error -> R.string.patcher_install_error_subtitle
    // The install button says as much, so only mounting, which works differently, is explained
    else -> if (usingMountInstall) R.string.patcher_ready_to_mount_subtitle else 0
}

/**
 * Patching failed screen.
 */
@Composable
fun PatchingFailed(
    onHomeClick: () -> Unit,
    onErrorClick: () -> Unit
) {
    val windowSize = rememberWindowSize()

    // Main content area
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = windowSize.contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing * 2)
            ) {
                SuccessIcon(
                    icon = Icons.Default.Error,
                    iconTint = MaterialTheme.colorScheme.error,
                    glowColor = MaterialTheme.colorScheme.error,
                    windowSize = windowSize
                )

                Text(
                    text = stringResource(R.string.patcher_failed_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = stringResource(R.string.patcher_failed_hint, stringResource(R.string.error_)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Bottom action bar
        PatcherBottomActionBar(
            showCancelButton = false,
            showErrorButton = true,
            onHomeClick = onHomeClick,
            onErrorClick = onErrorClick
        )
    }
}
