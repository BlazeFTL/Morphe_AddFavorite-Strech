/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import app.morphe.manager.R

/**
 * Title a full page opens on, after a back action where the page was opened from another and
 * before the page's own [actions].
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            TitleAction(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
                style = TitleActionStyle.Neutral
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

/**
 * Padding a page puts around content that scrolls on under the status bar, ending [bottom] clear of
 * what floats over its lower edge.
 */
@Composable
fun pageContentPadding(bottom: Dp): PaddingValues {
    val horizontal = rememberWindowSize().contentPadding
    return PaddingValues(
        start = horizontal,
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + Defaults.SpaceLarge,
        end = horizontal,
        bottom = bottom
    )
}

/**
 * A page opened from another, such as one section of the settings: a [PageHeader] with a way back,
 * then [content] in one scrolling column, its blocks spaced as the settings space theirs.
 */
@Composable
fun SubpageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollFade(scrollState)
                .verticalScroll(scrollState)
                .padding(pageContentPadding(bottom = navigationBar + Defaults.SpaceLarge)),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            PageHeader(title = title, onBack = onBack)
            content()
        }

        ListScrollbar(scrollState = scrollState, modifier = Modifier.scrollbarSystemBarsPadding())
        ScrollToTopButton(scrollState = scrollState, extraBottomPadding = navigationBar)
        StatusBarScrim()
    }
}
