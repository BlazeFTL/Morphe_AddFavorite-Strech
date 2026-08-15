/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val YOUTUBE = "com.google.android.youtube"

class HomeAppSlotsTest {
    private fun record(currentPackageName: String) = InstalledApp(
        currentPackageName = currentPackageName,
        originalPackageName = YOUTUBE,
        version = "19.16.39",
        installType = InstallType.DEFAULT
    )

    @Test
    fun `an app nothing was patched from shows a single empty card`() {
        val slots = homeAppSlots(YOUTUBE, emptyList())

        assertEquals(listOf(HomeAppSlot(YOUTUBE, YOUTUBE, null)), slots)
    }

    @Test
    fun `an unrenamed install takes the app's own card`() {
        val installed = record(YOUTUBE)

        assertEquals(
            listOf(HomeAppSlot(YOUTUBE, YOUTUBE, installed)),
            homeAppSlots(YOUTUBE, listOf(installed))
        )
    }

    @Test
    fun `every clone gets a card of its own beside the app`() {
        val second = record("$YOUTUBE.morphe2")
        val first = record("$YOUTUBE.morphe")
        val own = record(YOUTUBE)

        assertEquals(
            listOf(
                HomeAppSlot(YOUTUBE, YOUTUBE, own),
                HomeAppSlot("$YOUTUBE.morphe", YOUTUBE, first),
                HomeAppSlot("$YOUTUBE.morphe2", YOUTUBE, second)
            ),
            homeAppSlots(YOUTUBE, listOf(second, first, own))
        )
    }

    @Test
    fun `the app keeps a card to be cloned from again once only clones are left`() {
        val clone = record("$YOUTUBE.morphe")

        val slots = homeAppSlots(YOUTUBE, listOf(clone))

        assertEquals(2, slots.size)
        assertNull(slots.first().installedApp)
        assertEquals(YOUTUBE, slots.first().id)
    }
}
