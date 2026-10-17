/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import app.morphe.manager.R

/**
 * A part of the settings with too much to it for a row of the settings page, opened as a page of its own.
 *
 * @param keywordRes What the page holds, which a search of the settings page finds its row by.
 */
enum class SettingsSubpage(
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val icon: ImageVector,
    val keywordRes: List<Int>
) {
    PATCH_OPTIONS(
        R.string.settings_advanced_patch_options,
        R.string.settings_section_patch_options_summary,
        Icons.Outlined.Tune,
        listOf(
            R.string.settings_advanced_patch_options_custom_branding,
            R.string.settings_advanced_patch_options_custom_header
        )
    ),
    APPEARANCE(
        R.string.appearance,
        R.string.settings_section_appearance_summary,
        Icons.Outlined.Palette,
        listOf(
            R.string.settings_appearance_language_and_display,
            R.string.settings_appearance_ui_scale,
            R.string.settings_appearance_theme,
            R.string.settings_appearance_pure_black,
            R.string.settings_appearance_color_accents,
            R.string.settings_appearance_app_card_colors,
            R.string.settings_appearance_outlines,
            R.string.settings_appearance_background,
            R.string.settings_appearance_app_icon_selector_title,
            R.string.settings_appearance_home_screen,
            R.string.settings_appearance_greeting_phrases,
            R.string.settings_appearance_sort_button,
            R.string.settings_appearance_repatch_notice
        )
    )
}
