/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val YOUTUBE = "com.google.android.youtube"

class CloneCardIdentityTest {
    private fun item(id: String, packageName: String = YOUTUBE) = HomeAppItem(
        id = id,
        packageName = packageName,
        displayName = "YouTube",
        gradientColors = emptyList(),
        installedApp = null,
        packageInfo = null,
        isPinnedByDefault = false,
        isInstalledOnDevice = false,
        isDeleted = false,
        isInstallStateNotPatched = false,
        isInstallStateUnknown = false,
        isInstallStatePending = false,
        savedApkFile = null,
        hasUpdate = false,
        patchCount = 0
    )

    @Test
    fun `the app's own card is not a clone`() {
        assertFalse(item(YOUTUBE).isClone)
    }

    @Test
    fun `a card under a package of its own is a clone`() {
        assertTrue(item("$YOUTUBE.morphe2").isClone)
    }

    @Test
    fun `a clone renamed outside the app's own namespace still counts as one`() {
        assertTrue(item("app.morphe.youtube").isClone)
    }
}
