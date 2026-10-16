/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import app.morphe.manager.ManagerApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Short messages for the user, shown as a snackbar on whichever surface is on top, see
 * [MessageHost]. A screen or the work behind it raises one without holding a [Context], as a
 * string resource is resolved by the surface that shows it. One raised before any surface is up
 * waits for the first, and a surface closing with messages left hands them to the one below.
 */
class UserMessages {
    private val hosts = mutableListOf<MessageHostSlot>()
    private val waiting = mutableListOf<Message>()

    fun show(text: String, long: Boolean = false, action: MessageAction? = null) =
        post(Message({ text }, long, action))

    fun show(@StringRes text: Int, vararg args: Any?, long: Boolean = false, action: MessageAction? = null) =
        post(Message({ it.getString(text, *args) }, long, action))

    /**
     * Shows [text] only while a Morphe screen is in focus, for work that also runs without one: an
     * update check woken by an FCM push would otherwise leave it waiting for the app to come back.
     */
    fun showIfInForeground(@StringRes text: Int, vararg args: Any?) {
        if (ManagerApplication.isInForeground) show(text, *args)
    }

    /** Shows [text] until the returned handle takes it down, for work whose end is not known up front. */
    fun showUntilDismissed(@StringRes text: Int): MessageHandle {
        val dismissed = CompletableDeferred<Unit>()
        post(Message({ it.getString(text) }, long = true, dismissed = dismissed))
        return MessageHandle(dismissed)
    }

    /** [action], confirming itself with [done], the feedback every selection gives. */
    fun confirming(done: String, action: () -> Unit): () -> Unit = {
        show(done)
        action()
    }

    private fun post(message: Message) {
        synchronized(hosts) {
            val host = hosts.lastOrNull()
            if (host != null) host.post(message) else waiting += message
        }
    }

    internal fun attach(host: MessageHostSlot) {
        synchronized(hosts) {
            hosts += host
            waiting.forEach(host::post)
            waiting.clear()
        }
    }

    internal fun detach(host: MessageHostSlot) {
        synchronized(hosts) { hosts -= host }
        host.takeUnshown().forEach(::post)
    }
}

/** A button on a message, such as one undoing what the message reports. */
class MessageAction(@param:StringRes val label: Int, val onClick: () -> Unit)

/** Takes down a message shown by [UserMessages.showUntilDismissed], wherever it has got to. */
class MessageHandle internal constructor(private val dismissed: CompletableDeferred<Unit>) {
    fun dismiss() {
        dismissed.complete(Unit)
    }
}

internal class Message(
    val text: (Context) -> String,
    val long: Boolean,
    val action: MessageAction? = null,
    val dismissed: CompletableDeferred<Unit>? = null
) {
    val isDismissed get() = dismissed?.isCompleted == true
}

/** A surface's place in [UserMessages], holding what it has yet to show. */
internal class MessageHostSlot {
    private val queue = Channel<Message>(Channel.UNLIMITED)
    private var current: Message? = null

    fun post(message: Message) {
        queue.trySend(message)
    }

    suspend fun showAll(state: SnackbarHostState, context: Context) = coroutineScope {
        for (message in queue) {
            if (message.isDismissed) continue
            current = message
            val shown = launch {
                val result = state.showSnackbar(
                    message = message.text(context),
                    actionLabel = message.action?.let { context.getString(it.label) },
                    // One that stays up a while can be closed once read, a short one goes before
                    // a close button would be reached
                    withDismissAction = message.long,
                    duration = when {
                        message.dismissed != null -> SnackbarDuration.Indefinite
                        message.long -> SnackbarDuration.Long
                        else -> SnackbarDuration.Short
                    }
                )
                if (result == SnackbarResult.ActionPerformed) message.action?.onClick?.invoke()
            }
            // Cancelling the call takes the snackbar down with it
            val watcher = message.dismissed?.let { dismissed -> launch { dismissed.await(); shown.cancel() } }
            shown.join()
            watcher?.cancel()
            current = null
        }
    }

    /** The message cut short, if any, then those still waiting, in the order they came. */
    fun takeUnshown(): List<Message> = buildList {
        current?.let(::add)
        current = null
        while (true) add(queue.tryReceive().getOrNull() ?: break)
    }.filterNot { it.isDismissed }
}

/**
 * [content] with the app's messages shown over its bottom edge while it is the top surface. The
 * snackbar rises above whatever inside marks itself with [keepsClearOfMessages], such as a bottom
 * bar, as Material places one over the bar of its screen.
 */
@Composable
fun MessageHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val messages = koinInject<UserMessages>()
    val context = LocalContext.current
    val slot = remember { MessageHostSlot() }
    val state = remember { SnackbarHostState() }
    val clearance = remember { MessageClearance() }
    DisposableEffect(messages, slot) {
        messages.attach(slot)
        onDispose { messages.detach(slot) }
    }
    LaunchedEffect(slot) { slot.showAll(state, context) }

    Box(modifier = modifier.onPlaced { clearance.hostBottom = it.boundsInWindow().bottom }) {
        CompositionLocalProvider(LocalMessageClearance provides clearance) {
            content()
        }
        SnackbarHost(
            hostState = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = Defaults.ContentMaxWidth)
                .fillMaxWidth()
                // A bar already stands clear of the navigation bar, so its height covers that too
                .then(
                    if (clearance.height > 0f) {
                        Modifier.padding(bottom = with(LocalDensity.current) { clearance.height.toDp() })
                    } else {
                        Modifier.navigationBarsPadding()
                    }
                )
                .padding(horizontal = Defaults.SpaceLarge, vertical = Defaults.SpaceSmall)
        )
    }
}

/** Keeps the messages of the surface this sits in clear of it, for a bar along its bottom edge. */
@Composable
fun Modifier.keepsClearOfMessages(): Modifier {
    val clearance = LocalMessageClearance.current ?: return this
    val key = remember { Any() }
    DisposableEffect(clearance) { onDispose { clearance.tops -= key } }
    return onPlaced { coordinates ->
        val top = coordinates.boundsInWindow().top
        if (clearance.tops[key] != top) clearance.tops[key] = top
    }
}

private val LocalMessageClearance = staticCompositionLocalOf<MessageClearance?> { null }

/** How far up the bars of a surface reach, so its snackbar can stand above the highest. */
@Stable
private class MessageClearance {
    val tops = mutableStateMapOf<Any, Float>()
    var hostBottom by mutableFloatStateOf(0f)

    /** Distance from the surface's bottom edge to the top of its highest bar, none without one. */
    val height: Float
        get() = tops.values.minOrNull()?.let { (hostBottom - it).coerceAtLeast(0f) } ?: 0f
}
