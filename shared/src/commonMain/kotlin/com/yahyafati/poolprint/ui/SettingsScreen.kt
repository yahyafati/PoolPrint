package com.yahyafati.poolprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yahyafati.poolprint.service.FailureKind
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

@Composable
fun SettingsScreen(model: SettingsModel, onClose: () -> Unit) {
    val state by model.state.collectAsState()
    val addDevice = state.addDevice
    if (addDevice is AddDevice.Failed && addDevice.failure.kind == FailureKind.HostKeyChanged) {
        HostKeyChangedScreen(addDevice.failure, onClose = model::closeAddDevice)
        return
    }
    val c = PoolTheme.colors
    val t = PoolTheme.type
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val sharer = rememberTextSharer()
    PhoneScreen(
        topBar = { TopBar(stringResource(Res.string.settings_title), onBack = onClose) },
        overlay = {
            when (state.confirm) {
                SettingsConfirm.ForgetHostKey -> ConfirmPanel(stringResource(Res.string.settings_forget_confirm_title), model::cancelConfirm, danger = true) {
                    Text(stringResource(Res.string.settings_forget_confirm_body), style = t.bodySmall, color = c.onVariant)
                    ConfirmButtons(
                        stringResource(Res.string.settings_forget_confirm_action),
                        model::confirmForgetHostKey,
                        stringResource(Res.string.action_cancel),
                        model::cancelConfirm,
                    )
                }
                SettingsConfirm.RegenerateKey -> ConfirmPanel(stringResource(Res.string.settings_regenerate_confirm_title), model::cancelConfirm, danger = true) {
                    Text(stringResource(Res.string.settings_regenerate_confirm_body), style = t.bodySmall, color = c.onVariant)
                    ConfirmButtons(
                        stringResource(Res.string.settings_regenerate_confirm_action),
                        model::confirmRegenerateKey,
                        stringResource(Res.string.settings_regenerate_keep),
                        model::cancelConfirm,
                    )
                }
                SettingsConfirm.None -> Unit
            }
        },
    ) {
        Column(Modifier.gutter()) {
            SectionLabel(stringResource(Res.string.settings_account), Modifier.padding(top = 20.dp, bottom = 4.dp))
            ListRow {
                RowText(
                    stringResource(Res.string.settings_username),
                    Modifier.weight(1f),
                    subtitle = state.username?.value ?: "—",
                    subtitleStyle = t.mono.copy(fontSize = 14.sp, lineHeight = 20.sp),
                )
            }
            Rule(strong = false)

            Section(stringResource(Res.string.settings_device_key))
            state.publicKey?.let { key ->
                MonoBox(key)
                Row(Modifier.padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockButton(
                        stringResource(Res.string.action_copy),
                        { scope.launch { clipboard.setText(key) } },
                        Modifier.weight(1f),
                        kind = ButtonKind.Outline,
                        height = 44.dp,
                        icon = PoolIcons.Copy,
                    )
                    sharer?.let { share ->
                        BlockButton(
                            stringResource(Res.string.action_share),
                            { share(key) },
                            Modifier.weight(1f),
                            kind = ButtonKind.Outline,
                            height = 44.dp,
                            icon = PoolIcons.Share,
                        )
                    }
                }
            }
            ListRow(
                onClick = if (addDevice == AddDevice.Closed) model::openAddDevice else model::closeAddDevice,
                trailing = { Chevron(if (addDevice == AddDevice.Closed) "›" else "▴") },
            ) {
                RowText(stringResource(Res.string.settings_add_device), Modifier.weight(1f), subtitle = stringResource(Res.string.settings_add_device_hint))
            }
            if (addDevice != AddDevice.Closed) AddDeviceSection(addDevice, model)
            Rule(strong = false)
            ListRow(onClick = { model.ask(SettingsConfirm.RegenerateKey) }) {
                RowText(
                    stringResource(Res.string.settings_regenerate),
                    Modifier.weight(1f),
                    subtitle = stringResource(Res.string.settings_regenerate_hint),
                    titleColor = c.error,
                )
            }
            Rule(strong = false)

            Section(stringResource(Res.string.settings_server))
            val pinned = state.hostKey
            ListRow(
                minHeight = 72.dp,
                trailing = {
                    if (pinned != null) TextAction(stringResource(Res.string.settings_forget_host_key), { model.ask(SettingsConfirm.ForgetHostKey) })
                },
            ) {
                RowText(
                    stringResource(Res.string.settings_host_key),
                    Modifier.weight(1f),
                    subtitle = pinned?.let { "${it.type} · ${it.fingerprint}" } ?: stringResource(Res.string.settings_host_key_none),
                    subtitleStyle = if (pinned != null) t.monoSmall else t.caption,
                )
            }
            Rule(strong = false)

            Section(stringResource(Res.string.settings_printers))
            val running = state.discoveryRefresh == DiscoveryRefresh.Running
            ListRow(
                onClick = if (running) null else model::refreshDiscovery,
                trailing = { IconButton48(PoolIcons.Refresh, stringResource(Res.string.settings_discovery_refresh), model::refreshDiscovery, enabled = !running) },
            ) {
                RowText(stringResource(Res.string.settings_discovery_refresh), Modifier.weight(1f), subtitle = discoverySubtitle(state))
            }
            if (running) BarProgress(null)
            Rule(strong = false)

            Section(stringResource(Res.string.settings_about_title))
            ListRow {
                RowText(stringResource(Res.string.app_name), Modifier.weight(1f), subtitle = stringResource(Res.string.settings_about))
            }
        }
    }
}

/** A strong rule and an uppercase label opening a settings section. */
@Composable
private fun Section(title: String) {
    Rule(Modifier.padding(top = 20.dp))
    SectionLabel(title, Modifier.padding(top = 24.dp, bottom = 8.dp))
}

@Composable
private fun discoverySubtitle(state: SettingsState): String {
    val updatedAt = state.discoveryUpdatedAt
    val known = if (updatedAt == null) {
        stringResource(Res.string.settings_discovery_never)
    } else {
        stringResource(Res.string.settings_discovery_updated, state.discoveryPrinterCount, formatTimestamp(updatedAt))
    }
    val outcome = when (state.discoveryRefresh) {
        DiscoveryRefresh.Running -> stringResource(Res.string.settings_discovery_running)
        DiscoveryRefresh.Updated -> stringResource(Res.string.settings_discovery_done)
        DiscoveryRefresh.Unavailable -> stringResource(Res.string.settings_discovery_failed)
        DiscoveryRefresh.NeedsTrust -> stringResource(Res.string.settings_discovery_trust)
        DiscoveryRefresh.None -> null
    }
    return listOfNotNull(known, outcome).joinToString("\n")
}

/** Paste another device's public key and append it to `authorized_keys` over this device's login. */
@Composable
private fun AddDeviceSection(addDevice: AddDevice, model: SettingsModel) {
    val c = PoolTheme.colors
    val t = PoolTheme.type
    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (addDevice) {
            AddDevice.Closed -> Unit
            is AddDevice.Form -> {
                Text(stringResource(Res.string.settings_add_device_body), style = t.bodySmall, color = c.onVariant)
                PoolTextField(
                    value = addDevice.input,
                    onValueChange = model::onAddDeviceInput,
                    label = stringResource(Res.string.settings_add_device_label),
                    error = if (addDevice.invalid) stringResource(Res.string.settings_add_device_invalid) else null,
                    textStyle = t.monoSmall,
                    singleLine = false,
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    BlockButton(
                        stringResource(Res.string.settings_add_device_action),
                        model::authorizeDevice,
                        Modifier.weight(1f),
                        height = 44.dp,
                        trailing = "→",
                        enabled = addDevice.input.isNotBlank(),
                    )
                    TextAction(stringResource(Res.string.action_cancel), model::closeAddDevice, color = c.onVariant, style = t.buttonSmall)
                }
            }
            AddDevice.Running -> {
                BarProgress(null)
                Text(stringResource(Res.string.settings_add_device_running), style = t.bodySmall, color = c.onVariant)
            }
            is AddDevice.Added -> {
                Text(stringResource(Res.string.settings_add_device_done, addDevice.comment), style = t.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.primary)
                TextAction(stringResource(Res.string.action_done), model::closeAddDevice)
            }
            is AddDevice.Failed -> {
                FailureCard(addDevice.failure, onRetry = model::openAddDevice)
                TextAction(stringResource(Res.string.action_cancel), model::closeAddDevice, color = c.onVariant, style = t.buttonSmall)
            }
        }
    }
}
