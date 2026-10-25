package com.yahyafati.poolprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.service.FailureKind
import com.yahyafati.poolprint.service.UserFailure
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

@Composable
private fun message(failure: UserFailure): String = when (failure.kind) {
    FailureKind.NotConfigured -> stringResource(Res.string.error_not_configured)
    FailureKind.HostUnreachable -> stringResource(Res.string.error_host_unreachable)
    FailureKind.AuthRejected -> stringResource(Res.string.error_auth_rejected)
    FailureKind.PasswordRejected -> stringResource(Res.string.error_password_rejected)
    FailureKind.HostKeyChanged -> stringResource(Res.string.error_host_key_changed_body)
    FailureKind.Timeout -> stringResource(Res.string.error_timeout)
    FailureKind.UnknownPrinter -> stringResource(Res.string.error_unknown_printer, failure.printerName.orEmpty())
    FailureKind.QuotaExceeded -> stringResource(Res.string.error_quota)
    FailureKind.LprMissing -> stringResource(Res.string.error_lpr_missing)
    FailureKind.PrintFailed ->
        failure.exitCode?.let { stringResource(Res.string.error_print_failed_code, it) } ?: stringResource(Res.string.error_print_failed)
    FailureKind.CommandFailed ->
        failure.exitCode?.let { stringResource(Res.string.error_command_failed, it) } ?: stringResource(Res.string.error_protocol)
    FailureKind.UnexpectedOutput -> stringResource(Res.string.error_unexpected_output)
    FailureKind.Protocol -> stringResource(Res.string.error_protocol)
}

/** A catalogue error with a "Details" toggle for the raw stderr / protocol message. */
@Composable
fun FailureCard(
    failure: UserFailure,
    onRetry: (() -> Unit)?,
    onShowKey: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (failure.kind == FailureKind.HostKeyChanged) {
        HostKeyChangedWarning(failure, modifier)
        return
    }
    var showDetails by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message(failure), color = MaterialTheme.colorScheme.onErrorContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onRetry != null) Button(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                if (failure.kind == FailureKind.AuthRejected && onShowKey != null) {
                    OutlinedButton(onClick = onShowKey) { Text(stringResource(Res.string.action_show_key)) }
                }
                if (failure.details != null) {
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(stringResource(if (showDetails) Res.string.action_hide_details else Res.string.action_details))
                    }
                }
            }
            if (showDetails && failure.details != null) {
                SelectionContainer {
                    Text(failure.details, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

/** Full-width red warning. There is deliberately no "continue" action (docs/security.md). */
@Composable
fun HostKeyChangedWarning(failure: UserFailure, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.error_host_key_changed_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(Res.string.error_host_key_changed_body))
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.error_host_key_expected), style = MaterialTheme.typography.labelLarge)
                    Text(failure.expectedFingerprint.orEmpty(), fontFamily = FontFamily.Monospace)
                    Text(stringResource(Res.string.error_host_key_actual), style = MaterialTheme.typography.labelLarge)
                    Text(failure.actualFingerprint.orEmpty(), fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

/** First-connection prompt: shows the fingerprint and pins it only after an explicit tap. */
@Composable
fun TrustHostKeyCard(key: PinnedHostKey, onTrust: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.trust_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.trust_body))
            Text(stringResource(Res.string.trust_key_type, key.type), style = MaterialTheme.typography.labelLarge)
            SelectionContainer { Text(key.fingerprint, fontFamily = FontFamily.Monospace) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTrust) { Text(stringResource(Res.string.trust_accept)) }
                OutlinedButton(onClick = onDecline) { Text(stringResource(Res.string.trust_decline)) }
            }
        }
    }
}
