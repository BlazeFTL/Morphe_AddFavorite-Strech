/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import app.morphe.manager.R

/**
 * Asks to confirm an action, the [message] saying what it does: plain text, or an
 * [AnnotatedString] for one with emphasis.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: CharSequence,
    primaryText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isPrimaryDestructive: Boolean = true,
    secondaryText: String = stringResource(android.R.string.cancel)
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        description = message,
        footer = {
            AppDialogButtonRow(
                primaryText = primaryText,
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = isPrimaryDestructive,
                secondaryText = secondaryText,
                onSecondaryClick = onDismiss
            )
        }
    )
}

/**
 * Asks before a download over a metered connection, where the provider may charge for the data.
 */
@Composable
fun MeteredDownloadDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.download),
                onPrimaryClick = onConfirm,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Notice(
            icon = Icons.Outlined.Warning,
            text = stringResource(R.string.download_confirmation_metered),
            tone = SemanticTone.Warning
        )
    }
}

/**
 * Dialog to show a message with a clickable link.
 *
 * @param title Dialog title
 * @param message Main message text, whose first address-like word becomes the link
 * @param urlLink URL to open in browser
 * @param onDismiss Callback when OK is pressed
 */
@Composable
fun AppDialogWithLinks(
    title: String,
    message: String,
    urlLink: String,
    onDismiss: () -> Unit
) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline
        )
    )

    // Set as the dialog's description, whose text opens the link itself when tapped
    val annotatedMessage = buildAnnotatedString {
        val linkMatch = Regex("""\S+\.\S+""").find(message)

        if (linkMatch == null) {
            append(message)
            return@buildAnnotatedString
        }

        val start = linkMatch.range.first
        val end = linkMatch.range.last + 1

        append(message.take(start))
        withLink(LinkAnnotation.Url(urlLink, linkStyles)) {
            append(message.substring(start, end))
        }
        append(message.substring(end))
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        description = annotatedMessage,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(android.R.string.ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}
