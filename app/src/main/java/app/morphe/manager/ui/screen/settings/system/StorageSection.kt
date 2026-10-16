/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*

/** What the manager keeps on the device, opening the storage management screen. */
@Composable
fun StorageSection() {
    var showStorageDialog by remember { mutableStateOf(false) }

    if (showStorageDialog) {
        StorageManagementDialog(onDismissRequest = { showStorageDialog = false })
    }

    Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)) {
        SectionTitle(
            text = stringResource(R.string.settings_section_storage),
            icon = Icons.Outlined.FolderCopy
        )

        SettingsGroup {
            SettingsItem(
                onClick = { showStorageDialog = true },
                title = stringResource(R.string.settings_system_storage_management_title),
                subtitle = stringResource(R.string.settings_system_storage_management_description),
                leadingContent = { ThemedIcon(icon = Icons.Outlined.Storage) }
            )
        }
    }
}
