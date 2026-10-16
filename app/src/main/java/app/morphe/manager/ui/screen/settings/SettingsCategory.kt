/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import app.morphe.manager.R

/** What a group of [SettingsCategory]s on the settings page is about, which heads the group. */
enum class SettingsCategoryGroup(@param:StringRes val titleRes: Int, val icon: ImageVector) {
    PATCHING(R.string.settings_section_patching, Icons.Outlined.AutoFixHigh),
    MANAGER(R.string.settings_group_manager, Icons.Outlined.Settings)
}

/** One category of the settings, listed on the settings page in its [group] and opened as a page of its own. */
enum class SettingsCategory(
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val icon: ImageVector,
    val group: SettingsCategoryGroup
) {
    PATCHES(R.string.patches, R.string.settings_section_patches_summary, Icons.Outlined.Extension, SettingsCategoryGroup.PATCHING),
    PATCHER(R.string.settings_advanced_patcher, R.string.settings_section_patcher_summary, Icons.Outlined.Speed, SettingsCategoryGroup.PATCHING),
    INSTALLER(R.string.installer, R.string.settings_section_installer_summary, Icons.Outlined.InstallMobile, SettingsCategoryGroup.PATCHING),
    APPEARANCE(R.string.appearance, R.string.settings_section_appearance_summary, Icons.Outlined.Palette, SettingsCategoryGroup.MANAGER),
    UPDATES(R.string.settings_advanced_updates, R.string.settings_section_updates_summary, Icons.Outlined.Update, SettingsCategoryGroup.MANAGER),
    BACKGROUND(R.string.settings_system_background, R.string.settings_section_background_summary, Icons.Outlined.NotificationsActive, SettingsCategoryGroup.MANAGER),
    FILES(R.string.settings_section_files, R.string.settings_section_files_summary, Icons.Outlined.Folder, SettingsCategoryGroup.MANAGER),
    ABOUT(R.string.settings_system_about, R.string.settings_section_about_summary, Icons.Outlined.Info, SettingsCategoryGroup.MANAGER)
}
