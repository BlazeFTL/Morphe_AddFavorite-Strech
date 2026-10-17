/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.os.PowerManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.requestIgnoreBatteryOptimizations
import org.koin.androidx.compose.koinViewModel

/** What keeps the manager working in the background: the battery optimization exemption and notifications. */
@Composable
fun BackgroundSection(settingsViewModel: SettingsViewModel = koinViewModel()) {
    val context = LocalContext.current
    var showNotificationsDialog by remember { mutableStateOf(false) }

    if (showNotificationsDialog) {
        NotificationsDialog(
            settingsViewModel = settingsViewModel,
            onDismiss = { showNotificationsDialog = false }
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val pm = remember { context.getSystemService(PowerManager::class.java) }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    SettingsSection(
        title = stringResource(R.string.settings_system_background),
        icon = Icons.Outlined.PhoneAndroid
    ) {
        SettingsGroup {
            SettingsItem(
                onClick = {
                    context.requestIgnoreBatteryOptimizations()
                },
                title = stringResource(R.string.settings_system_battery_optimization),
                subtitle = stringResource(R.string.settings_system_battery_optimization_description),
                leadingContent = { ThemedIcon(icon = Icons.Outlined.BatterySaver) },
                statusContent = {
                    StatusCircleIcon(
                        icon = if (isIgnoringBatteryOptimizations) Icons.Outlined.Check else Icons.Outlined.Warning,
                        containerColor = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.primaryContainer
                        else SemanticTone.Warning.container,
                        contentColor = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.onPrimaryContainer
                        else SemanticTone.Warning.content
                    )
                }
            )

            SettingsItem(
                onClick = { showNotificationsDialog = true },
                title = stringResource(R.string.settings_system_notifications),
                subtitle = stringResource(R.string.settings_system_notifications_description),
                leadingContent = { ThemedIcon(icon = Icons.Outlined.NotificationsActive) }
            )
        }
    }
}
