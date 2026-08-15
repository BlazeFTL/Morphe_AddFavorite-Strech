package app.morphe.manager.ui.model

import android.content.pm.PackageInfo
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.morphe.manager.data.room.apps.installed.InstalledApp
import java.io.File

/**
 * One card on the home screen: either an app, or one of the installs it was cloned into.
 *
 * Cloning gives an app a second install under a package name of its own, so a card is identified
 * by [id] rather than by [packageName], which several cards of the same app share and which the
 * bundle data is keyed by.
 */
@Immutable
data class HomeAppItem(
    val id: String,
    val packageName: String,
    val displayName: String,
    val gradientColors: List<Color>,
    val installedApp: InstalledApp?,
    val packageInfo: PackageInfo?,
    val isPinnedByDefault: Boolean,
    val isInstalledOnDevice: Boolean,
    val isDeleted: Boolean,
    val isInstallStateNotPatched: Boolean,
    val isInstallStateUnknown: Boolean,
    val isInstallStatePending: Boolean,
    val savedApkFile: File?,
    val hasUpdate: Boolean,
    val patchCount: Int
) {
    val hasSavedCopy: Boolean get() = savedApkFile != null

    /**
     * Whether the pending update is worth surfacing in the UI.
     * Uninstalled apps keep their update flag but show the uninstalled state instead.
     */
    val showsUpdateBadge: Boolean get() = hasUpdate &&
            !isDeleted &&
            !isInstallStateNotPatched &&
            !isInstallStateUnknown &&
            !isInstallStatePending

    /** Whether this card is a clone rather than the app it was cloned from. */
    val isClone: Boolean get() = id != packageName
}

/** What a home screen card is about, before anything is read off the device to describe it. */
data class HomeAppSlot(
    val id: String,
    val packageName: String,
    val installedApp: InstalledApp?
)

/**
 * The cards one app is shown as: the app itself, followed by every install cloning gave a package
 * name of its own, ordered by that name so the list does not move around between reads.
 *
 * The app keeps a card of its own even once every install of it is a clone, because that card is
 * the only place another copy can be made from.
 */
fun homeAppSlots(packageName: String, records: List<InstalledApp>): List<HomeAppSlot> {
    val (own, clones) = records.partition { it.currentPackageName == packageName }

    return buildList {
        add(HomeAppSlot(packageName, packageName, own.firstOrNull()))
        clones.sortedBy { it.currentPackageName }.forEach { clone ->
            add(HomeAppSlot(clone.currentPackageName, packageName, clone))
        }
    }
}
