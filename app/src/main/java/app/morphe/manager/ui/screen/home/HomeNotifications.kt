/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.batch.BatchRunState
import app.morphe.manager.domain.batch.SavedBatchQueue
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.BundleUpdateStatus
import app.morphe.manager.util.formatMegabytes
import kotlin.math.roundToInt

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

/** Transient state driving the bundle update alert, from its progress to how it ended. */
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
 * @param passing Whether it only reports on something under way, which a swipe puts away for good
 *   rather than behind the bell, since brought back it would have nothing left to say.
 */
private class HomeAlert(
    val key: String,
    val visible: Boolean,
    val tone: SemanticTone,
    val snoozable: Boolean = true,
    val revision: String = "",
    val passing: Boolean = false,
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
    val snoozedCount = alerts.count { it.visible && !it.passing && it.isSnoozed() }
    val passingKeys = alerts.filter { it.passing }.mapTo(HashSet()) { it.key }
    val stacked = active.size - 1

    LaunchedEffect(stacked) { if (stacked <= 0) expanded.value = false }
    // A swipe on the folded stack hands its place to the alert drawn beneath it, so the two swap
    // in place. Kept until another alert comes on top, since a transition must not change midway
    val handoff = remember { mutableStateOf<StackHandoff?>(null) }
    val topKey = active.firstOrNull()?.key
    LaunchedEffect(topKey) { if (handoff.value?.revealed != topKey) handoff.value = null }

    // Eased in step with the cards, so the chips ride down with the last one as it leaves
    val hang = animateFloatAsState(
        targetValue = if (active.isEmpty()) 0f else 1f,
        animationSpec = Animations.defaultTween(),
        label = "alertControlsHang"
    )

    // Each alert carries the gap under it, so the gap leaves with it instead of after it
    Column(modifier = modifier) {
        // Every alert keeps its slot, so one leaving or joining the stack animates in place
        alerts.forEach { alert ->
            val isTop = alert === active.firstOrNull()
            key(alert.key) {
                AnimatedVisibility(
                    visible = alert in active && (isTop || expanded.value),
                    enter = if (alert.key == handoff.value?.revealed) EnterTransition.None
                    else Animations.slideUpFadeEnter,
                    // A swiped card has already left sideways, so only its slot closes after it
                    exit = when {
                        alert.key == handoff.value?.swiped -> ExitTransition.None
                        alert.isSnoozed() -> Animations.shrinkFadeExit
                        else -> Animations.slideUpFadeExit
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val beneath = active.getOrNull(1)?.takeIf { isTop && !expanded.value }
                    Box(modifier = Modifier.padding(bottom = AlertSpacing)) {
                        SwipeAway(
                            enabled = alert.snoozable,
                            onSwiped = {
                                if (beneath != null) handoff.value = StackHandoff(alert.key, beneath.key)
                                snoozed[alert.key] = alert.revision
                            },
                            background = beneath?.card
                        ) {
                            alert.card()
                        }
                    }
                }
            }
        }

        // Drawn up against the top card across its gap, as the edges of what waits behind it
        AnimatedVisibility(
            visible = !expanded.value && stacked > 0,
            enter = Animations.expandFadeEnter,
            exit = Animations.shrinkFadeExit,
            modifier = Modifier.offset { IntOffset(0, -AlertSpacing.roundToPx()) }
        ) {
            StackEdges(
                tones = active.drop(1).take(StackEdgeCount).map { it.tone },
                onClick = { expanded.value = true }
            )
        }

        // Hung on the edge of the last card, so the chips cover as little as possible of the
        // screen under the strip. With every alert snoozed the bell is all that is left of it
        AlertStackControls(
            stacked = stacked,
            expanded = expanded.value,
            snoozedCount = snoozedCount,
            onToggleExpanded = { expanded.value = !expanded.value },
            onRestoreSnoozed = { snoozed.keys.retainAll(passingKeys) },
            modifier = Modifier.hangOverAbove(fraction = { hang.value }, gap = AlertSpacing)
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
    val bundleUpdate = notifications.bundleUpdate

    return listOf(
        // Leads while it lasts, so the progress of a running update stays in sight. Only how it
        // ended can be swiped, and a new status brings it back
        HomeAlert(
            key = "bundle_update",
            visible = bundleUpdate.visible,
            tone = bundleUpdate.status.tone,
            snoozable = bundleUpdate.status != BundleUpdateStatus.Updating,
            revision = bundleUpdate.status.name,
            passing = true
        ) {
            BundleUpdateCard(status = bundleUpdate.status, progress = bundleUpdate.progress)
        },
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

/** Swipe on the folded stack that put [swiped] away and uncovered [revealed] under it. */
@Immutable
private data class StackHandoff(val swiped: String, val revealed: String)

/** Edges of the alerts waiting behind the top one, each in its own tone, like a deck seen side on. */
@Composable
private fun StackEdges(tones: List<SemanticTone>, onClick: () -> Unit) {
    val shape = RoundedCornerShape(bottomStart = StackEdgeHeight, bottomEnd = StackEdgeHeight)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Each edge folds away on its own, so the deck thins as alerts leave it
        repeat(StackEdgeCount) { depth ->
            AnimatedVisibility(
                visible = depth < tones.size,
                enter = Animations.expandFadeEnter,
                exit = Animations.shrinkFadeExit
            ) {
                val tone = rememberLatest(tones.getOrNull(depth)) ?: return@AnimatedVisibility
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
    // The chips keep their last count while they shrink away, rather than reading 0 on the way out
    val shownStacked = rememberLatest(stacked.takeIf { it > 0 }) ?: 0
    val shownSnoozed = rememberLatest(snoozedCount.takeIf { it > 0 }) ?: 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding),
        horizontalArrangement = Arrangement.End
    ) {
        AnimatedRowPill(visible = stacked > 0, gap = AlertSpacing) {
            AlertChip(
                icon = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                text = if (expanded) stringResource(R.string.less) else "+$shownStacked",
                onClick = onToggleExpanded
            )
        }
        AnimatedRowPill(visible = snoozedCount > 0, gap = AlertSpacing) {
            val description = pluralStringResource(
                R.plurals.home_alerts_snoozed_count,
                shownSnoozed,
                shownSnoozed.toString()
            )
            AlertChip(
                icon = Icons.Outlined.Notifications,
                text = shownSnoozed.toString(),
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
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private val AlertSpacing = 8.dp
private const val StackEdgeCount = 2
private val StackEdgeInset = 12.dp
private val StackEdgeHeight = 6.dp

/**
 * Lays the element half over whatever comes before it, across that one's [gap], by [fraction].
 * Only its lower half and its own [gap] take up room then, and at 0 it sits fully below.
 */
private fun Modifier.hangOverAbove(fraction: () -> Float, gap: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val half = placeable.height / 2
    val gapPx = gap.roundToPx()
    val shown = fraction()
    val lift = ((half + gapPx) * shown).roundToInt()
    layout(placeable.width, placeable.height + gapPx - (half * shown).roundToInt()) {
        placeable.place(0, -lift)
    }
}

private val SnoozeSaver = listSaver<SnapshotStateMap<String, String>, String>(
    save = { map -> map.flatMap { (key, revision) -> listOf(key, revision) } },
    restore = { saved ->
        mutableStateMapOf<String, String>().apply {
            saved.chunked(2).forEach { (key, revision) -> put(key, revision) }
        }
    }
)

private val BundleUpdateStatus.tone: SemanticTone
    get() = when (this) {
        BundleUpdateStatus.Updating -> SemanticTone.Neutral
        BundleUpdateStatus.Success -> SemanticTone.Success
        BundleUpdateStatus.Warning -> SemanticTone.Warning
        BundleUpdateStatus.Error -> SemanticTone.Error
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
        tone = status.tone,
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
 * Lets [content] be swiped off either side while [enabled], reporting it through [onSwiped] once
 * it is fully off. [background] is uncovered as it goes.
 */
@Composable
private fun SwipeAway(
    enabled: Boolean,
    onSwiped: () -> Unit,
    background: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    // Made anew each time the alert shows, so one swiped away earlier does not come back
    // still pushed off to the side
    val swipeState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }
    )
    // Kept as one callback, since the box reports again whenever it is handed a new one
    val currentOnSwiped = rememberUpdatedState { if (enabled) onSwiped() }
    val onDismiss = remember { { _: SwipeToDismissBoxValue -> currentOnSwiped.value() } }

    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            // Only seen through the gap the swipe opens, so it stays out of reach of screen readers
            if (background != null) Box(modifier = Modifier.clearAndSetSemantics {}) { background() }
        },
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        onDismiss = onDismiss
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
