#!/usr/bin/env python3
"""
patch.py -- apply the favorite-universal-patches feature to a
MorpheApp/morphe-manager checkout (main or dev branch).

Anchor-based, not a unified diff: every change is matched against a
short, unique span of unobfuscated source text rather than a line
number, so it survives unrelated changes elsewhere in the file.

Idempotent: safe to re-run, safe against a tree that already has some
or all of the changes applied.

Usage:
    python3 patch.py [path-to-morphe-manager-checkout]   # apply
    python3 patch.py [path] --check                      # dry-run only
"""
import argparse
import pathlib
import sys

_APP_DIALOG = [
    (
        '                    .widthIn(max = if (isLandscape) 600.dp else 450.dp)\n                    .fillMaxHeight(),',
        '                    .fillMaxSize(),',
        'dialog Column fills full size instead of width/height cap',
    ),
]

_PREFERENCES_MANAGER = [
    (
        '    val useExpertMode = booleanPreference("use_expert_mode", false)',
        '    val useExpertMode = booleanPreference("use_expert_mode", false)\n\n    /** Names of universal patches favorited by the user, displayed at the top of universal patch sections. */\n    val favoriteUniversalPatches = stringSetPreference("favorite_universal_patches", emptySet())',
        'pref declaration',
    ),
    (
        '        val useExpertMode: Boolean? = null,',
        '        val useExpertMode: Boolean? = null,\n        val favoriteUniversalPatches: Set<String>? = null,',
        'SettingsSnapshot field',
    ),
    (
        '        useExpertMode = useExpertMode.get(),',
        '        useExpertMode = useExpertMode.get(),\n        favoriteUniversalPatches = favoriteUniversalPatches.get().takeIf { it.isNotEmpty() },',
        'export',
    ),
    (
        '        snapshot.useExpertMode?.let { useExpertMode.value = it }',
        '        snapshot.useExpertMode?.let { useExpertMode.value = it }\n        snapshot.favoriteUniversalPatches?.let { favoriteUniversalPatches.value = it }',
        'restore',
    ),
]

_SETTING_COMPONENTS = [
    (
        'import androidx.compose.animation.Crossfade\nimport androidx.compose.animation.core.tween\nimport androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.background\nimport androidx.compose.foundation.border\nimport androidx.compose.foundation.clickable\nimport androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.selection.toggleable\nimport androidx.compose.foundation.shape.CircleShape\n',
        'import androidx.compose.animation.Crossfade\nimport androidx.compose.animation.core.tween\nimport androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.ExperimentalFoundationApi\nimport androidx.compose.foundation.background\nimport androidx.compose.foundation.border\nimport androidx.compose.foundation.clickable\nimport androidx.compose.foundation.combinedClickable\nimport androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.selection.toggleable\nimport androidx.compose.foundation.shape.CircleShape\n',
        'imports',
    ),
    (
        ' * Elevated card with proper Material 3 theming.\n * Base card for all other card types.\n */\n@Composable\nfun SurfaceCard(\n    modifier: Modifier = Modifier,\n    onClick: (() -> Unit)? = null,\n    enabled: Boolean = true,\n    elevation: Dp = Defaults.CardElevation,\n    cornerRadius: Dp = Defaults.CardCornerRadius,\n',
        ' * Elevated card with proper Material 3 theming.\n * Base card for all other card types.\n */\n@OptIn(ExperimentalFoundationApi::class)\n@Composable\nfun SurfaceCard(\n    modifier: Modifier = Modifier,\n    onClick: (() -> Unit)? = null,\n    onLongClick: (() -> Unit)? = null,\n    enabled: Boolean = true,\n    elevation: Dp = Defaults.CardElevation,\n    cornerRadius: Dp = Defaults.CardCornerRadius,\n',
        'SurfaceCard signature + OptIn',
    ),
    (
        '        else -> null\n    }\n\n    Surface(\n        modifier = modifier\n            .fillMaxWidth()\n            .clip(RoundedCornerShape(cornerRadius))\n            .then(\n                if (onClick != null) {\n                    Modifier.clickable(enabled = enabled, onClick = onClick)\n                } else Modifier\n            ),\n        shape = RoundedCornerShape(cornerRadius),\n        color = effectiveColor,\n        contentColor = MaterialTheme.colorScheme.onSurface,\n',
        '        else -> null\n    }\n\n    val clickModifier = when {\n        onClick != null || onLongClick != null -> Modifier.combinedClickable(\n            enabled = enabled,\n            onClick = onClick ?: {},\n            onLongClick = onLongClick\n        )\n        else -> Modifier\n    }\n\n    Surface(\n        modifier = modifier\n            .fillMaxWidth()\n            .clip(RoundedCornerShape(cornerRadius))\n            .then(clickModifier),\n        shape = RoundedCornerShape(cornerRadius),\n        color = effectiveColor,\n        contentColor = MaterialTheme.colorScheme.onSurface,\n',
        'clickModifier logic',
    ),
    (
        '@Composable\nfun SettingsItemCard(\n    onClick: (() -> Unit)?,\n    modifier: Modifier = Modifier,\n    enabled: Boolean = true,\n    borderWidth: Dp = 0.dp,\n',
        '@Composable\nfun SettingsItemCard(\n    onClick: (() -> Unit)?,\n    onLongClick: (() -> Unit)? = null,\n    modifier: Modifier = Modifier,\n    enabled: Boolean = true,\n    borderWidth: Dp = 0.dp,\n',
        'SettingsItemCard signature',
    ),
    (
        ') {\n    SurfaceCard(\n        onClick = onClick,\n        enabled = enabled,\n        elevation = 1.dp,\n        cornerRadius = Defaults.SettingsCornerRadius,\n',
        ') {\n    SurfaceCard(\n        onClick = onClick,\n        onLongClick = onLongClick,\n        enabled = enabled,\n        elevation = 1.dp,\n        cornerRadius = Defaults.SettingsCornerRadius,\n',
        'SettingsItemCard body SurfaceCard call',
    ),
]

_STRINGS = [
    (
        '<string name="expert_mode_universal_patches">Universal patches</string>',
        '<string name="expert_mode_universal_patches">Universal patches</string>\n    <string name="expert_mode_favorite_added">%s added to favorites</string>\n    <string name="expert_mode_favorite_removed">%s removed from favorites</string>\n    <string name="add_to_favorites">Add to favorites</string>\n    <string name="remove_from_favorites">Remove from favorites</string>',
        'favorite strings',
    ),
]

_EXPERT_PATCH_CARD_MAIN = [
    (
        'import androidx.compose.foundation.background\nimport androidx.compose.foundation.layout.*\nimport androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.outlined.*\nimport androidx.compose.material3.*\nimport androidx.compose.runtime.Composable\nimport androidx.compose.runtime.remember\nimport androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.res.stringResource\nimport androidx.compose.ui.semantics.contentDescription\nimport androidx.compose.ui.semantics.semantics\n',
        'import androidx.compose.foundation.background\nimport androidx.compose.foundation.layout.*\nimport androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.Star\nimport androidx.compose.material.icons.outlined.*\nimport androidx.compose.material3.*\nimport androidx.compose.runtime.Composable\nimport androidx.compose.runtime.remember\nimport androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.hapticfeedback.HapticFeedbackType\nimport androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalHapticFeedback\nimport androidx.compose.ui.res.stringResource\nimport androidx.compose.ui.semantics.contentDescription\nimport androidx.compose.ui.semantics.semantics\n',
        '03-ExpertPatchCard hunk 0',
    ),
    (
        '    buildsClone: Boolean = false,\n    hasRequiredOptionsMissing: Boolean = false,\n    lockState: PatchLockState = PatchLockState.NONE,\n    onToggle: () -> Unit,\n    onConfigureOptions: () -> Unit,\n    hasOptions: Boolean\n) {\n',
        '    buildsClone: Boolean = false,\n    hasRequiredOptionsMissing: Boolean = false,\n    lockState: PatchLockState = PatchLockState.NONE,\n    isFavorite: Boolean = false,\n    onToggle: () -> Unit,\n    onToggleFavorite: (() -> Unit)? = null,\n    onConfigureOptions: () -> Unit,\n    hasOptions: Boolean\n) {\n',
        '03-ExpertPatchCard hunk 1',
    ),
    (
        '    val patchState = if (isEnabled) enabledState else disabledState\n    val newLabel = stringResource(R.string.expert_mode_new_patches)\n    val cloneLabel = stringResource(R.string.clone)\n    // The card speaks for its whole contents, so the badges have to be read out here or not at all\n    val badges = listOfNotNull(newLabel.takeIf { isNew }, cloneLabel.takeIf { buildsClone })\n    val contentDesc = remember(patch.displayName, patchState, badges) {\n',
        '    val patchState = if (isEnabled) enabledState else disabledState\n    val newLabel = stringResource(R.string.expert_mode_new_patches)\n    val cloneLabel = stringResource(R.string.clone)\n    val addToFavoritesLabel = stringResource(R.string.add_to_favorites)\n    val removeFromFavoritesLabel = stringResource(R.string.remove_from_favorites)\n    // The card speaks for its whole contents, so the badges have to be read out here or not at all\n    val badges = listOfNotNull(newLabel.takeIf { isNew }, cloneLabel.takeIf { buildsClone })\n    val contentDesc = remember(patch.displayName, patchState, badges) {\n',
        '03-ExpertPatchCard hunk 2',
    ),
    (
        '    }\n\n    val context = LocalContext.current\n    val lockedMessage = when (lockState) {\n        PatchLockState.LOCKED_ON  -> stringResource(R.string.expert_mode_patch_required_by_installer)\n        PatchLockState.LOCKED_OFF -> stringResource(R.string.expert_mode_patch_unavailable_for_installer)\n',
        '    }\n\n    val context = LocalContext.current\n    val haptic = LocalHapticFeedback.current\n    val lockedMessage = when (lockState) {\n        PatchLockState.LOCKED_ON  -> stringResource(R.string.expert_mode_patch_required_by_installer)\n        PatchLockState.LOCKED_OFF -> stringResource(R.string.expert_mode_patch_unavailable_for_installer)\n',
        '03-ExpertPatchCard hunk 3',
    ),
    (
        '        { context.toast(lockedMessage) }\n    } else onToggle\n\n    val colors = MaterialTheme.colorScheme\n    val showErrorBorder = hasRequiredOptionsMissing && isEnabled\n    val containerColor = when {\n',
        '        { context.toast(lockedMessage) }\n    } else onToggle\n\n    val onCardLongClick: (() -> Unit)? = if (patch.isUniversal && onToggleFavorite != null) {\n        {\n            haptic.performHapticFeedback(HapticFeedbackType.LongPress)\n            onToggleFavorite()\n        }\n    } else null\n\n    val colors = MaterialTheme.colorScheme\n    val showErrorBorder = hasRequiredOptionsMissing && isEnabled\n    val containerColor = when {\n',
        '03-ExpertPatchCard hunk 4',
    ),
    (
        '\n    SettingsItemCard(\n        onClick = onCardClick,\n        color = containerColor,\n        borderWidth = 1.dp,\n        borderColor = when {\n',
        '\n    SettingsItemCard(\n        onClick = onCardClick,\n        onLongClick = onCardLongClick,\n        color = containerColor,\n        borderWidth = 1.dp,\n        borderColor = when {\n',
        '03-ExpertPatchCard hunk 5',
    ),
    (
        '                horizontalArrangement = Arrangement.SpaceBetween,\n                verticalAlignment = Alignment.CenterVertically\n            ) {\n                // Patch info',
        '                horizontalArrangement = Arrangement.SpaceBetween,\n                verticalAlignment = Alignment.Top\n            ) {\n                // Patch info',
        'outer Row top-aligned (icons no longer drift to card center)',
    ),
    (
        '                        if (buildsClone) {\n                            StatusBadge(\n                                text = cloneLabel,\n                                icon = Icons.Outlined.ContentCopy,\n                                tone = SemanticTone.Warning\n                            )\n                        }\n                    }',
        '                        if (buildsClone) {\n                            StatusBadge(\n                                text = cloneLabel,\n                                icon = Icons.Outlined.ContentCopy,\n                                tone = SemanticTone.Warning\n                            )\n                        }\n                        if (patch.isUniversal && onToggleFavorite != null) {\n                            IconButton(\n                                onClick = onToggleFavorite,\n                                modifier = Modifier\n                                    .size(28.dp)\n                                    .semantics {\n                                        contentDescription = "${patch.displayName}, ${if (isFavorite) removeFromFavoritesLabel else addToFavoritesLabel}"\n                                    }\n                            ) {\n                                Icon(\n                                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,\n                                    contentDescription = null,\n                                    modifier = Modifier.size(18.dp),\n                                    tint = if (isFavorite) Color(0xFFFFB300) else colors.onSurfaceVariant.copy(alpha = 0.6f)\n                                )\n                            }\n                        }\n                    }',
        'favorite star moved inline next to patch name',
    ),
]

_EXPERT_PATCH_CARD_DEV = [
    (
        'import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.outlined.*',
        'import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.Star\nimport androidx.compose.material.icons.outlined.*',
        'import Star icon',
    ),
    (
        'import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.res.stringResource',
        'import androidx.compose.ui.hapticfeedback.HapticFeedbackType\nimport androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalHapticFeedback\nimport androidx.compose.ui.res.stringResource',
        'import haptic feedback',
    ),
    (
        '    hasCustomOptions: Boolean = false,\n    lockState: PatchLockState = PatchLockState.NONE,\n    onToggle: () -> Unit,\n    onConfigureOptions: () -> Unit,\n    hasOptions: Boolean\n) {',
        '    hasCustomOptions: Boolean = false,\n    lockState: PatchLockState = PatchLockState.NONE,\n    isFavorite: Boolean = false,\n    onToggle: () -> Unit,\n    onToggleFavorite: (() -> Unit)? = null,\n    onConfigureOptions: () -> Unit,\n    hasOptions: Boolean\n) {',
        'PatchCard params',
    ),
    (
        '    val customizedLabel = stringResource(R.string.expert_mode_options_customized)',
        '    val customizedLabel = stringResource(R.string.expert_mode_options_customized)\n    val addToFavoritesLabel = stringResource(R.string.add_to_favorites)\n    val removeFromFavoritesLabel = stringResource(R.string.remove_from_favorites)',
        'favorite labels',
    ),
    (
        '    val context = LocalContext.current\n    val lockedMessage = when (lockState) {',
        '    val context = LocalContext.current\n    val haptic = LocalHapticFeedback.current\n    val lockedMessage = when (lockState) {',
        'haptic val',
    ),
    (
        '    val onCardClick: () -> Unit = if (lockState.blocksToggle(isEnabled) && lockedMessage != null) {\n        { context.toast(lockedMessage) }\n    } else onToggle\n',
        '    val onCardClick: () -> Unit = if (lockState.blocksToggle(isEnabled) && lockedMessage != null) {\n        { context.toast(lockedMessage) }\n    } else onToggle\n\n    val onCardLongClick: (() -> Unit)? = if (patch.isUniversal && onToggleFavorite != null) {\n        {\n            haptic.performHapticFeedback(HapticFeedbackType.LongPress)\n            onToggleFavorite()\n        }\n    } else null\n',
        'onCardLongClick logic',
    ),
    (
        '    SettingsItemCard(\n        onClick = onCardClick,\n        color = containerColor,',
        '    SettingsItemCard(\n        onClick = onCardClick,\n        onLongClick = onCardLongClick,\n        color = containerColor,',
        'SettingsItemCard onLongClick',
    ),
    (
        '                horizontalArrangement = Arrangement.SpaceBetween,\n                verticalAlignment = Alignment.CenterVertically\n            ) {\n                // Patch info',
        '                horizontalArrangement = Arrangement.SpaceBetween,\n                verticalAlignment = Alignment.Top\n            ) {\n                // Patch info',
        'outer Row top-aligned (icons no longer drift to card center)',
    ),
    (
        '                        if (buildsClone) {\n                            StatusBadge(\n                                text = cloneLabel,\n                                icon = Icons.Outlined.ContentCopy,\n                                tone = SemanticTone.Warning\n                            )\n                        }\n                    }',
        '                        if (buildsClone) {\n                            StatusBadge(\n                                text = cloneLabel,\n                                icon = Icons.Outlined.ContentCopy,\n                                tone = SemanticTone.Warning\n                            )\n                        }\n                        if (patch.isUniversal && onToggleFavorite != null) {\n                            IconButton(\n                                onClick = onToggleFavorite,\n                                modifier = Modifier\n                                    .size(28.dp)\n                                    .semantics {\n                                        contentDescription = "${patch.displayName}, ${if (isFavorite) removeFromFavoritesLabel else addToFavoritesLabel}"\n                                    }\n                            ) {\n                                Icon(\n                                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,\n                                    contentDescription = null,\n                                    modifier = Modifier.size(18.dp),\n                                    tint = if (isFavorite) Color(0xFFFFB300) else colors.onSurfaceVariant.copy(alpha = 0.6f)\n                                )\n                            }\n                        }\n                    }',
        'favorite star moved inline next to patch name',
    ),
]

_EXPERT_MODE_DIALOG_MAIN = [
    (
        'import androidx.compose.ui.text.style.TextOverflow\nimport androidx.compose.ui.unit.dp\nimport app.morphe.manager.R\nimport app.morphe.manager.patcher.patch.PatchBundleInfo\nimport app.morphe.manager.patcher.patch.PatchInfo\nimport app.morphe.manager.patcher.patch.PatchLockState\n',
        'import androidx.compose.ui.text.style.TextOverflow\nimport androidx.compose.ui.unit.dp\nimport app.morphe.manager.R\nimport app.morphe.manager.domain.manager.PreferencesManager\nimport app.morphe.manager.patcher.patch.PatchBundleInfo\nimport app.morphe.manager.patcher.patch.PatchInfo\nimport app.morphe.manager.patcher.patch.PatchLockState\n',
        '04-ExpertModeDialog hunk 0',
    ),
    (
        'import app.morphe.manager.util.PatchSelection\nimport app.morphe.manager.util.toast\nimport kotlinx.coroutines.launch\n\n/** Callbacks the expert-mode dialog invokes on the underlying patch selection. */\n@Stable\n',
        'import app.morphe.manager.util.PatchSelection\nimport app.morphe.manager.util.toast\nimport kotlinx.coroutines.launch\nimport org.koin.compose.koinInject\n\n/** Callbacks the expert-mode dialog invokes on the underlying patch selection. */\n@Stable\n',
        '04-ExpertModeDialog hunk 1',
    ),
    (
        '    val search = rememberSearchFieldState()\n    val showMultipleSourcesWarning = remember { mutableStateOf(false) }\n    val context = LocalContext.current\n\n    // Compute set of enabled patch names that have at least one required option\n    // with no default (default == null) and no user-provided non-blank value.\n',
        '    val search = rememberSearchFieldState()\n    val showMultipleSourcesWarning = remember { mutableStateOf(false) }\n    val context = LocalContext.current\n    val prefs: PreferencesManager = koinInject()\n    val favoritePatches by prefs.favoriteUniversalPatches.getAsState()\n    val coroutineScope = rememberCoroutineScope()\n\n    val onToggleFavoritePatch: (PatchInfo) -> Unit = { patch ->\n        val patchName = patch.name\n        val isFav = patchName in favoritePatches || patch.displayName in favoritePatches\n        val newFavorites = if (isFav) {\n            favoritePatches - patchName - patch.displayName\n        } else {\n            favoritePatches + patchName\n        }\n        coroutineScope.launch {\n            prefs.favoriteUniversalPatches.update(newFavorites)\n        }\n        val message = if (isFav) {\n            context.getString(R.string.expert_mode_favorite_removed, patch.displayName)\n        } else {\n            context.getString(R.string.expert_mode_favorite_added, patch.displayName)\n        }\n        context.toast(message)\n    }\n\n    // Compute set of enabled patch names that have at least one required option\n    // with no default (default == null) and no user-provided non-blank value.\n',
        '04-ExpertModeDialog hunk 2',
    ),
    (
        '                    val singleBundleList = rememberLazyListState()\n                    val sections = rememberPatchSections(\n                        patches = filteredPatches,\n                        newPatchNames = newPatches[bundle.uid] ?: emptySet()\n                    )\n                    Box(\n                        modifier = Modifier\n',
        '                    val singleBundleList = rememberLazyListState()\n                    val sections = rememberPatchSections(\n                        patches = filteredPatches,\n                        newPatchNames = newPatches[bundle.uid] ?: emptySet(),\n                        favoritePatches = favoritePatches\n                    )\n                    Box(\n                        modifier = Modifier\n',
        '04-ExpertModeDialog hunk 3',
    ),
    (
        '                                newPatchNames = newPatches[bundle.uid] ?: emptySet(),\n                                missingRequiredOptions = patchesWithMissingRequired,\n                                lockStateOf = lockStateOf,\n                                onToggle = { patchActions.onPatchToggle(bundle.uid, it) },\n                                onConfigureOptions = {\n                                    if (!it.options.isNullOrEmpty()) selectedPatchForOptions.value = bundle.uid to it\n                                }\n',
        '                                newPatchNames = newPatches[bundle.uid] ?: emptySet(),\n                                missingRequiredOptions = patchesWithMissingRequired,\n                                lockStateOf = lockStateOf,\n                                favoritePatches = favoritePatches,\n                                onToggle = { patchActions.onPatchToggle(bundle.uid, it) },\n                                onToggleFavorite = onToggleFavoritePatch,\n                                onConfigureOptions = {\n                                    if (!it.options.isNullOrEmpty()) selectedPatchForOptions.value = bundle.uid to it\n                                }\n',
        '04-ExpertModeDialog hunk 4',
    ),
    (
        '                            } else {\n                                val sections = rememberPatchSections(\n                                    patches = patches,\n                                    newPatchNames = newPatches[bundle.uid] ?: emptySet()\n                                )\n                                LazyColumn(\n                                    state = pageListStates[pageIndex],\n',
        '                            } else {\n                                val sections = rememberPatchSections(\n                                    patches = patches,\n                                    newPatchNames = newPatches[bundle.uid] ?: emptySet(),\n                                    favoritePatches = favoritePatches\n                                )\n                                LazyColumn(\n                                    state = pageListStates[pageIndex],\n',
        '04-ExpertModeDialog hunk 5',
    ),
    (
        '                                        newPatchNames = newPatches[bundle.uid] ?: emptySet(),\n                                        missingRequiredOptions = patchesWithMissingRequired,\n                                        lockStateOf = lockStateOf,\n                                        onToggle = { patchActions.onPatchToggle(bundle.uid, it) },\n                                        onConfigureOptions = {\n                                            if (!it.options.isNullOrEmpty()) selectedPatchForOptions.value = bundle.uid to it\n                                        }\n',
        '                                        newPatchNames = newPatches[bundle.uid] ?: emptySet(),\n                                        missingRequiredOptions = patchesWithMissingRequired,\n                                        lockStateOf = lockStateOf,\n                                        favoritePatches = favoritePatches,\n                                        onToggle = { patchActions.onPatchToggle(bundle.uid, it) },\n                                        onToggleFavorite = onToggleFavoritePatch,\n                                        onConfigureOptions = {\n                                            if (!it.options.isNullOrEmpty()) selectedPatchForOptions.value = bundle.uid to it\n                                        }\n',
        '04-ExpertModeDialog hunk 6',
    ),
    (
        "\n/**\n * Splits and orders one bundle's patches for display. New patches float to the top of each\n * group; within a group the order is alphabetical.\n */\n@Composable\nprivate fun rememberPatchSections(\n    patches: List<Pair<PatchInfo, Boolean>>,\n    newPatchNames: Set<String>\n): PatchSections = remember(patches, newPatchNames) {\n    val displayOrder = compareByDescending<Pair<PatchInfo, Boolean>> { (patch, _) ->\n        patch.name in newPatchNames\n    }.thenBy { (patch, _) -> patch.name }\n\n    val (universal, specific) = patches.partition { (patch, _) -> patch.isUniversal }\n    PatchSections(\n        specific = specific.sortedWith(displayOrder),\n        universal = universal.sortedWith(displayOrder)\n    )\n}\n\n",
        "\n/**\n * Splits and orders one bundle's patches for display. New patches float to the top of each\n * group; within a group the order is alphabetical. Universal favorite patches float to the top of universal patches.\n */\n@Composable\nprivate fun rememberPatchSections(\n    patches: List<Pair<PatchInfo, Boolean>>,\n    newPatchNames: Set<String>,\n    favoritePatches: Set<String> = emptySet()\n): PatchSections = remember(patches, newPatchNames, favoritePatches) {\n    val specificDisplayOrder = compareByDescending<Pair<PatchInfo, Boolean>> { (patch, _) ->\n        patch.name in newPatchNames\n    }.thenBy { (patch, _) -> patch.displayName }\n\n    val universalDisplayOrder = compareByDescending<Pair<PatchInfo, Boolean>> { (patch, _) ->\n        patch.name in favoritePatches || patch.displayName in favoritePatches\n    }.thenByDescending { (patch, _) ->\n        patch.name in newPatchNames\n    }.thenBy { (patch, _) -> patch.displayName }\n\n    val (universal, specific) = patches.partition { (patch, _) -> patch.isUniversal }\n    PatchSections(\n        specific = specific.sortedWith(specificDisplayOrder),\n        universal = universal.sortedWith(universalDisplayOrder)\n    )\n}\n\n",
        '04-ExpertModeDialog hunk 7',
    ),
    (
        '    newPatchNames: Set<String>,\n    missingRequiredOptions: Set<String>,\n    lockStateOf: (PatchInfo) -> PatchLockState,\n    onToggle: (String) -> Unit,\n    onConfigureOptions: (PatchInfo) -> Unit\n) = patchSectionRows(\n    sectionKey = bundleUid,\n',
        '    newPatchNames: Set<String>,\n    missingRequiredOptions: Set<String>,\n    lockStateOf: (PatchInfo) -> PatchLockState,\n    favoritePatches: Set<String> = emptySet(),\n    onToggle: (String) -> Unit,\n    onToggleFavorite: (PatchInfo) -> Unit,\n    onConfigureOptions: (PatchInfo) -> Unit\n) = patchSectionRows(\n    sectionKey = bundleUid,\n',
        '04-ExpertModeDialog hunk 8',
    ),
    (
        '        buildsClone = patch.renamesByDefault,\n        hasRequiredOptionsMissing = patch.name in missingRequiredOptions,\n        lockState = lockStateOf(patch),\n        onToggle = { onToggle(patch.name) },\n        onConfigureOptions = { onConfigureOptions(patch) },\n        hasOptions = !patch.options.isNullOrEmpty(),\n        modifier = Modifier.animatedListItem(this)\n',
        '        buildsClone = patch.renamesByDefault,\n        hasRequiredOptionsMissing = patch.name in missingRequiredOptions,\n        lockState = lockStateOf(patch),\n        isFavorite = patch.name in favoritePatches || patch.displayName in favoritePatches,\n        onToggle = { onToggle(patch.name) },\n        onToggleFavorite = { onToggleFavorite(patch) },\n        onConfigureOptions = { onConfigureOptions(patch) },\n        hasOptions = !patch.options.isNullOrEmpty(),\n        modifier = Modifier.animatedListItem(this)\n',
        '04-ExpertModeDialog hunk 9',
    ),
]

_EXPERT_MODE_DIALOG_DEV = [
    (
        'import app.morphe.manager.R\nimport app.morphe.manager.patcher.patch.PatchBundleInfo',
        'import app.morphe.manager.R\nimport app.morphe.manager.domain.manager.PreferencesManager\nimport app.morphe.manager.patcher.patch.PatchBundleInfo',
        'import PreferencesManager',
    ),
    (
        'import kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch',
        'import kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\nimport org.koin.compose.koinInject',
        'import koinInject',
    ),
    (
        '    val context = LocalContext.current\n\n    // Both markers are keyed by bundle,',
        '    val context = LocalContext.current\n    val prefs: PreferencesManager = koinInject()\n    val favoritePatches by prefs.favoriteUniversalPatches.getAsState()\n    val coroutineScope = rememberCoroutineScope()\n\n    val onToggleFavoritePatch: (PatchInfo) -> Unit = { patch ->\n        val patchName = patch.name\n        val isFav = patchName in favoritePatches || patch.displayName in favoritePatches\n        val newFavorites = if (isFav) {\n            favoritePatches - patchName - patch.displayName\n        } else {\n            favoritePatches + patchName\n        }\n        coroutineScope.launch {\n            prefs.favoriteUniversalPatches.update(newFavorites)\n        }\n        val message = if (isFav) {\n            context.getString(R.string.expert_mode_favorite_removed, patch.displayName)\n        } else {\n            context.getString(R.string.expert_mode_favorite_added, patch.displayName)\n        }\n        context.toast(message)\n    }\n\n    // Both markers are keyed by bundle,',
        'favorites state + toggle logic',
    ),
    (
        'private fun rememberPatchSections(\n    patches: List<Pair<PatchInfo, Boolean>>,\n    newPatchNames: Set<String>\n): PatchSections = remember(patches, newPatchNames) {\n    val displayOrder = compareByDescending<Pair<PatchInfo, Boolean>> { (patch, _) ->\n        patch.name in newPatchNames\n    }.thenBy { (patch, _) -> patch.name }\n\n    val (universal, specific) = patches.partition { (patch, _) -> patch.isUniversal }\n    PatchSections(\n        specific = specific.sortedWith(displayOrder),\n        universal = universal.sortedWith(displayOrder)\n    )\n}',
        'private fun rememberPatchSections(\n    patches: List<Pair<PatchInfo, Boolean>>,\n    newPatchNames: Set<String>,\n    favoritePatches: Set<String> = emptySet()\n): PatchSections = remember(patches, newPatchNames, favoritePatches) {\n    val specificDisplayOrder = compareByDescending<Pair<PatchInfo, Boolean>> { (patch, _) ->\n        patch.name in newPatchNames\n    }.thenBy { (patch, _) -> patch.displayName }\n\n    val universalDisplayOrder = compareByDescending<Pair<PatchInfo, Boolean>> { (patch, _) ->\n        patch.name in favoritePatches || patch.displayName in favoritePatches\n    }.thenByDescending { (patch, _) ->\n        patch.name in newPatchNames\n    }.thenBy { (patch, _) -> patch.displayName }\n\n    val (universal, specific) = patches.partition { (patch, _) -> patch.isUniversal }\n    PatchSections(\n        specific = specific.sortedWith(specificDisplayOrder),\n        universal = universal.sortedWith(universalDisplayOrder)\n    )\n}',
        'rememberPatchSections sort logic',
    ),
    (
        '    newPatchNames: Set<String>,\n    missingRequiredOptions: Set<String>,\n    customOptions: Set<String>,\n    lockStateOf: (PatchInfo) -> PatchLockState,\n    onToggle: (String) -> Unit,\n    onConfigureOptions: (PatchInfo) -> Unit\n) = patchSectionRows(',
        '    newPatchNames: Set<String>,\n    missingRequiredOptions: Set<String>,\n    customOptions: Set<String>,\n    favoritePatches: Set<String> = emptySet(),\n    lockStateOf: (PatchInfo) -> PatchLockState,\n    onToggle: (String) -> Unit,\n    onToggleFavorite: (PatchInfo) -> Unit = {},\n    onConfigureOptions: (PatchInfo) -> Unit\n) = patchSectionRows(',
        'patchSections signature',
    ),
    (
        '        hasRequiredOptionsMissing = patch.name in missingRequiredOptions,\n        hasCustomOptions = patch.name in customOptions,\n        lockState = lockStateOf(patch),\n        onToggle = { onToggle(patch.name) },\n        onConfigureOptions = { onConfigureOptions(patch) },',
        '        hasRequiredOptionsMissing = patch.name in missingRequiredOptions,\n        hasCustomOptions = patch.name in customOptions,\n        isFavorite = patch.name in favoritePatches || patch.displayName in favoritePatches,\n        lockState = lockStateOf(patch),\n        onToggle = { onToggle(patch.name) },\n        onToggleFavorite = { onToggleFavorite(patch) },\n        onConfigureOptions = { onConfigureOptions(patch) },',
        'PatchCard call wiring',
    ),
    (
        '                    val sections = rememberPatchSections(\n                        patches = filteredPatches,\n                        newPatchNames = newPatches[bundle.uid] ?: emptySet()\n                    )',
        '                    val sections = rememberPatchSections(\n                        patches = filteredPatches,\n                        newPatchNames = newPatches[bundle.uid] ?: emptySet(),\n                        favoritePatches = favoritePatches\n                    )',
        'call site 1: rememberPatchSections',
    ),
    (
        '                                customOptions = patchesWithCustomOptions[bundle.uid] ?: emptySet(),\n                                lockStateOf = lockStateOf,\n                                onToggle = { patchActions.onPatchToggle(bundle.uid, it) },',
        '                                customOptions = patchesWithCustomOptions[bundle.uid] ?: emptySet(),\n                                favoritePatches = favoritePatches,\n                                lockStateOf = lockStateOf,\n                                onToggle = { patchActions.onPatchToggle(bundle.uid, it) },\n                                onToggleFavorite = onToggleFavoritePatch,',
        'call site 1: patchSections',
    ),
    (
        '                                val sections = rememberPatchSections(\n                                    patches = patches,\n                                    newPatchNames = newPatches[bundle.uid] ?: emptySet()\n                                )',
        '                                val sections = rememberPatchSections(\n                                    patches = patches,\n                                    newPatchNames = newPatches[bundle.uid] ?: emptySet(),\n                                    favoritePatches = favoritePatches\n                                )',
        'call site 2: rememberPatchSections',
    ),
    (
        '                                        customOptions = patchesWithCustomOptions[bundle.uid] ?: emptySet(),\n                                        lockStateOf = lockStateOf,\n                                        onToggle = { patchActions.onPatchToggle(bundle.uid, it) },',
        '                                        customOptions = patchesWithCustomOptions[bundle.uid] ?: emptySet(),\n                                        favoritePatches = favoritePatches,\n                                        lockStateOf = lockStateOf,\n                                        onToggle = { patchActions.onPatchToggle(bundle.uid, it) },\n                                        onToggleFavorite = onToggleFavoritePatch,',
        'call site 2: patchSections',
    ),
]

# path (relative to repo root) -> one or more strategies.
# A strategy is a list of (old, new, description) edits, all independent
# (non-overlapping) within that strategy. Multiple strategies exist for
# files whose surrounding code differs between branches (main vs dev);
# the first fully-compatible strategy is used.
FILES = {
    "app/src/main/java/app/morphe/manager/ui/screen/shared/AppDialog.kt": [_APP_DIALOG],
    "app/src/main/java/app/morphe/manager/domain/manager/PreferencesManager.kt": [_PREFERENCES_MANAGER],
    "app/src/main/java/app/morphe/manager/ui/screen/shared/SettingComponents.kt": [_SETTING_COMPONENTS],
    "app/src/main/res/values/strings.xml": [_STRINGS],
    "app/src/main/java/app/morphe/manager/ui/screen/home/ExpertPatchCard.kt": [
        _EXPERT_PATCH_CARD_MAIN,
        _EXPERT_PATCH_CARD_DEV,
    ],
    "app/src/main/java/app/morphe/manager/ui/screen/home/ExpertModeDialog.kt": [
        _EXPERT_MODE_DIALOG_MAIN,
        _EXPERT_MODE_DIALOG_DEV,
    ],
}

BOM = "\ufeff"


def strategy_status(content, strategy):
    """
    Check whether every edit in strategy is either already applied or
    cleanly pending against content. Returns (compatible, per_edit_status)
    where per_edit_status is a list of "applied" / "pending" / "conflict"
    aligned with strategy.
    """
    statuses = []
    for old, new, desc in strategy:
        if new in content:
            statuses.append("applied")
        elif content.count(old) == 1:
            statuses.append("pending")
        else:
            statuses.append("conflict")
    compatible = all(s != "conflict" for s in statuses)
    return compatible, statuses


def pick_strategy(content, strategies):
    """Return (strategy, statuses) for the first compatible strategy, or (None, None)."""
    for strategy in strategies:
        compatible, statuses = strategy_status(content, strategy)
        if compatible:
            return strategy, statuses
    return None, None


def apply_file(rel_path, root, strategies, check_only):
    path = root / rel_path
    if not path.is_file():
        print(f"[!] {rel_path}: file not found", file=sys.stderr)
        return "failed"

    content = path.read_text(encoding="utf-8")
    strategy, statuses = pick_strategy(content, strategies)

    if strategy is None:
        print(f"[!] {rel_path}: no matching anchors in any known branch variant", file=sys.stderr)
        for i, s in enumerate(strategies):
            _, statuses = strategy_status(content, s)
            bad = [desc for (old, new, desc), st in zip(s, statuses) if st == "conflict"]
            print(f"    variant {i}: {len(bad)}/{len(s)} edits unmatched: {', '.join(bad)}", file=sys.stderr)
        return "failed"

    if all(s == "applied" for s in statuses):
        print(f"[=] {rel_path}: already applied")
        return "skipped"

    if check_only:
        pending = [desc for (old, new, desc), s in zip(strategy, statuses) if s == "pending"]
        print(f"[ ] {rel_path}: pending ({len(pending)}/{len(strategy)} edits)")
        return "pending"

    strip_bom = content.startswith(BOM)
    if strip_bom:
        content = content[len(BOM):]

    for (old, new, desc), status in zip(strategy, statuses):
        if status == "applied":
            continue
        if content.count(old) != 1:
            print(f"[!] {rel_path}: '{desc}' anchor no longer unique after prior edits", file=sys.stderr)
            return "failed"
        content = content.replace(old, new, 1)

    path.write_text(content, encoding="utf-8")
    print(f"[+] {rel_path}: applied")
    return "applied"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("source", nargs="?", default=".", help="morphe-manager repo root (default: cwd)")
    ap.add_argument("--check", action="store_true", help="dry-run only, no changes written")
    args = ap.parse_args()

    root = pathlib.Path(args.source).resolve()
    if not (root / "app" / "src" / "main").is_dir():
        print(f"error: {root} doesn't look like a morphe-manager checkout (no app/src/main)", file=sys.stderr)
        sys.exit(1)

    results = {rel: apply_file(rel, root, strategies, args.check) for rel, strategies in FILES.items()}

    failed = [r for r, s in results.items() if s == "failed"]
    if failed:
        print(f"\n{len(failed)} file(s) failed: {', '.join(failed)}", file=sys.stderr)
        sys.exit(1)

    if args.check:
        pending = [r for r, s in results.items() if s == "pending"]
        sys.exit(1 if pending else 0)

    print("\ndone.")


if __name__ == "__main__":
    main()
