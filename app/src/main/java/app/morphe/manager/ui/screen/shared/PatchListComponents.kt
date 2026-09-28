/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One patch of a [LabeledSection] list, its rows parted by [DividedRows]. Every row of the list is a
 * patch that applies, so a mark on each would tell them nothing apart. [dimmed] is for a patch
 * known only by name, whose source no longer describes it.
 */
@Composable
fun PatchNameRow(
    name: String,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false
) {
    Text(
        text = name,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalDialogTextColor.current.let { if (dimmed) it.copy(alpha = 0.5f) else it },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding)
    )
}

/** [items] one under another in a [LabeledSection], parted by dividers as the version lists are. */
@Composable
fun <T> DividedRows(items: List<T>, row: @Composable (T) -> Unit) {
    items.forEachIndexed { index, item ->
        if (index > 0) SettingsDivider()
        row(item)
    }
}

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
        SettingsGroup {
            Column(
                modifier = Modifier.padding(vertical = Defaults.ItemSpacing),
                verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
            ) {
                content()
            }
        }
    }
}

@Composable
fun PatchOptionsGroup(
    patchName: String,
    options: Map<String, Any?>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = patchName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = LocalDialogTextColor.current
        )
        options.forEach { (key, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
                Text(
                    text = formatOptionValue(value),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = LocalDialogTextColor.current
                )
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
