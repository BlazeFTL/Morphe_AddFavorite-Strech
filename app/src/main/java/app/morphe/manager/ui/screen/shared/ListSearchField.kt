/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

/**
 * Search field for the app's lists, held by a dialog's [ListDialogHeader] or heading the list
 * itself: a filled pill in the shape of a Material search bar.
 *
 * @param requestFocus Opens the keyboard on first composition, for fields revealed by a toggle.
 */
@Composable
fun ListSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    requestFocus: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val textColor = dialogTextColor()
    val container = MaterialTheme.colorScheme.surfaceContainerHighest
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label) },
        leadingIcon = {
            // The placeholder already names the field, so the icon stays decorative
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = stringResource(R.string.clear)
                    )
                }
            }
        } else null,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            cursorColor = textColor,
            focusedLeadingIconColor = textColor.copy(alpha = 0.7f),
            unfocusedLeadingIconColor = textColor.copy(alpha = 0.7f),
            focusedTrailingIconColor = textColor.copy(alpha = 0.7f),
            unfocusedTrailingIconColor = textColor.copy(alpha = 0.7f),
            focusedPlaceholderColor = textColor.copy(alpha = 0.5f),
            unfocusedPlaceholderColor = textColor.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SearchFieldHeight)
            .focusRequester(focusRequester)
    )
}

/** Height of [ListSearchField], a Material search bar's. */
private val SearchFieldHeight = 56.dp
