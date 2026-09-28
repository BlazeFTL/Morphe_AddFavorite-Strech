/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A patch of a [PatchEntryList] with its options. [dimmed] marks one not in effect: no longer
 * described by its source, or with only its options kept.
 */
@Immutable
data class PatchEntry(
    val name: String,
    val options: Map<String, Any?> = emptyMap(),
    val dimmed: Boolean = false
)

/**
 * Entries for stored [keys] with their [options]. Keys are suffixed on duplicate patch names, so each
 * is shown under [displayName] where the source still knows it.
 */
fun patchEntries(
    keys: List<String>,
    options: Map<String, Map<String, Any?>>,
    displayName: (String) -> String? = { null },
    dimmed: (String) -> Boolean = { false }
): List<PatchEntry> = keys.map { key ->
    PatchEntry(
        name = displayName(key) ?: key,
        options = options[key].orEmpty(),
        dimmed = dimmed(key)
    )
}

/**
 * [entries] in a [LabeledSection], parted by dividers. Only patches with options carry a mark, since
 * every row applies and a mark on each would tell nothing apart.
 */
@Composable
fun PatchEntryList(entries: List<PatchEntry>) {
    entries.forEachIndexed { index, entry ->
        if (index > 0) SettingsDivider()
        PatchEntryRow(entry)
    }
}

/** Share of an option row its key may take before it wraps. */
private const val OPTION_KEY_MAX_FRACTION = 0.4f

@Composable
private fun PatchEntryRow(entry: PatchEntry) {
    val textColor = LocalDialogTextColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.dimmed) textColor.copy(alpha = 0.5f) else textColor,
                modifier = Modifier.weight(1f)
            )
            if (entry.options.isNotEmpty()) {
                StatusBadge(
                    text = entry.options.size.toString(),
                    icon = Icons.Outlined.Tune,
                    tone = SemanticTone.Primary
                )
            }
        }
        if (entry.options.isNotEmpty()) {
            // A capped key cannot squeeze its value to a few letters a line, and the rail hangs the
            // options from their patch, whose name they would otherwise match in size
            BoxWithConstraints {
                val keyMaxWidth = (maxWidth - AccentRailWidth - Defaults.ItemSpacing) * OPTION_KEY_MAX_FRACTION
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
                ) {
                    AccentRail(modifier = Modifier.fillMaxHeight())
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        entry.options.forEach { (key, value) ->
                            // Set as the info panels set theirs, side by side on one baseline
                            Row(horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)) {
                                ValueLabel(
                                    text = key,
                                    color = textColor,
                                    modifier = Modifier
                                        .alignByBaseline()
                                        .widthIn(max = keyMaxWidth)
                                )
                                ValueText(
                                    text = formatOptionValue(value),
                                    color = textColor,
                                    modifier = Modifier
                                        .alignByBaseline()
                                        .weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Patch lists as plain text under [title] for the clipboard, options indented under their patch as shown. */
fun patchListText(title: String, lists: List<Pair<String?, List<PatchEntry>>>): String =
    (listOf(title) + lists.map { (heading, entries) ->
        listOfNotNull(heading).plus(
            entries.flatMap { entry ->
                listOf("- ${entry.name}") + entry.options.map { (key, value) ->
                    "    $key: ${formatOptionValue(value)}"
                }
            }
        ).joinToString("\n")
    }).joinToString("\n\n")

@Composable
fun LabeledSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    version: String? = null,
    count: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val effectiveVersion = version?.takeIf { it.isNotBlank() }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
    ) {
        if (title != null || effectiveVersion != null || count != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                effectiveVersion?.let { v ->
                    StatusBadge(text = v)
                }
                if (count != null) {
                    StatusBadge(
                        text = count.toString(),
                        tone = SemanticTone.Primary
                    )
                }
            }
        }
        // Edged in the color of the dialog it sits in, as the app's other cards are
        SectionCard(accentColor = LocalAccent.current) {
            Column(
                modifier = Modifier.padding(vertical = Defaults.ItemSpacing),
                verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
            ) {
                content()
            }
        }
    }
}

private fun formatOptionValue(value: Any?): String = when (value) {
    null -> "null"
    is String -> value
    is Boolean -> value.toString()
    is Number -> value.toString()
    is List<*> -> if (value.isEmpty()) "[]" else value.joinToString(", ")
    else -> value.toString()
}
