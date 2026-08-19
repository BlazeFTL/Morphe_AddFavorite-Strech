/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.Animations
import app.morphe.manager.ui.screen.shared.AppDialogTextField
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.HeroInfoCard

/**
 * Header card shown at the top of patches-list dialogs.
 */
@Composable
internal fun PatchesListHeaderCard(
    title: String,
    totalCount: Int,
    filteredCount: Int,
    isFiltering: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Extension
) {
    HeroInfoCard(
        icon = icon,
        title = title,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Outlined.Widgets,
            contentDescription = null,
            tint = LocalContentColor.current,
            modifier = Modifier.size(16.dp)
        )
        val patchCountLabel = pluralStringResource(
            R.plurals.patch_count,
            totalCount,
            totalCount
        )
        val countText = if (isFiltering) "$filteredCount/$patchCountLabel"
        else patchCountLabel
        AnimatedContent(
            targetState = countText,
            transitionSpec = Animations.counterTransitionSpec,
            label = "patches_count"
        ) { count ->
            Text(
                text = count,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Collapsible section header for the universal patches of one bundle.
 *
 * A null [onToggle] drops the chevron and the click, for the cases where the section has
 * nothing left to fold away.
 */
@Composable
internal fun UniversalPatchesHeader(
    count: Int,
    isExpanded: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    // One chevron that turns, so the fold reads as the same control in both states
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "universal_patches_chevron"
    )

    HomeGlassCategoryRow(
        title = stringResource(R.string.expert_mode_universal_patches),
        count = pluralStringResource(R.plurals.patch_count, count, count),
        onClick = onToggle,
        leading = {
            Icon(
                imageVector = Icons.Outlined.Public,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailing = {
            if (onToggle != null) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(
                        if (isExpanded) R.string.collapse else R.string.expand
                    ),
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        cornerRadius = Defaults.SettingsCornerRadius,
        modifier = modifier
    )
}

/**
 * Search field + optional filter button row.
 */
@Composable
internal fun PatchesListSearchRow(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showFilterButton: Boolean,
    isFilterActive: Boolean,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AppDialogTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text(stringResource(R.string.expert_mode_search)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null
                    )
                },
                showClearButton = true,
                modifier = Modifier.weight(1f)
            )

            if (showFilterButton) {
                FilledTonalIconButton(
                    onClick = onFilterClick,
                    modifier = Modifier.padding(bottom = 4.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isFilterActive)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isFilterActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = stringResource(R.string.filter),
                        modifier = Modifier.size(Defaults.IconSizeSmall)
                    )
                }
            }
        }
    }
}

/**
 * "No results" empty state used when search or filter yields no patches.
 */
@Composable
internal fun PatchesListEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.expert_mode_no_results),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
