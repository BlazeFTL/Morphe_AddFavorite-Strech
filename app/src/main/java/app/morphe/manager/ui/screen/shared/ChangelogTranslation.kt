/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.morphe.manager.R
import app.morphe.manager.data.platform.NetworkInfo
import app.morphe.manager.util.ChangelogSection
import app.morphe.manager.util.ChangelogTranslator
import app.morphe.manager.util.mapItemTexts
import app.morphe.manager.util.simpleMessage
import app.morphe.manager.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Whether a changelog dialog shows its entries in the app language, and the steps to get there:
 * the model download, with the user's consent on a metered network, and the translation itself.
 */
@Stable
class ChangelogTranslation internal constructor(
    private val language: String?,
    private val translator: ChangelogTranslator,
    private val networkInfo: NetworkInfo,
    private val scope: CoroutineScope,
    private val context: Context
) {
    private var session: ChangelogTranslator.Session? = null
    private var pending: Job? = null

    /** Whether there is a language to translate into at all. */
    val isAvailable: Boolean get() = language != null

    /** Whether entries show their translation rather than the original. */
    var isEnabled by mutableStateOf(false)
        private set

    var isDownloadingModel by mutableStateOf(false)
        private set

    /** Set while the model download waits for the user to accept a metered connection. */
    var isAwaitingMeteredConsent by mutableStateOf(false)
        private set

    fun toggle() {
        if (isEnabled) {
            isEnabled = false
            return
        }
        val language = language ?: return
        if (pending?.isActive == true) return

        pending = scope.launch {
            reportingFailure {
                when {
                    translator.isModelDownloaded(language) -> isEnabled = true
                    !networkInfo.isConnected() -> context.toast(context.getString(R.string.no_network_toast))
                    networkInfo.isMetered() -> isAwaitingMeteredConsent = true
                    else -> downloadModel(language)
                }
            }
        }
    }

    fun onMeteredConsent(granted: Boolean) {
        isAwaitingMeteredConsent = false
        val language = language ?: return
        if (granted) pending = scope.launch { reportingFailure { downloadModel(language) } }
    }

    /**
     * [sections] as the release should show them: the original until the translation of every
     * change is ready, then the translation.
     */
    @Composable
    fun displayed(sections: List<ChangelogSection>): List<ChangelogSection> {
        if (!isEnabled) return sections

        val translated by produceState(initialValue = cached(sections), this, sections) {
            if (value == null) value = translate(sections)
        }
        return translated ?: sections
    }

    /**
     * [sections] translated from what is already at hand, so a reopened dialog does not flash,
     * or null when any change still needs work.
     */
    private fun cached(sections: List<ChangelogSection>): List<ChangelogSection>? {
        val session = session ?: return null
        return sections.mapItemTexts { session.cached(it) ?: return null }
    }

    /** Translates every change of [sections], or returns null once one fails. */
    private suspend fun translate(sections: List<ChangelogSection>): List<ChangelogSection>? =
        sections.mapItemTexts { translate(it) ?: return null }

    /** Translates [text], or turns translation off and returns null when that fails. */
    private suspend fun translate(text: String): String? {
        val session = session ?: language?.let(translator::open)?.also { session = it } ?: return null
        return try {
            session.translate(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Every entry fails alike once one does, so report it once and fall back to the original
            if (isEnabled) {
                isEnabled = false
                reportFailure(e)
            }
            null
        }
    }

    private suspend fun downloadModel(language: String) {
        isDownloadingModel = true
        try {
            translator.downloadModel(language)
            isEnabled = true
        } finally {
            isDownloadingModel = false
        }
    }

    /** Runs [block] in the dialog's scope, where a failure has to end in a message rather than a crash. */
    private suspend fun reportingFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportFailure(e)
        }
    }

    private fun reportFailure(error: Exception) {
        context.toast(context.getString(R.string.changelog_translation_failed, error.simpleMessage()))
    }

    internal fun close() {
        session?.close()
        session = null
    }
}

/** Translation state of one changelog dialog, into the language the app is currently shown in. */
@Composable
fun rememberChangelogTranslation(): ChangelogTranslation {
    val context = LocalContext.current.applicationContext
    val locale = LocalConfiguration.current.locales[0]
    val translator: ChangelogTranslator = koinInject()
    val networkInfo: NetworkInfo = koinInject()
    val scope = rememberCoroutineScope()

    val translation = remember(locale) {
        ChangelogTranslation(translator.languageFor(locale), translator, networkInfo, scope, context)
    }
    DisposableEffect(translation) {
        onDispose(translation::close)
    }
    return translation
}
