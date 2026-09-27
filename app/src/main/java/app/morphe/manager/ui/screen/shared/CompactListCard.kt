/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Size of the picture leading a [CompactListCard]. */
val CompactCardIconSize = 36.dp

/** Gap between the [CompactListCard]s of a list. */
val CompactCardSpacing = 6.dp

private val CompactCardPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)

/**
 * Card of a long list to pick one entry from, the one patch cards sit on, laying out a picture
 * and the entry's name over its details in a row. Such a list can run to hundreds of entries, a
 * folder or the installed apps, so the card is set tighter than the app's other cards to keep as
 * many in view as plain rows would.
 */
@Composable
fun CompactListCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    SettingsItemCard(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        borderWidth = 1.dp,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CompactCardPadding),
            horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** Placeholder of a [CompactListCard] while the list it belongs to is read. */
@Composable
fun ShimmerCompactListCard() {
    CompactListCard(onClick = null) {
        ShimmerBox(
            modifier = Modifier.size(CompactCardIconSize),
            shape = RoundedCornerShape(Defaults.CompactCornerRadius)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ShimmerText(widthFraction = 0.55f, height = 14.dp)
            ShimmerText(widthFraction = 0.35f, height = 12.dp)
        }
    }
}
