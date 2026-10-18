/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.advanced

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.patcher.runtime.*
import app.morphe.manager.ui.screen.settings.SettingsSubpage
import app.morphe.manager.ui.screen.settings.system.BatteryOptimizationItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.formatMebibytes
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/**
 * How the patcher runs: the process it runs in and the memory it is given there, whether it keeps
 * running in the background, how much of the app it leaves out of the APK, and whether other apps
 * may start it, on a page of their own, see [SettingsSubpage.PATCHER]. A change made here is saved
 * at once.
 */
@Composable
fun PatcherSettings(settingsViewModel: SettingsViewModel = koinViewModel()) {
    val prefs = settingsViewModel.prefs
    val stripUnusedNativeLibs by prefs.stripUnusedNativeLibs.getAsState()
    val externalBatchPatchEnabled by prefs.externalBatchPatchEnabled.getAsState()

    SettingsColumn {
        SettingsSection(
            title = stringResource(R.string.settings_system_process_runtime),
            icon = MaterialSymbols.Rounded.Memory
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ProcessRuntimeSettings(settingsViewModel)
            } else {
                ProcessRuntimeUnavailable()
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_section_patching),
            icon = MaterialSymbols.Rounded.Auto_fix_high
        ) {
            SettingsGroup {
                BatteryOptimizationItem()
                // Strip unused native libraries + filter split APKs for device
                SettingsSwitchItem(
                    checked = stripUnusedNativeLibs,
                    onToggle = { settingsViewModel.setStripUnusedNativeLibs(!stripUnusedNativeLibs) },
                    icon = MaterialSymbols.Rounded.Layers_clear,
                    title = stringResource(R.string.settings_advanced_strip_unused_libs),
                    subtitle = stringResource(R.string.settings_advanced_strip_unused_libs_description)
                )
                // Entry point other apps use to start a re-patch queue
                SettingsSwitchItem(
                    checked = externalBatchPatchEnabled,
                    onToggle = { settingsViewModel.toggleExternalBatchPatch(externalBatchPatchEnabled) },
                    icon = MaterialSymbols.Rounded.Api,
                    title = stringResource(R.string.settings_advanced_external_batch_patch),
                    subtitle = stringResource(R.string.settings_advanced_external_batch_patch_description)
                )
            }
        }
    }
}

/**
 * Whether patching runs in a process of its own, and the memory that process is given, picked on
 * a slider from what the device can spare. Only offered while the process runtime is on.
 */
@Composable
private fun ProcessRuntimeSettings(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val prefs = settingsViewModel.prefs
    val enabled by prefs.useProcessRuntime.getAsState()
    val memoryLimit by prefs.patcherProcessMemoryLimit.getAsState()
    val heapLimitIgnored by prefs.patcherHeapLimitIgnored.getAsState()

    val maxLimit = remember(context) { maxMemoryLimit(context) }
    // Clamped because the stored limit may come from an import made on a roomier device
    val sliderState = rememberSliderState(
        value = coerceMemoryLimit(context, memoryLimit).toFloat(),
        steps = (((maxLimit.toDouble() - PROCESS_RUNTIME_MEMORY_MINIMUM)
                / PROCESS_RUNTIME_MEMORY_STEP - 1)).toInt(),
        trackRange = PROCESS_RUNTIME_MEMORY_MINIMUM.toFloat()..maxLimit.toFloat()
    )
    val selectedLimit = sliderState.value.roundToInt()
    val memoryLimitTitle = stringResource(R.string.settings_system_memory_limit_subtitle)

    SettingsGroup {
        SettingsSwitchItem(
            checked = enabled,
            onToggle = { settingsViewModel.setProcessRuntime(!enabled) },
            icon = MaterialSymbols.Rounded.Memory,
            title = stringResource(R.string.settings_system_process_runtime_enable),
            subtitle = stringResource(R.string.settings_system_process_runtime_description)
        )

        FoldingVisibility(visible = enabled) {
            SearchableSetting(listOf(memoryLimitTitle)) {
                SettingsItemCard(onClick = null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Defaults.SpaceLarge),
                        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
                    ) {
                        InfoStatBox(
                            value = context.formatMebibytes(selectedLimit),
                            subtitle = memoryLimitTitle,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.padding(bottom = Defaults.SpaceSmall)
                        )
                        Slider(
                            state = sliderState,
                            // Passing `onValueChange` makes the slider fully controlled, so the
                            // thumb only moves once the new value is written back to the state
                            onValueChange = {
                                sliderState.value = it
                                // Saved here rather than from `onValueChangeFinished`, which a track
                                // tap fires in the same pointer event and would persist the pre-tap value
                                settingsViewModel.setMemoryLimit(it.roundToInt())
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        SliderScaleLabels(
                            start = context.formatMebibytes(PROCESS_RUNTIME_MEMORY_MINIMUM),
                            end = context.formatMebibytes(maxLimit)
                        )
                    }
                }
            }
        }
    }

    // One warning at a time. A device that ignores the limit makes the slider moot, and otherwise
    // both ends of the range have something to say
    val warning = when {
        heapLimitIgnored ->
            R.string.settings_system_process_runtime_heap_limit_ignored to SemanticTone.Warning

        selectedLimit < PROCESS_RUNTIME_MEMORY_LOW_WARNING ->
            R.string.settings_system_memory_limit_warning to SemanticTone.Error

        isExtendedMemoryLimit(selectedLimit) ->
            R.string.settings_system_memory_limit_high_warning to SemanticTone.Warning

        else -> null
    }
    // Held on to so the warning keeps its text and tone while it animates out
    var lastWarning by remember { mutableStateOf(warning) }
    warning?.let { lastWarning = it }

    FoldingVisibility(visible = enabled && warning != null) {
        lastWarning?.let { (text, tone) -> SettingsFooter(text = stringResource(text), tone = tone) }
    }
}

/** The process runtime greyed out, for a device on an Android too old to run it. */
@Composable
private fun ProcessRuntimeUnavailable() {
    val title = stringResource(R.string.settings_system_process_runtime)
    val description = stringResource(R.string.settings_system_process_runtime_description_not_available)
    SettingsGroup {
        SearchableSetting(listOf(title, description)) {
            SettingsItemCard(onClick = null) {
                IconTextRow(
                    modifier = Modifier.padding(Defaults.SpaceLarge),
                    leadingContent = {
                        ThemedIcon(
                            icon = MaterialSymbols.Rounded.Memory,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    },
                    title = title,
                    description = description
                )
            }
        }
    }
}
