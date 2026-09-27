/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstalledAppPickerItem

private enum class AppFilter { All, UserOnly, SystemOnly }

/**
 * Dialog that shows all installed apps for the universal-patch flow.
 * User picks an app; its APK is extracted and sent through the patch pipeline.
 */
@Composable
fun InstalledAppPickerDialog(
    items: List<InstalledAppPickerItem>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (InstalledAppPickerItem) -> Unit
) {
    val search = rememberSearchFieldState()
    var appFilter by remember { mutableStateOf(AppFilter.UserOnly) }
    val filtered = remember(items, search.query, appFilter) {
        items
            .let { list ->
                when (appFilter) {
                    AppFilter.UserOnly -> list.filter { !it.isSystemApp }
                    AppFilter.SystemOnly -> list.filter { it.isSystemApp }
                    AppFilter.All -> list
                }
            }
            .let { list ->
                if (search.query.isBlank()) list
                else list.filter {
                    it.label.contains(search.query, ignoreCase = true) ||
                            it.packageName.contains(search.query, ignoreCase = true)
                }
            }
    }
    AppDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = true,
        padding = DialogPadding.Compact,
        scrollable = false,
        contentArrangement = Arrangement.Top,
        fillContentHeight = true,
        hideFooterWhileTyping = true,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        SearchFieldBackHandler(search)

        val (filterIcon, filterLabel) = when (appFilter) {
            AppFilter.All -> Icons.Outlined.FilterList to stringResource(R.string.home_installed_app_picker_filter_all)
            AppFilter.UserOnly -> Icons.Outlined.Person to stringResource(R.string.home_installed_app_picker_filter_user)
            AppFilter.SystemOnly -> Icons.Outlined.Android to stringResource(R.string.home_installed_app_picker_filter_system)
        }
        val accent = MaterialTheme.colorScheme.primary
        ListDialogHeader(
            icon = { modifier ->
                ListDialogHeaderIcon(icon = Icons.Outlined.Apps, color = accent, modifier = modifier)
            },
            title = stringResource(R.string.home_installed_app_picker_title),
            // The filter names itself here, so switching it says what the list now holds. A line
            // each, as the filter's name is a phrase that would otherwise break partway
            subtitle = listOf(
                pluralStringResource(R.plurals.home_category_app_count, filtered.size, filtered.size.toString()),
                filterLabel
            ).joinToString("\n"),
            subtitleLoading = isLoading,
            search = search,
            searchLabel = stringResource(R.string.home_search_apps),
            searchEnabled = !isLoading,
            accentColor = accent
        ) {
            TitleAction(
                icon = filterIcon,
                contentDescription = filterLabel,
                onClick = {
                    appFilter = when (appFilter) {
                        AppFilter.All -> AppFilter.UserOnly
                        AppFilter.UserOnly -> AppFilter.SystemOnly
                        AppFilter.SystemOnly -> AppFilter.All
                    }
                },
                style = TitleActionStyle.Toggle,
                active = appFilter != AppFilter.All
            )
        }

        val textColor = LocalDialogTextColor.current
        val secondaryColor = LocalDialogSecondaryTextColor.current

        DialogLazyList(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
            pinnedFirstRow = true,
            userScrollEnabled = !isLoading
        ) {
            // Kept while the field is closed, so its share of the spacing makes the gap under
            // the header
            stickyHeader(key = "search") {
                AppDialogSearchHeader(
                    visible = search.visible,
                    value = search.query,
                    onValueChange = { search.query = it },
                    label = stringResource(R.string.home_search_apps),
                    // Opaque, so rows scrolled under the gap stay hidden
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = Defaults.ItemSpacing)
                )
            }

            if (isLoading) {
                items(10) { ShimmerInstalledAppRow() }
            } else {
                if (filtered.isEmpty()) {
                    item(key = "empty_state") {
                        Box(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.home_installed_app_picker_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                items(filtered, key = { it.packageName }) { item ->
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sized to the three text lines beside it so the row reads as one block
                        AppIcon(
                            packageInfo = item.packageInfo,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                // Universal patches name no version, so nothing here
                                // checks the build code and nothing would act on it
                                text = "v${item.info.version}",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryColor.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
