/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import android.app.Activity
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppButtonPreferences
import app.morphe.manager.ui.screen.settings.appearance.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.theme.SeasonalEvent
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.ui.theme.ThemeStyle
import app.morphe.manager.ui.theme.resolveThemeStyle
import app.morphe.manager.ui.theme.toUiScalePercent
import app.morphe.manager.ui.viewmodel.RandomInterval
import app.morphe.manager.ui.viewmodel.ThemeSettingsViewModel
import app.morphe.manager.util.AppCardColorDefaults
import app.morphe.manager.util.AppCardColorMode
import app.morphe.manager.util.AppLocale
import app.morphe.manager.util.MORPHE_WEBSITE_URL
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

/**
 * The appearance section of the settings: language and scale, theme, colors and the home screen.
 */
@Composable
fun AppearanceSettings(
    themeViewModel: ThemeSettingsViewModel = koinViewModel(),
    homeAppButtonPrefs: HomeAppButtonPreferences = koinInject()
) {
    val scope = rememberCoroutineScope()
    val theme by themeViewModel.prefs.theme.getAsState()
    val themeStyle by themeViewModel.prefs.themeStyle.getAsState()
    val pureBlackTheme by themeViewModel.prefs.pureBlackTheme.getAsState()
    val customAccentColorHex by themeViewModel.prefs.customAccentColor.getAsState()
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val appLanguage by AppLocale.selected.collectAsStateWithLifecycle()
    val showGreetingPhrases by themeViewModel.prefs.showGreetingPhrases.getAsState()
    val showRepatchNotice by themeViewModel.prefs.showRepatchNotice.getAsState()
    val colorAccents by themeViewModel.prefs.colorAccents.getAsState()
    val outlines by themeViewModel.prefs.outlines.getAsState()
    val appCardColorMode by themeViewModel.prefs.appCardColorMode.getAsState()
    val customAppCardColors by themeViewModel.prefs.customAppCardColors.getAsState()
    val showSortButton by homeAppButtonPrefs.showSortButton.collectAsStateWithLifecycle()
    val backgroundType by themeViewModel.prefs.backgroundType.getAsState()
    val enableParallax by themeViewModel.prefs.enableBackgroundParallax.getAsState()
    val randomInterval by themeViewModel.prefs.randomBackgroundInterval.getAsState()
    val matrixUnlocked by themeViewModel.prefs.matrixBackgroundUnlocked.getAsState()
    val seasonalThemes by themeViewModel.prefs.seasonalThemes.getAsState()
    val seasonalEvent = remember { SeasonalEvent.on() }
    val resolvedRandomBackground =
        if (backgroundType == BackgroundType.RANDOM) rememberRandomBackground(themeViewModel.prefs) else null
    val effectiveThemeStyle = resolveThemeStyle(themeStyle, supportsDynamicColor)
    val showAppCardColorSetting = effectiveThemeStyle != ThemeStyle.MONOCHROME

    val uiScale by themeViewModel.prefs.uiScale.getAsState()

    val showLanguageDialog = remember { mutableStateOf(false) }
    val showUiScaleDialog = remember { mutableStateOf(false) }
    val showTranslationInfoDialog = remember { mutableStateOf(false) }
    val showAppCardColorDialog = remember { mutableStateOf(false) }
    val showBackgroundDialog = remember { mutableStateOf(false) }
    val appCardColorValues = remember(customAppCardColors) {
        AppCardColorDefaults.decodeColorValues(customAppCardColors)
    }
    val supportsPureBlack = theme != Theme.LIGHT

    LaunchedEffect(supportsPureBlack, pureBlackTheme) {
        if (!supportsPureBlack && pureBlackTheme) {
            themeViewModel.setPureBlackTheme(false)
        }
    }

    LaunchedEffect(showAppCardColorSetting) {
        if (!showAppCardColorSetting) showAppCardColorDialog.value = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)) {
        LanguageAndDisplaySection(
            appLanguage = appLanguage,
            uiScale = uiScale,
            onLanguageClick = { showTranslationInfoDialog.value = true },
            onUiScaleClick = { showUiScaleDialog.value = true }
        )

        ThemeSection(
            theme = theme,
            themeStyle = effectiveThemeStyle,
            supportsDynamicColor = supportsDynamicColor,
            pureBlackTheme = pureBlackTheme,
            supportsPureBlack = supportsPureBlack,
            onThemeSelected = themeViewModel::setThemeMode,
            onStyleSelected = themeViewModel::setThemeStyle,
            onPureBlackToggle = { themeViewModel.setPureBlackTheme(!pureBlackTheme) },
            colorAccents = colorAccents,
            onColorAccentsToggle = { themeViewModel.toggleColorAccents(colorAccents) },
            outlines = outlines,
            onOutlinesToggle = { themeViewModel.toggleOutlines(outlines) },
            backgroundType = backgroundType,
            randomInterval = randomInterval,
            onBackgroundClick = { showBackgroundDialog.value = true },
            accentColorHex = customAccentColorHex,
            onAccentSelected = themeViewModel::setCustomAccentColor
        )

        HomeScreenSection(
            appCardColorMode = appCardColorMode,
            showAppCardColors = showAppCardColorSetting,
            onAppCardColorsClick = { showAppCardColorDialog.value = true },
            showGreetingPhrases = showGreetingPhrases,
            showRepatchNotice = showRepatchNotice,
            showSortButton = showSortButton,
            onGreetingPhrasesToggle = { themeViewModel.toggleShowGreetingPhrases(showGreetingPhrases) },
            onRepatchNoticeToggle = { themeViewModel.toggleShowRepatchNotice(showRepatchNotice) },
            onSortButtonToggle = { homeAppButtonPrefs.setShowSortButton(!showSortButton) }
        )
    }

    if (showBackgroundDialog.value) {
        BackgroundPickerDialog(
            selectedBackground = backgroundType,
            onBackgroundSelected = themeViewModel::setBackgroundType,
            selectedInterval = randomInterval,
            onIntervalSelected = themeViewModel::setRandomInterval,
            onDismiss = { showBackgroundDialog.value = false },
            resolvedRandomBackground = resolvedRandomBackground,
            enableParallax = enableParallax,
            onParallaxToggle = { themeViewModel.toggleBackgroundParallax(enableParallax) },
            seasonalThemes = seasonalThemes,
            seasonalEvent = seasonalEvent,
            onSeasonalThemesToggle = { themeViewModel.toggleSeasonalThemes(seasonalThemes) },
            matrixUnlocked = matrixUnlocked
        )
    }

    // App card color dialog
    AnimatedVisibility(
        visible = showAppCardColorDialog.value && showAppCardColorSetting,
        enter = Animations.fadeIn,
        exit = Animations.fadeOut
    ) {
        AppCardColorDialog(
            mode = appCardColorMode,
            accentColorHex = customAccentColorHex,
            startColorHex = appCardColorValues.startHex,
            middleColorHex = appCardColorValues.middleHex,
            endColorHex = appCardColorValues.endHex,
            solidColorHex = appCardColorValues.solidHex,
            onApply = themeViewModel::applyAppCardColors,
            onDismiss = { showAppCardColorDialog.value = false }
        )
    }

    // Interface scale dialog
    AnimatedVisibility(
        visible = showUiScaleDialog.value,
        enter = Animations.fadeIn,
        exit = Animations.fadeOut
    ) {
        UiScaleDialog(
            currentScale = uiScale,
            // The scale lives on the activity context, so it takes effect on the next attach.
            // To write has to land first, or that attach would read the previous value
            onApply = { scale ->
                scope.launch {
                    themeViewModel.setUiScale(scale).join()
                    (context as? Activity)?.recreate()
                }
            },
            onDismiss = { showUiScaleDialog.value = false }
        )
    }

    // Translation info dialog
    AnimatedVisibility(
        visible = showTranslationInfoDialog.value,
        enter = Animations.fadeIn,
        exit = Animations.fadeOut(if (showLanguageDialog.value) 0 else Defaults.ANIMATION_DURATION)
    ) {
        AppDialogWithLinks(
            title = stringResource(R.string.settings_appearance_translations_info_title),
            message = stringResource(
                R.string.settings_appearance_translations_info_text,
                stringResource(R.string.settings_appearance_translations_info_url)
            ),
            urlLink = "$MORPHE_WEBSITE_URL/translate",
            onDismiss = {
                showTranslationInfoDialog.value = false
                scope.launch {
                    delay(50.milliseconds)
                    showLanguageDialog.value = true
                }
            }
        )
    }

    // Language picker dialog
    AnimatedVisibility(
        visible = showLanguageDialog.value,
        enter = Animations.fadeIn,
        exit = Animations.fadeOut
    ) {
        LanguagePickerDialog(
            currentLanguage = appLanguage,
            onLanguageSelected = { languageCode ->
                themeViewModel.setAppLanguage(languageCode)
                showLanguageDialog.value = false
            },
            onDismiss = { showLanguageDialog.value = false }
        )
    }
}

/**
 * How the app presents itself before any styling: the language it speaks and the scale it is
 * drawn at.
 */
@Composable
private fun LanguageAndDisplaySection(
    appLanguage: String,
    uiScale: Float,
    onLanguageClick: () -> Unit,
    onUiScaleClick: () -> Unit
) {
    val context = LocalContext.current
    val currentLanguage = remember(appLanguage, context) {
        LanguageRepository.getLanguage(appLanguage, context)
    }

    SectionTitle(
        text = stringResource(R.string.settings_appearance_language_and_display),
        icon = Icons.Outlined.Language
    )

    SettingsGroup {
        SettingsItem(
            onClick = onLanguageClick,
            title = stringResource(R.string.settings_appearance_app_language_current),
            subtitle = currentLanguage.displayName,
            leadingContent = {
                Box(
                    modifier = Modifier.size(Defaults.IconSize),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentLanguage.flag,
                        fontSize = 20.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        )
        SettingsItem(
            onClick = onUiScaleClick,
            title = stringResource(R.string.settings_appearance_ui_scale),
            subtitle = "${uiScale.toUiScalePercent()}%",
            leadingContent = { ThemedIcon(icon = Icons.Outlined.FormatSize) }
        )
    }
}

/**
 * Theme mode and color style, then how the rest of the manager is dressed: its accent, pure black,
 * the colors apps and sources wear, outlines, the animated background and the launcher icon.
 */
@Composable
private fun ThemeSection(
    theme: Theme,
    themeStyle: ThemeStyle,
    supportsDynamicColor: Boolean,
    pureBlackTheme: Boolean,
    supportsPureBlack: Boolean,
    onThemeSelected: (Theme) -> Unit,
    onStyleSelected: (ThemeStyle) -> Unit,
    onPureBlackToggle: () -> Unit,
    colorAccents: Boolean,
    onColorAccentsToggle: () -> Unit,
    outlines: Boolean,
    onOutlinesToggle: () -> Unit,
    backgroundType: BackgroundType,
    randomInterval: RandomInterval,
    onBackgroundClick: () -> Unit,
    accentColorHex: String?,
    onAccentSelected: (Color?) -> Unit
) {
    SectionTitle(
        text = stringResource(R.string.settings_appearance_theme),
        icon = Icons.Outlined.Palette
    )

    ThemeSelector(
        theme = theme,
        onThemeSelected = onThemeSelected,
        style = themeStyle,
        supportsDynamicColor = supportsDynamicColor,
        onStyleSelected = onStyleSelected
    )

    // Dynamic color derives the accent from the wallpaper, leaving nothing to pick here
    val showAccent = themeStyle != ThemeStyle.MATERIAL_YOU

    SettingsGroup {
        AnimatedVisibility(
            visible = showAccent,
            enter = Animations.expandFadeEnter,
            exit = Animations.shrinkFadeExit
        ) {
            SettingsItemCard(onClick = null) {
                AccentColorSelector(
                    selectedColorHex = accentColorHex,
                    onColorSelected = onAccentSelected,
                    dynamicColorEnabled = !showAccent
                )
            }
        }

        AnimatedVisibility(
            visible = supportsPureBlack,
            enter = Animations.expandFadeEnter,
            exit = Animations.shrinkFadeExit
        ) {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_appearance_pure_black),
                subtitle = stringResource(R.string.settings_appearance_pure_black_description),
                icon = Icons.Outlined.Contrast,
                checked = pureBlackTheme,
                onToggle = onPureBlackToggle
            )
        }

        // Monochrome already trades every app's and source's color for its own accent
        AnimatedVisibility(
            visible = themeStyle != ThemeStyle.MONOCHROME,
            enter = Animations.expandFadeEnter,
            exit = Animations.shrinkFadeExit
        ) {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_appearance_color_accents),
                subtitle = stringResource(R.string.settings_appearance_color_accents_description),
                icon = Icons.Outlined.FormatColorFill,
                checked = colorAccents,
                onToggle = onColorAccentsToggle
            )
        }

        SettingsSwitchItem(
            title = stringResource(R.string.settings_appearance_outlines),
            subtitle = stringResource(R.string.settings_appearance_outlines_description),
            icon = Icons.Outlined.BorderStyle,
            checked = outlines,
            onToggle = onOutlinesToggle
        )

        BackgroundSettingsItem(
            selectedBackground = backgroundType,
            selectedInterval = randomInterval,
            onClick = onBackgroundClick
        )

        AppIconSettingsItem()
    }
}

/**
 * How the home screen looks: the colors its app cards wear, then what it shows.
 *
 * @param showAppCardColors Whether the theme leaves app cards a color to pick, which monochrome does not.
 */
@Composable
private fun HomeScreenSection(
    appCardColorMode: AppCardColorMode,
    showAppCardColors: Boolean,
    onAppCardColorsClick: () -> Unit,
    showGreetingPhrases: Boolean,
    showRepatchNotice: Boolean,
    showSortButton: Boolean,
    onGreetingPhrasesToggle: () -> Unit,
    onRepatchNoticeToggle: () -> Unit,
    onSortButtonToggle: () -> Unit
) {
    SectionTitle(
        text = stringResource(R.string.settings_appearance_home_screen),
        icon = Icons.Outlined.Dashboard
    )

    SettingsGroup {
        AnimatedVisibility(
            visible = showAppCardColors,
            enter = Animations.expandFadeEnter,
            exit = Animations.shrinkFadeExit
        ) {
            SettingsItem(
                onClick = onAppCardColorsClick,
                title = stringResource(R.string.settings_appearance_app_card_colors),
                subtitle = stringResource(appCardColorMode.descriptionResId),
                leadingContent = { ThemedIcon(icon = Icons.Outlined.Style) }
            )
        }
        SettingsSwitchItem(
            title = stringResource(R.string.settings_appearance_greeting_phrases),
            subtitle = stringResource(R.string.settings_appearance_greeting_phrases_subtitle),
            icon = Icons.Outlined.ChatBubbleOutline,
            checked = showGreetingPhrases,
            onToggle = onGreetingPhrasesToggle
        )
        SettingsSwitchItem(
            title = stringResource(R.string.settings_appearance_repatch_notice),
            subtitle = stringResource(R.string.settings_appearance_repatch_notice_description),
            icon = Icons.Outlined.AutoFixHigh,
            checked = showRepatchNotice,
            onToggle = onRepatchNoticeToggle
        )
        SettingsSwitchItem(
            title = stringResource(R.string.settings_appearance_sort_button),
            subtitle = stringResource(R.string.settings_appearance_sort_button_description),
            icon = Icons.AutoMirrored.Outlined.Sort,
            checked = showSortButton,
            onToggle = onSortButtonToggle
        )
    }
}
