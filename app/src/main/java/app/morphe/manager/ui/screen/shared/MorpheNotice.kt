/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much room a notice takes. [Comfortable] is the standalone warning a dialog is built
 * around, [Compact] the aside that sits between other content.
 */
enum class MorpheNoticeDensity {
    Comfortable,
    Compact
}

/** Sizing for a single [MorpheNoticeDensity]. */
private data class NoticeMetrics(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val iconSize: Dp,
    val itemSpacing: Dp
)

private val ComfortableMetrics = NoticeMetrics(
    horizontalPadding = 16.dp,
    verticalPadding = 16.dp,
    iconSize = 24.dp,
    itemSpacing = 12.dp
)

private val CompactMetrics = NoticeMetrics(
    horizontalPadding = 12.dp,
    verticalPadding = 8.dp,
    iconSize = 20.dp,
    itemSpacing = 8.dp
)

/**
 * Full-width tinted block carrying a warning, a hint or a status line.
 *
 * @param text The message
 * @param icon Optional icon drawn before the message
 * @param tone Semantic color role
 * @param density How much room the block takes
 * @param isCentered Centers the content and shrinks the block to fit it
 * @param modifier Modifier to be applied to the notice
 */
@Composable
fun MorpheNotice(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: MorpheTone = MorpheTone.Neutral,
    density: MorpheNoticeDensity = MorpheNoticeDensity.Comfortable,
    isCentered: Boolean = false
) {
    val metrics = when (density) {
        MorpheNoticeDensity.Comfortable -> ComfortableMetrics
        MorpheNoticeDensity.Compact -> CompactMetrics
    }
    val textStyle = when (density) {
        MorpheNoticeDensity.Comfortable -> MaterialTheme.typography.bodyMedium
        MorpheNoticeDensity.Compact -> MaterialTheme.typography.bodySmall
    }
    val contentColor = tone.content

    // Add zero-width space so long tokens can break at "/" and "." - cached per text value
    val breakableText = remember(text) {
        text.replace("/", "/​").replace(".", ".​")
    }

    Surface(
        modifier = if (isCentered) modifier.wrapContentWidth() else modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MorpheDefaults.CompactCornerRadius),
        color = tone.container
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = metrics.horizontalPadding,
                vertical = metrics.verticalPadding
            ),
            horizontalArrangement = if (isCentered) {
                Arrangement.spacedBy(metrics.itemSpacing, Alignment.CenterHorizontally)
            } else {
                Arrangement.spacedBy(metrics.itemSpacing)
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                MorpheIcon(icon = it, tint = contentColor, size = metrics.iconSize)
            }
            Text(
                text = breakableText,
                style = textStyle,
                color = contentColor,
                textAlign = if (isCentered) TextAlign.Center else TextAlign.Start
            )
        }
    }
}
