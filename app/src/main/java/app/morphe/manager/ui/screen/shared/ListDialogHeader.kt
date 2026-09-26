/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val HeaderIconSize = 44.dp

/**
 * Head of a list dialog that names what the list belongs to: its [icon], [title] and a
 * [subtitle] summing the list up, with the toggle for the list's search field.
 *
 * @param searchLabel What the field searches, as the toggle's description.
 * The header sits on a band like the app details' one, which gives the list scrolling under it an
 * edge to stop at.
 *
 * @param accentColor Color of the app the list belongs to, which tints the band, or null for a
 *   neutral one.
 * @param badges What the whole list shares, in a row under the title as the app details keep theirs.
 * @param actions Title actions drawn ahead of the search toggle.
 */
@Composable
fun ListDialogHeader(
    icon: @Composable (Modifier) -> Unit,
    title: String,
    subtitle: String,
    search: SearchFieldState,
    searchLabel: String,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    badges: (@Composable FlowRowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val band = appAccentFill(accentColor)
    val bleed = LocalDialogHorizontalInset.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .headerBand(band, bleed, statusBarHeight)
            .padding(vertical = Defaults.ContentPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
        ) {
            icon(Modifier.size(HeaderIconSize))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = LocalDialogTextColor.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
            }
            actions()
            TitleAction(
                icon = if (search.visible) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                contentDescription = searchLabel,
                onClick = { search.toggle() },
                style = TitleActionStyle.Toggle,
                active = search.visible
            )
        }
        if (badges != null) {
            // A row that folds its chips out resizes on the list's own spring, as the list's rows do,
            // without the settle, which would dip below the chips and clip them
            StatusBadgeRow(
                modifier = Modifier
                    .padding(top = Defaults.ContentPaddingSmall)
                    .animateContentSize(Animations.listSpring(dampingRatio = Spring.DampingRatioNoBouncy)),
                content = badges
            )
        }
    }
}

/**
 * Tints the header out to the dialog's edges and up under the status bar, rounded off below like
 * the app details' header. Only the drawing reaches past the header, so its content stays in line
 * with the list under it.
 */
private fun Modifier.headerBand(color: Color, bleed: Dp, statusBarHeight: Dp): Modifier = drawBehind {
    val corner = CornerRadius(Defaults.CardCornerRadius.toPx())
    val band = RoundRect(
        left = -bleed.toPx(),
        top = -statusBarHeight.toPx(),
        right = size.width + bleed.toPx(),
        bottom = size.height,
        bottomLeftCornerRadius = corner,
        bottomRightCornerRadius = corner
    )
    drawPath(Path().apply { addRoundRect(band) }, color)
}
