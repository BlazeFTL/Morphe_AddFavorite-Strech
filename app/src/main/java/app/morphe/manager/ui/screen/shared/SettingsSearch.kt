/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.*

/**
 * A search through a page of settings, which every [SettingsItem] under [LocalSettingsSearch] checks
 * itself against, leaving the page while it does not match. The section titles step aside meanwhile,
 * so what matches reads as one list.
 */
@Stable
class SettingsSearch(private val query: () -> String) {
    /** Whether the query narrows the page. */
    val isActive: Boolean get() = query().isNotBlank()

    /** Rows the query leaves on the page, so it can tell when it leaves none. */
    var shown by mutableIntStateOf(0)
        internal set

    fun matches(texts: List<String?>): Boolean {
        val query = query().trim()
        return query.isEmpty() || texts.any { it != null && it.contains(query, ignoreCase = true) }
    }
}

/**
 * The search the settings under it are narrowed by, null where nothing is searched. A dialog or sheet
 * resets it, so what it holds is never searched along with the page it was opened from.
 */
val LocalSettingsSearch = staticCompositionLocalOf<SettingsSearch?> { null }

/**
 * Shows [content], a setting found by [texts], unless the [LocalSettingsSearch] leaves it out,
 * folding it away and back as the query changes. Counted among the matches while it matches, not
 * while it folds away.
 */
@Composable
fun SearchableSetting(texts: List<String?>, content: @Composable () -> Unit) {
    val search = LocalSettingsSearch.current ?: return content()
    val matches = search.matches(texts)
    if (matches) {
        DisposableEffect(search) {
            search.shown++
            onDispose { search.shown-- }
        }
    }
    AnimatedVisibility(
        visible = matches,
        enter = Animations.expandFadeEnter,
        exit = Animations.shrinkFadeExit
    ) {
        content()
    }
}
