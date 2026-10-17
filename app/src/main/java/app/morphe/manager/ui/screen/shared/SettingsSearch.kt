/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * A search through a page of settings, which every [SettingsItem] under [LocalSettingsSearch] checks
 * itself against, leaving the page while it does not match. A [SettingsGroup] with nothing left in
 * it folds away whole, and a [SettingsSection] keeps its title only over what it still holds.
 */
@Stable
class SettingsSearch(private val query: () -> String) {
    /** Whether the query narrows the page. */
    val isActive: Boolean get() = query().isNotBlank()

    /** Every row the query leaves on the page. */
    internal val root = SearchMatches(parent = null)

    /** Rows the query leaves on the page, so it can tell when it leaves none. */
    val shown: Int get() = root.shown

    fun matches(texts: List<String?>): Boolean {
        val query = query().trim()
        return query.isEmpty() || texts.any { it != null && it.contains(query, ignoreCase = true) }
    }
}

/** Rows a search leaves within one part of the page, counted into the part that holds it too. */
@Stable
internal class SearchMatches(private val parent: SearchMatches?) {
    var shown by mutableIntStateOf(0)
        private set

    fun add(count: Int) {
        shown += count
        parent?.add(count)
    }
}

/**
 * The search the settings under it are narrowed by, null where nothing is searched. A dialog or sheet
 * resets it, so what it holds is never searched along with the page it was opened from.
 */
val LocalSettingsSearch = staticCompositionLocalOf<SettingsSearch?> { null }

/** The part of the page a matching row counts into, the whole page where it stands in none. */
internal val LocalSearchMatches = staticCompositionLocalOf<SearchMatches?> { null }

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
        val counter = LocalSearchMatches.current ?: search.root
        DisposableEffect(counter) {
            counter.add(1)
            onDispose { counter.add(-1) }
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

/**
 * A part of the page that counts its own matches, see [LocalSearchMatches], under the part it stands
 * in. Null where nothing is searched.
 */
@Composable
internal fun rememberSearchMatches(): SearchMatches? {
    val search = LocalSettingsSearch.current ?: return null
    val parent = LocalSearchMatches.current ?: search.root
    return remember(parent) { SearchMatches(parent) }
}

/**
 * Folds this away while a search leaves nothing in [matches], and back once it does. It stays
 * composed meanwhile, so its rows go on checking themselves against the query.
 */
@Composable
internal fun Modifier.foldsWhileUnmatched(matches: SearchMatches?): Modifier {
    val search = LocalSettingsSearch.current
    if (matches == null || search == null) return this
    val shown = animateFloatAsState(
        targetValue = if (!search.isActive || matches.shown > 0) 1f else 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "search_fold"
    )
    return clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = (placeable.height * shown.value).roundToInt()
            layout(placeable.width, height) { placeable.placeRelative(0, 0) }
        }
        .graphicsLayer { alpha = shown.value }
}

/**
 * A titled part of the settings: its [title] over [content], laid outside by side with the rest of
 * the page rather than in a column of its own. While a search runs the title stays over what the
 * part still holds, so each match reads under where it lives.
 *
 * @param onTitleClick Opens where the part lives, for one found away from the page it belongs to.
 */
@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector? = null,
    onTitleClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val matches = rememberSearchMatches()
    CompositionLocalProvider(LocalSearchMatches provides matches) {
        SectionTitle(text = title, icon = icon, onClick = onTitleClick)
        content()
    }
}

/**
 * Stacks the blocks of a page of settings [Defaults.SpaceLarge] apart, closing up over any a search
 * folds away, which a column spaced by its arrangement would still leave a gap for. A block opens
 * the gap above it no wider than itself, so the gap folds away along with it rather than at its end.
 */
@Composable
fun SettingsColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = Defaults.SpaceLarge.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val tops = IntArray(placeables.size)
        var height = 0
        placeables.forEachIndexed { index, placeable ->
            // Nothing above the first block it shows, however many folded ones stand before it
            height += minOf(gap, placeable.height, height)
            tops[index] = height
            height += placeable.height
        }
        // Held to the least height it is given, which a short page would otherwise be centered in
        layout(constraints.maxWidth, height.coerceAtLeast(constraints.minHeight)) {
            placeables.forEachIndexed { index, placeable -> placeable.place(0, tops[index]) }
        }
    }
}

/** A [SettingsGroup]'s column, folding away whole while a search leaves none of its rows. */
@Composable
internal fun SearchableGroupColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val matches = rememberSearchMatches()
    CompositionLocalProvider(LocalSearchMatches provides matches) {
        Column(modifier = modifier.foldsWhileUnmatched(matches), content = content)
    }
}
