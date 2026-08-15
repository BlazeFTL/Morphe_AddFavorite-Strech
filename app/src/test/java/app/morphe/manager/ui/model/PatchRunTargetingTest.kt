/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val CAKE = "me.mycake"
private const val CLONE = "me.mycake.morphe"
private const val BUNDLE = 7
private const val CLONE_PATCH = "Clone app"
private const val OTHER_PATCH = "Enable Plus"

class ConfigurationKeyTest {
    @Test
    fun `a run aimed at no install reads what the app was last patched with`() {
        assertEquals(
            CAKE,
            configurationKey(
                originalPackageName = CAKE,
                repatchedPackageName = null,
                repatchedHasOwnConfiguration = false
            )
        )
    }

    @Test
    fun `rebuilding the app's own install reads the app`() {
        assertEquals(
            CAKE,
            configurationKey(
                originalPackageName = CAKE,
                repatchedPackageName = CAKE,
                repatchedHasOwnConfiguration = true
            )
        )
    }

    @Test
    fun `rebuilding a copy reads what that copy was built with`() {
        assertEquals(
            CLONE,
            configurationKey(
                originalPackageName = CAKE,
                repatchedPackageName = CLONE,
                repatchedHasOwnConfiguration = true
            )
        )
    }

    @Test
    fun `a copy with nothing saved of its own falls back to the app`() {
        assertEquals(
            CAKE,
            configurationKey(
                originalPackageName = CAKE,
                repatchedPackageName = CLONE,
                repatchedHasOwnConfiguration = false
            )
        )
    }
}

class PendingRenameTest {
    private fun rename(
        targetPackageName: String,
        selectedPatches: Set<String> = setOf(CLONE_PATCH),
        declaredName: String? = null
    ): PendingRename? = pendingRename(
        originalPackageName = CAKE,
        targetPackageName = targetPackageName,
        selection = mapOf(BUNDLE to selectedPatches),
        options = declaredName
            ?.let { mapOf(BUNDLE to mapOf(CLONE_PATCH to mapOf(PACKAGE_NAME_OPTION_KEY to it))) }
            .orEmpty(),
        declaresPackageName = { _, patchName -> patchName == CLONE_PATCH }
    )

    @Test
    fun `patches that build under the app's own name never rename it`() {
        assertNull(rename(targetPackageName = CAKE, selectedPatches = setOf(OTHER_PATCH)))
    }

    @Test
    fun `a named rename away from the app is reported with the name`() {
        assertEquals(
            PendingRename(CLONE),
            rename(targetPackageName = CAKE, declaredName = CLONE)
        )
    }

    @Test
    fun `a rename pointed back at the install being rebuilt is no rename`() {
        assertNull(rename(targetPackageName = CLONE, declaredName = CLONE))
    }

    @Test
    fun `an unnamed rename of the app itself is reported without a name`() {
        assertEquals(PendingRename(null), rename(targetPackageName = CAKE))
    }

    @Test
    fun `an unnamed rename while rebuilding a copy lands on that same copy`() {
        assertNull(rename(targetPackageName = CLONE))
    }

    @Test
    fun `a value that is no package name is the patch naming the result itself`() {
        assertEquals(PendingRename(null), rename(targetPackageName = CAKE, declaredName = "Default"))
        assertEquals(PendingRename(null), rename(targetPackageName = CAKE, declaredName = " "))
    }

    @Test
    fun `a copy rebuilt under a name of another copy is reported`() {
        assertEquals(
            PendingRename("me.mycake.morphe2"),
            rename(targetPackageName = CLONE, declaredName = "me.mycake.morphe2")
        )
    }
}
