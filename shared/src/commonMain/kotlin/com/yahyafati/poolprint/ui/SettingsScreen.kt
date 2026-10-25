package com.yahyafati.poolprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

@Composable
fun SettingsScreen(model: SettingsModel, onClose: () -> Unit) {
    val state by model.state.collectAsState()
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onClose) { Text(stringResource(Res.string.action_done)) }
        }
        state.username?.let {
            Text(stringResource(Res.string.settings_username), style = MaterialTheme.typography.labelLarge)
            Text(it.value)
        }
        state.publicKey?.let { KeyInstructions(it) }

        Text(stringResource(Res.string.settings_host_key), style = MaterialTheme.typography.labelLarge)
        val pinned = state.hostKey
        if (pinned == null) {
            Text(stringResource(Res.string.settings_host_key_none))
        } else {
            Text(pinned.type, style = MaterialTheme.typography.bodySmall)
            SelectionContainer { Text(pinned.fingerprint, fontFamily = FontFamily.Monospace) }
            OutlinedButton(onClick = { model.ask(SettingsConfirm.ForgetHostKey) }) { Text(stringResource(Res.string.settings_forget_host_key)) }
        }
        Text(stringResource(Res.string.settings_discovery), style = MaterialTheme.typography.labelLarge)
        val updatedAt = state.discoveryUpdatedAt
        Text(
            if (updatedAt == null) stringResource(Res.string.settings_discovery_never)
            else stringResource(Res.string.settings_discovery_updated, state.discoveryPrinterCount, formatTimestamp(updatedAt)),
        )
        OutlinedButton(onClick = model::refreshDiscovery, enabled = state.discoveryRefresh != DiscoveryRefresh.Running) {
            Text(stringResource(if (state.discoveryRefresh == DiscoveryRefresh.Running) Res.string.settings_discovery_running else Res.string.settings_discovery_refresh))
        }
        when (state.discoveryRefresh) {
            DiscoveryRefresh.Updated -> Text(stringResource(Res.string.settings_discovery_done))
            DiscoveryRefresh.Unavailable -> Text(stringResource(Res.string.settings_discovery_failed))
            DiscoveryRefresh.NeedsTrust -> Text(stringResource(Res.string.settings_discovery_trust))
            else -> Unit
        }
        Text(stringResource(Res.string.settings_add_device), style = MaterialTheme.typography.labelLarge)
        AddDeviceSection(state.addDevice, model)
        OutlinedButton(onClick = { model.ask(SettingsConfirm.RegenerateKey) }) { Text(stringResource(Res.string.settings_regenerate)) }
        Text(stringResource(Res.string.settings_about), style = MaterialTheme.typography.bodySmall)
    }

    when (state.confirm) {
        SettingsConfirm.ForgetHostKey -> ConfirmDialog(
            title = stringResource(Res.string.settings_forget_confirm_title),
            body = stringResource(Res.string.settings_forget_confirm_body),
            action = stringResource(Res.string.settings_forget_confirm_action),
            onConfirm = model::confirmForgetHostKey,
            onDismiss = model::cancelConfirm,
        )
        SettingsConfirm.RegenerateKey -> ConfirmDialog(
            title = stringResource(Res.string.settings_regenerate_confirm_title),
            body = stringResource(Res.string.settings_regenerate_confirm_body),
            action = stringResource(Res.string.settings_regenerate_confirm_action),
            onConfirm = model::confirmRegenerateKey,
            onDismiss = model::cancelConfirm,
        )
        SettingsConfirm.None -> Unit
    }
}

@Composable
private fun AddDeviceSection(addDevice: AddDevice, model: SettingsModel) {
    when (addDevice) {
        AddDevice.Closed -> OutlinedButton(onClick = model::openAddDevice) { Text(stringResource(Res.string.settings_add_device_open)) }
        is AddDevice.Form -> {
            Text(stringResource(Res.string.settings_add_device_body))
            OutlinedTextField(
                value = addDevice.input,
                onValueChange = model::onAddDeviceInput,
                label = { Text(stringResource(Res.string.settings_add_device_label)) },
                isError = addDevice.invalid,
                supportingText = if (addDevice.invalid) { { Text(stringResource(Res.string.settings_add_device_invalid)) } } else null,
                minLines = 2,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = model::authorizeDevice, enabled = addDevice.input.isNotBlank()) {
                    Text(stringResource(Res.string.settings_add_device_action))
                }
                TextButton(onClick = model::closeAddDevice) { Text(stringResource(Res.string.action_cancel)) }
            }
        }
        AddDevice.Running -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(stringResource(Res.string.settings_add_device_running))
        }
        is AddDevice.Added -> {
            Text(stringResource(Res.string.settings_add_device_done, addDevice.comment), color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = model::closeAddDevice) { Text(stringResource(Res.string.action_done)) }
        }
        is AddDevice.Failed -> {
            FailureCard(addDevice.failure, onRetry = model::openAddDevice)
            TextButton(onClick = model::closeAddDevice) { Text(stringResource(Res.string.action_cancel)) }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
