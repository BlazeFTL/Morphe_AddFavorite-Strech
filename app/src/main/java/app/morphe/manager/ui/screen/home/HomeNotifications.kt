/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.batch.BatchRunState
import app.morphe.manager.domain.batch.SavedBatchQueue
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.BundleUpdateStatus
import app.morphe.manager.util.formatMegabytes

/** Visibility flag paired with the tap callback of a single alert in the strip. */
@Immutable
data class AlertState(val visible: Boolean, val onShow: () -> Unit)

/**
 * The apps whose patches have moved on, and the tap that queues them. Shown from one app up,
 * which is also when the cards themselves start showing their update badge.
 */
@Immutable
data class RepatchAlertState(val count: Int, val visible: Boolean, val onShow: () -> Unit)

/**
 * The batch queue while it waits to be started, patches or holds results nobody closed yet, and
 * the tap back to it. [saved] is the queue being prepared, which outlives the process.
 */
@Immutable
data class BatchQueueAlertState(val run: BatchRunState?, val saved: SavedBatchQueue?, val onShow: () -> Unit)

/** Transient state driving the bundle-update progress snackbar. */
@Immutable
data class BundleUpdateState(
    val visible: Boolean,
    val status: BundleUpdateStatus,
    val progress: PatchBundleRepository.BundleUpdateProgress?
)

/** Aggregate of all notification-strip inputs, grouped by alert. */
@Immutable
data class HomeNotificationsUi(
    val managerUpdate: AlertState,
    val outdatedManager: AlertState,
    val heldBackSources: AlertState,
    val blockedSources: AlertState,
    val metadataErrors: AlertState,
    val meteredSkipped: AlertState,
    val repatchAvailable: RepatchAlertState,
    val batchQueue: BatchQueueAlertState,
    val bundleUpdate: BundleUpdateState
)

/**
 * One alert of the notification strip.
 *
 * @param snoozable Whether a swipe may put it away. Alerts that are the only way back to running
 *   work, or that block patching outright, stay put.
 * @param revision Changes when the alert has something new to say, which brings a snoozed one back.
 */
private class HomeAlert(
    val key: String,
    val visible: Boolean,
    val tone: SemanticTone,
    val snoozable: Boolean = true,
    val revision: String = "",
    val card: @Composable () -> Unit
)

/**
 * Section 1: Unified notifications overlay component.
 *
 * Alerts stack behind the most pressing one, so however many are raised they cover no more of the
 * screen than one. A swipe snoozes an alert for the session, and the bell chip brings it back.
 */
@Composable
fun NotificationsOverlay(
    notifications: HomeNotificationsUi,
    modifier: Modifier = Modifier
) {
    val alerts = homeAlerts(notifications)
    // Revision each alert was snoozed at, kept across navigation and rotation
    val snoozed = rememberSaveable(saver = SnoozeSaver) { mutableStateMapOf() }
    val expanded = rememberSaveable { mutableStateOf(false) }

    // An alert that goes away forgets its snooze, so raised again it is shown again
    val hiddenKeys = alerts.filterNot { it.visible }.map { it.key }
    LaunchedEffect(hiddenKeys) { hiddenKeys.forEach(snoozed::remove) }

    fun HomeAlert.isSnoozed() = snoozable && snoozed[key] == revision
    val active = alerts.filter { it.visible && !it.isSnoozed() }
    val snoozedCount = alerts.count { it.visible && it.isSnoozed() }
    val stacked = active.size - 1

    LaunchedEffect(stacked) { if (stacked <= 0) expanded.value = false }
    val lastShown = if (expanded.value) active.lastOrNull() else active.firstOrNull()

    val controls: @Composable (Modifier) -> Unit = { controlsModifier ->
        AlertStackControls(
            stacked = stacked,
            expanded = expanded.value,
            snoozedCount = snoozedCount,
            onToggleExpanded = { expanded.value = !expanded.value },
            onRestoreSnoozed = { snoozed.clear() },
            modifier = controlsModifier
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Every alert keeps its slot, so one leaving or joining the stack animates in place
        alerts.forEach { alert ->
            val isTop = alert === active.firstOrNull()
            key(alert.key) {
                AnimatedVisibility(
                    visible = alert in active && (isTop || expanded.value),
                    enter = Animations.slideUpFadeEnter,
                    exit = Animations.slideUpFadeExit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SwipeAway(
                            enabled = alert.snoozable,
                            onSwiped = { snoozed[alert.key] = alert.revision }
                        ) {
                            alert.card()
                        }
                        AnimatedVisibility(
                            visible = isTop && !expanded.value && stacked > 0,
                            enter = Animations.expandFadeEnter,
                            exit = Animations.shrinkFadeExit
                        ) {
                            StackEdges(
                                tones = active.drop(1).take(2).map { it.tone },
                                onClick = { expanded.value = true }
                            )
                        }
                        // Hung on the edge of the last card, so the chips cover as little as
                        // possible of the screen under the strip
                        if (alert === lastShown) controls(Modifier.hangOverAbove())
                    }
                }
            }
        }

        // With every alert snoozed the bell is all that is left of the strip
        if (active.isEmpty()) controls(Modifier)

        // Kept out of the stack: it only lasts while the update runs, and its progress is the point
        BundleUpdateSnackbar(
            visible = notifications.bundleUpdate.visible,
            status = notifications.bundleUpdate.status,
            progress = notifications.bundleUpdate.progress,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Every alert of the strip, raised or not, in order of priority. */
@Composable
private fun homeAlerts(notifications: HomeNotificationsUi): List<HomeAlert> {
    val batchRun = notifications.batchQueue.run
    val batchRunning = batchRun?.isActive == true
    val batchFinished = batchRun?.hasOutcome == true
    val batchSucceeded = (batchRun?.succeeded ?: 0) > 0
    val queuedCount = notifications.batchQueue.saved?.queuedCount ?: 0
    val repatch = notifications.repatchAvailable

    return listOf(
        // Blocked source alert takes priority and cannot be dismissed while the block persists
        alert(
            key = "blocked_sources",
            visible = notifications.blockedSources.visible,
            tone = SemanticTone.Error,
            icon = Icons.Outlined.Block,
            title = stringResource(R.string.home_blocked_source_title),
            subtitle = stringResource(R.string.home_blocked_source_subtitle),
            onClick = notifications.blockedSources.onShow,
            snoozable = false
        ),
        // The one alert raised by something that already went wrong rather than something
        // that might, so it sits above the rest
        alert(
            key = "held_back_sources",
            visible = notifications.heldBackSources.visible,
            tone = SemanticTone.Error,
            icon = Icons.Outlined.ErrorOutline,
            title = stringResource(R.string.home_held_back_title),
            subtitle = stringResource(R.string.home_held_back_subtitle),
            onClick = notifications.heldBackSources.onShow
        ),
        // A source built for a newer patcher stays unusable until the app itself is updated
        alert(
            key = "outdated_manager",
            visible = notifications.outdatedManager.visible,
            tone = SemanticTone.Error,
            icon = Icons.Outlined.SystemUpdate,
            title = stringResource(R.string.home_outdated_manager_title),
            subtitle = stringResource(R.string.home_outdated_manager_subtitle),
            onClick = notifications.outdatedManager.onShow
        ),
        alert(
            key = "metered_skipped",
            visible = notifications.meteredSkipped.visible,
            tone = SemanticTone.Warning,
            icon = Icons.Outlined.SignalCellularAlt,
            title = stringResource(R.string.home_metered_skipped_title),
            subtitle = stringResource(R.string.home_metered_skipped_subtitle),
            onClick = notifications.meteredSkipped.onShow
        ),
        alert(
            key = "metadata_errors",
            visible = notifications.metadataErrors.visible,
            tone = SemanticTone.Warning,
            icon = Icons.Outlined.CloudOff,
            title = stringResource(R.string.home_metadata_errors_title),
            subtitle = stringResource(R.string.home_metadata_errors_subtitle),
            onClick = notifications.metadataErrors.onShow
        ),
        alert(
            key = "manager_update",
            visible = notifications.managerUpdate.visible,
            tone = SemanticTone.Primary,
            icon = Icons.Outlined.Update,
            title = stringResource(R.string.home_update_available),
            subtitle = stringResource(R.string.home_update_available_subtitle),
            onClick = notifications.managerUpdate.onShow
        ),
        // Once its screen is gone this is the only way back to a running queue, which holds
        // single-app patching back meanwhile, so it cannot be swiped away
        alert(
            key = "batch_running",
            visible = batchRunning,
            tone = SemanticTone.Primary,
            icon = Icons.Outlined.AutoFixHigh,
            progress = batchRun?.let { run -> if (run.total > 0) run.processed.toFloat() / run.total else 0f },
            title = stringResource(R.string.batch_patch_title),
            subtitle = stringResource(
                R.string.batch_patch_progress_counter,
                (batchRun?.processed ?: 0).toString(),
                (batchRun?.total ?: 0).toString()
            ),
            onClick = notifications.batchQueue.onShow,
            snoozable = false
        ),
        // The patched APKs of a finished run are only installable from its summary
        alert(
            key = "batch_finished",
            visible = batchFinished,
            tone = if (batchSucceeded) SemanticTone.Success else SemanticTone.Error,
            icon = if (batchSucceeded) Icons.Outlined.Check else Icons.Outlined.ErrorOutline,
            title = stringResource(
                if (batchSucceeded) R.string.patcher_complete_title else R.string.patcher_failed_title
            ),
            subtitle = stringResource(
                R.string.batch_patch_summary,
                (batchRun?.succeeded ?: 0).toString(),
                (batchRun?.failed ?: 0).toString(),
                (batchRun?.skipped ?: 0).toString()
            ),
            onClick = notifications.batchQueue.onShow
        ),
        alert(
            key = "batch_queued",
            visible = queuedCount > 0 && !batchRunning && !batchFinished,
            tone = SemanticTone.Primary,
            icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
            title = pluralStringResource(R.plurals.batch_queue_pending_count, queuedCount, queuedCount.toString()),
            subtitle = stringResource(R.string.batch_queue_pending_subtitle),
            onClick = notifications.batchQueue.onShow,
            revision = queuedCount.toString()
        ),
        // The badges on the cards say the same thing one app at a time. This says it once
        // and queues every one of them, which is the part that is otherwise several taps.
        // While a queue is patching or awaiting review, its own alert stands in for this one
        alert(
            key = "repatch_available",
            visible = repatch.visible && repatch.count > 0 && !batchRunning && !batchFinished,
            tone = SemanticTone.Primary,
            icon = Icons.Outlined.AutoFixHigh,
            title = pluralStringResource(R.plurals.repatch_available_count, repatch.count, repatch.count.toString()),
            subtitle = stringResource(R.string.home_repatch_available_subtitle),
            onClick = repatch.onShow,
            revision = repatch.count.toString()
        )
    )
}

/**
 * An alert drawn as a [StatusCard].
 *
 * @param progress Share of the work done, which puts [icon] inside a ring filling up with it, for
 *   work still in progress.
 */
private fun alert(
    key: String,
    visible: Boolean,
    tone: SemanticTone,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    snoozable: Boolean = true,
    revision: String = "",
    progress: Float? = null
) = HomeAlert(key = key, visible = visible, tone = tone, snoozable = snoozable, revision = revision) {
    StatusCard(
        tone = tone,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leading = {
            if (progress != null) {
                ProgressRing(progress = progress) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                StatusDisc(icon = icon, tone = tone)
            }
        }
    )
}

/** Edges of the alerts waiting behind the top one, each in its own tone, like a deck seen side on. */
@Composable
private fun StackEdges(tones: List<SemanticTone>, onClick: () -> Unit) {
    val shape = RoundedCornerShape(bottomStart = StackEdgeHeight, bottomEnd = StackEdgeHeight)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        tones.forEachIndexed { depth, tone ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Defaults.ContentPadding + StackEdgeInset * (depth + 1))
                    .height(StackEdgeHeight)
                    .clip(shape)
                    .background(statusContainerColor(tone))
                    .border(width = 1.dp, color = statusBorderColor(tone), shape = shape)
            )
        }
    }
}

/** Chips of the strip: one folding the stack open and shut, one bringing back what was snoozed. */
@Composable
private fun AlertStackControls(
    stacked: Int,
    expanded: Boolean,
    snoozedCount: Int,
    onToggleExpanded: () -> Unit,
    onRestoreSnoozed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
    ) {
        AnimatedVisibility(
            visible = stacked > 0,
            enter = Animations.expandHorizFadeIn,
            exit = Animations.shrinkHorizFadeOut
        ) {
            AlertChip(
                icon = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                text = if (expanded) stringResource(R.string.less) else "+$stacked",
                onClick = onToggleExpanded
            )
        }
        AnimatedVisibility(
            visible = snoozedCount > 0,
            enter = Animations.expandHorizFadeIn,
            exit = Animations.shrinkHorizFadeOut
        ) {
            val description = pluralStringResource(
                R.plurals.home_alerts_snoozed_count,
                snoozedCount,
                snoozedCount.toString()
            )
            AlertChip(
                icon = Icons.Outlined.Notifications,
                text = snoozedCount.toString(),
                onClick = onRestoreSnoozed,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }
            )
        }
    }
}

/** Small pill among the strip's controls, lifted like the alerts it belongs to. */
@Composable
private fun AlertChip(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        border = CardBorder.of(MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private val StackEdgeInset = 12.dp
private val StackEdgeHeight = 6.dp

/** Lays the element half over whatever comes before it, so only its lower half takes up room. */
private fun Modifier.hangOverAbove() = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val lift = placeable.height / 2
    layout(placeable.width, placeable.height - lift) { placeable.place(0, -lift) }
}

private val SnoozeSaver = listSaver<SnapshotStateMap<String, String>, String>(
    save = { map -> map.flatMap { (key, revision) -> listOf(key, revision) } },
    restore = { saved ->
        mutableStateMapOf<String, String>().apply {
            saved.chunked(2).forEach { (key, revision) -> put(key, revision) }
        }
    }
)

/**
 * Bundle update snackbar.
 */
@Composable
fun BundleUpdateSnackbar(
    visible: Boolean,
    status: BundleUpdateStatus,
    progress: PatchBundleRepository.BundleUpdateProgress?,
    modifier: Modifier = Modifier
) {
    // Swipe only for terminal states, so an update in progress cannot be dismissed. A new status
    // brings the alert back, which is how a new update cycle shows up after the last was swiped
    DismissibleAlert(
        visible = visible,
        swipeEnabled = status != BundleUpdateStatus.Updating,
        resetKey = status,
        modifier = modifier
    ) {
        BundleUpdateCard(status = status, progress = progress)
    }
}

/** The card of the bundle update, from its progress while it runs to how it ended. */
@Composable
private fun BundleUpdateCard(
    status: BundleUpdateStatus,
    progress: PatchBundleRepository.BundleUpdateProgress?
) {
    val sourcesByUid = rememberSourcesByUid()

    val downloadFraction = progress?.bytesTotal
        ?.takeIf { it > 0L }
        ?.let { progress.bytesRead.toFloat() / it }
    val isDownloading = progress?.phase == PatchBundleRepository.BundleUpdatePhase.Downloading &&
            downloadFraction != null && downloadFraction > 0f
    val countFraction = progress
        ?.takeIf { it.total > 1 && it.completed > 0 }
        ?.let { it.completed.toFloat() / it.total }
    // Null until there is a share to show, which spins the ring rather than parking it at zero
    val ringProgress = if (isDownloading) downloadFraction else countFraction

    val tone = when (status) {
        BundleUpdateStatus.Updating -> SemanticTone.Neutral
        BundleUpdateStatus.Success -> SemanticTone.Success
        BundleUpdateStatus.Warning -> SemanticTone.Warning
        BundleUpdateStatus.Error -> SemanticTone.Error
    }

    val title = stringResource(
        when (status) {
            BundleUpdateStatus.Updating -> R.string.home_updating_sources
            BundleUpdateStatus.Success -> R.string.home_update_success
            BundleUpdateStatus.Warning -> R.string.home_update_skipped_metered
            BundleUpdateStatus.Error -> R.string.home_update_error
        }
    )

    // One line names the source at work and how much of it has arrived, while the badge carries
    // how far along the whole update is: its share, or how many sources are done when several run
    val activeName = progress?.activeNames?.firstOrNull() ?: progress?.currentBundleName
    val arrived = progress?.takeIf { isDownloading }?.let {
        stringResource(
            R.string.manager_update_progress_size,
            formatMegabytes(it.bytesRead),
            formatMegabytes(it.bytesTotal ?: 0L)
        )
    }
    val subtitle = when (status) {
        BundleUpdateStatus.Updating -> listOfNotNull(activeName, arrived).joinToString(DETAIL_SEPARATOR)
        BundleUpdateStatus.Success -> stringResource(R.string.home_update_success_subtitle)
        BundleUpdateStatus.Warning -> stringResource(R.string.home_update_skipped_metered_subtitle)
        BundleUpdateStatus.Error -> stringResource(R.string.home_update_error_subtitle)
    }
    val badge = when {
        status != BundleUpdateStatus.Updating || progress == null -> null
        progress.total > 1 -> stringResource(R.string.home_update_bundle_count, progress.completed, progress.total)
        isDownloading -> stringResource(R.string.patcher_percentage, (downloadFraction * 100).toInt().toString())
        else -> null
    }
    val activeSource = progress?.activeUids?.firstOrNull()?.let(sourcesByUid::get)

    StatusCard(
        tone = tone,
        title = title,
        subtitle = subtitle,
        badge = badge,
        leading = {
            Crossfade(targetState = status, label = "bundleUpdateLeading") { shown ->
                when (shown) {
                    BundleUpdateStatus.Updating -> ProgressRing(progress = ringProgress) {
                        if (activeSource != null) {
                            BundleIcon(bundle = activeSource, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    BundleUpdateStatus.Success -> StatusDisc(Icons.Outlined.Check, SemanticTone.Success)
                    BundleUpdateStatus.Warning -> StatusDisc(Icons.Outlined.SignalCellularAlt, SemanticTone.Warning)
                    BundleUpdateStatus.Error -> StatusDisc(Icons.Outlined.Warning, SemanticTone.Error)
                }
            }
        }
    )
}

/**
 * Shows [content] while [visible], sliding it in and out, and lets a swipe put it away for the rest
 * of the session while [swipeEnabled]. A change of [resetKey] while visible brings a swiped alert
 * back.
 */
@Composable
private fun DismissibleAlert(
    visible: Boolean,
    swipeEnabled: Boolean,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    content: @Composable () -> Unit
) {
    val dismissed = remember { mutableStateOf(false) }
    LaunchedEffect(visible, resetKey) { if (visible) dismissed.value = false }

    AnimatedVisibility(
        visible = visible && !dismissed.value,
        enter = Animations.slideUpFadeEnter,
        exit = Animations.slideUpFadeExit,
        modifier = modifier
    ) {
        SwipeAway(enabled = swipeEnabled, onSwiped = { dismissed.value = true }, content = content)
    }
}

/** Lets [content] be swiped off either side while [enabled], reporting it through [onSwiped]. */
@Composable
private fun SwipeAway(
    enabled: Boolean,
    onSwiped: () -> Unit,
    content: @Composable () -> Unit
) {
    // Made anew each time the alert shows, so one swiped away earlier does not come back
    // still pushed off to the side
    val swipeState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }
    )
    LaunchedEffect(swipeState.currentValue) {
        if (enabled && swipeState.currentValue != SwipeToDismissBoxValue.Settled) onSwiped()
    }

    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {},
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled
    ) {
        content()
    }
}

/**
 * Card of one alert in the notification strip: [leading] beside its [title] over a [subtitle],
 * with an optional [badge], and a chevron when a tap opens more. Laid on the app's own card
 * surface, lightly tinted with [tone], and lifted just enough to stand apart from what it floats over.
 */
@Composable
private fun StatusCard(
    tone: SemanticTone,
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    badge: String? = null,
    onClick: (() -> Unit)? = null
) {
    val neutral = tone == SemanticTone.Neutral
    val containerColor by animateColorAsState(
        targetValue = statusContainerColor(tone),
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "statusCardContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = statusBorderColor(tone),
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "statusCardBorder"
    )

    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding),
        shape = RoundedCornerShape(Defaults.CardCornerRadius),
        color = containerColor,
        border = CardBorder.of(borderColor),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .animateContentSize()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CrossfadeText(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotEmpty()) {
                    // Eased from one text to the next, but keyed by what comes before its details,
                    // so the figures of a running update tick over in place
                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = Animations.fadeCrossfade(200),
                        contentKey = { it.substringBefore(DETAIL_SEPARATOR) },
                        label = "statusCardSubtitle"
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (badge != null) {
                StatusBadge(text = badge, tone = if (neutral) SemanticTone.Primary else tone)
            }
            if (onClick != null) {
                ForwardChevronIcon(size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Fill of a [StatusCard] in [tone]: the app's card surface, lightly tinted unless neutral. */
@Composable
private fun statusContainerColor(tone: SemanticTone): Color {
    val surface = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    return if (tone == SemanticTone.Neutral) surface else tone.container.copy(alpha = 0.35f).compositeOver(surface)
}

@Composable
private fun statusBorderColor(tone: SemanticTone): Color =
    if (tone == SemanticTone.Neutral) MaterialTheme.colorScheme.outlineVariant else tone.accent.copy(alpha = 0.35f)

/** Leading icon of a [StatusCard] whose work is done, on a disc in [tone]. */
@Composable
private fun StatusDisc(icon: ImageVector, tone: SemanticTone) {
    StatusCircleIcon(
        icon = icon,
        containerColor = tone.container,
        contentColor = tone.content,
        size = StatusLeadingSize
    )
}

/**
 * Leading of a [StatusCard] whose work is still running: [content] inside a ring filling with
 * [progress], or spinning while there is no share to show yet.
 */
@Composable
private fun ProgressRing(progress: Float?, content: @Composable () -> Unit) {
    // Eased, since the share arrives in steps as the download reports back
    val animated by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "statusRingProgress"
    )

    Box(
        modifier = Modifier.size(StatusLeadingSize),
        contentAlignment = Alignment.Center
    ) {
        WavyProgressRing(
            progress = progress?.let { { animated } },
            wavelength = 10.dp,
            accentColor = null,
            strokeWidth = 3.5.dp,
            modifier = Modifier.matchParentSize()
        )
        content()
    }
}

private val StatusLeadingSize = 44.dp

/** Joins the parts of a [StatusCard]'s subtitle, a name and the details about it. */
private const val DETAIL_SEPARATOR = " · "
