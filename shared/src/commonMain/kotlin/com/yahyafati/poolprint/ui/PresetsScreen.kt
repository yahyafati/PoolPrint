package com.yahyafati.poolprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.settings.Preset
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

@Composable
fun PresetsScreen(model: PresetsModel, onClose: () -> Unit) {
    val state by model.state.collectAsState()
    var renaming by remember { mutableStateOf<Preset?>(null) }
    var deleting by remember { mutableStateOf<Preset?>(null) }
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.presets_title), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onClose) { Text(stringResource(Res.string.action_done)) }
        }
        if (state.loaded && state.presets.isEmpty()) Text(stringResource(Res.string.presets_empty))
        for (preset in state.presets) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(preset.name, style = MaterialTheme.typography.titleMedium)
                    Text(presetSummary(preset), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { renaming = preset }) { Text(stringResource(Res.string.action_rename)) }
                        TextButton(onClick = { deleting = preset }) { Text(stringResource(Res.string.action_delete)) }
                    }
                }
            }
        }
    }
    renaming?.let { RenameDialog(model, it, onDismiss = { renaming = null }) }
    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(Res.string.preset_delete_title)) },
            text = { Text(stringResource(Res.string.preset_delete_body, preset.name)) },
            confirmButton = { TextButton(onClick = { model.delete(preset.id); deleting = null }) { Text(stringResource(Res.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun RenameDialog(model: PresetsModel, preset: Preset, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(preset.name) }
    var error by remember { mutableStateOf<ValidationError?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.preset_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text(stringResource(Res.string.preset_name_label)) },
                supportingText = error?.let { { Text(presetErrorText(it)) } },
                isError = error != null,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val result = model.rename(preset.id, name)
                    if (result == null) onDismiss() else error = result
                }
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun presetSummary(preset: Preset): String {
    val s = preset.settings
    val duplex = when (Duplex.entries.firstOrNull { it.name == s.duplex }) {
        Duplex.LONG_EDGE -> stringResource(Res.string.print_duplex_long)
        Duplex.SHORT_EDGE -> stringResource(Res.string.print_duplex_short)
        else -> stringResource(Res.string.print_duplex_off)
    }
    val up = PagesPerSheet.entries.firstOrNull { it.count == s.pagesPerSheet }?.count ?: 1
    return stringResource(Res.string.presets_summary, s.printer ?: "", "$duplex · $up")
}

@Composable
internal fun presetErrorText(error: ValidationError): String = when (error) {
    ValidationError.PresetNameEmpty -> stringResource(Res.string.validation_preset_empty)
    is ValidationError.PresetNameTooLong -> stringResource(Res.string.validation_preset_too_long, error.max)
    ValidationError.PresetNameTaken -> stringResource(Res.string.validation_preset_taken)
    ValidationError.TooManyPresets -> stringResource(Res.string.validation_preset_limit)
    ValidationError.PresetNotFound -> stringResource(Res.string.validation_preset_missing)
    else -> stringResource(Res.string.validation_preset_empty)
}
