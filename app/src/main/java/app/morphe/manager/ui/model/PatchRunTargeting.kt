/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection

/**
 * Option key a patch declares the package name it builds under. The manager has no way to know
 * which patch renames an app, but the name that patch was given is right there to be read.
 */
const val PACKAGE_NAME_OPTION_KEY = "packageName"

/**
 * A run whose patches build the app under a package it was not aimed at, so it will install
 * beside the app rather than update it.
 *
 * @param resultPackageName The name the patches were given, or null when they were left to pick
 *   one themselves and only the patch knows what it will be.
 * @param replacesExisting Whether an install already answers to [resultPackageName], which is
 *   the case the user has most reason to hear about: that install is about to be rebuilt.
 */
data class RenameWarning(
    val targetPackageName: String,
    val resultPackageName: String?,
    val replacesExisting: Boolean
)

/** The rename [pendingRename] found, kept apart from what the manager then makes of it. */
internal data class PendingRename(val declaredPackageName: String?)

/**
 * The package a run reads its saved patches and options from.
 *
 * Patches belong to the install they were applied to, so rebuilding one reads what that install
 * was built with. Anything else reads the app itself: a run producing a separate install starts
 * from what the app was last patched with, and so does an install that predates configurations
 * of their own.
 */
internal fun configurationKey(
    originalPackageName: String,
    repatchedPackageName: String?,
    repatchedHasOwnConfiguration: Boolean
): String = when {
    repatchedPackageName == null || repatchedPackageName == originalPackageName -> originalPackageName
    repatchedHasOwnConfiguration -> repatchedPackageName
    else -> originalPackageName
}

/**
 * The rename [selection] will perform, or null whenever the result can be shown to answer to
 * [targetPackageName] after all.
 *
 * A value that is no package name at all is the patch's own default, which leaves the result
 * unnamed here: only the patch knows what it will pick. That is worth saying when the app itself
 * is being rebuilt, and worth nothing when a copy is, since the same default is what named that
 * copy to begin with and will name it again.
 *
 * @param declaresPackageName Whether the given patch is one that builds under a name of its own.
 */
internal fun pendingRename(
    originalPackageName: String,
    targetPackageName: String,
    selection: PatchSelection,
    options: Options,
    declaresPackageName: (bundleUid: Int, patchName: String) -> Boolean
): PendingRename? {
    val (bundleUid, patchName) = selection.entries.firstNotNullOfOrNull { (bundleUid, patchNames) ->
        patchNames.firstOrNull { declaresPackageName(bundleUid, it) }?.let { bundleUid to it }
    } ?: return null

    val declaredName = (options[bundleUid]?.get(patchName)?.get(PACKAGE_NAME_OPTION_KEY) as? String)
        ?.takeIf { it.isNotBlank() && it.contains('.') }

    return when (declaredName) {
        // The user pointed the rename back at the install being rebuilt, so nothing moves
        targetPackageName -> null

        // Rebuilding a copy is how that copy got its name, and it lands on the same one again
        null -> PendingRename(null).takeIf { targetPackageName == originalPackageName }

        else -> PendingRename(declaredName)
    }
}
