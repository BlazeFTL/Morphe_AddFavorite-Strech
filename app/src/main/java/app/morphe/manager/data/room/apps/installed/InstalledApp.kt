package app.morphe.manager.data.room.apps.installed

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.morphe.manager.R
import kotlinx.serialization.Serializable

enum class InstallType(val stringResource: Int) {
    DEFAULT(R.string.home_app_info_install_type_system_installer),
    PLAY_STORE(R.string.home_app_info_install_type_play_store),
    ROOT_PLAY_STORE(R.string.home_app_info_install_type_root_play_store),
    CUSTOM(R.string.home_app_info_install_type_custom_installer),
    MOUNT(R.string.mount),
    SAVED(R.string.saved),
    SHIZUKU(R.string.home_app_info_install_type_shizuku),
    SHIZUKU_PLAY_STORE(R.string.home_app_info_install_type_shizuku_play_store)
}

/**
 * Simplified payload for storing patch selection configuration.
 */
@Serializable
data class SelectionPayload(
    val bundles: List<BundleSelection>
) {
    /**
     * [bundleName] and [bundleVersion] are the source as it was when the app was patched. They
     * outlive its deletion, which cascades the applied patches away along with their attribution.
     */
    @Serializable
    data class BundleSelection(
        val bundleUid: Int,
        val patches: List<String>,
        val options: Map<String, Map<String, String>> = emptyMap(),
        val bundleName: String? = null,
        val bundleVersion: String? = null
    )
}

@Entity(tableName = "installed_app")
data class InstalledApp(
    @PrimaryKey
    @ColumnInfo(name = "current_package_name") val currentPackageName: String,
    @ColumnInfo(name = "original_package_name") val originalPackageName: String,
    @ColumnInfo(name = "version") val version: String,
    @ColumnInfo(name = "install_type") val installType: InstallType,
    @ColumnInfo(name = "selection_payload") val selectionPayload: SelectionPayload? = null,
    @ColumnInfo(name = "patched_at") val patchedAt: Long? = null,
    /**
     * The app's own name as it read at patch time. Records outlive the APKs and the bundle that
     * could describe them, and nothing else is left to show in their place but the package name.
     */
    @ColumnInfo(name = "app_label") val appLabel: String? = null
)

/**
 * Root mount binds the patched APK over the stock one, so it only applies while patching kept
 * the original package name. Apps renamed by patches (GmsCore builds) must use a regular install.
 */
val InstalledApp.supportsMount: Boolean
    get() = currentPackageName == originalPackageName
