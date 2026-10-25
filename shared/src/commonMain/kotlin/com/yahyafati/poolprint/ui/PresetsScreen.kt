package com.yahyafati.poolprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.settings.Preset
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

@Composable
fun PresetsScreen(model: PresetsModel, onClose: () -> Unit) {
    val state by model.state.collectAsState()
    var renaming by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<Preset?>(null) }
    val c = PoolTheme.colors
    val t = PoolTheme.type
    PhoneScreen(
        topBar = { TopBar(stringResource(Res.string.presets_title), onBack = onClose) },
        overlay = {
            deleting?.let { preset ->
                ConfirmPanel(stringResource(Res.string.preset_delete_title), onDismiss = { deleting = null }, danger = true) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.on)) { append(preset.name) }
                            append(" ")
                            append(stringResource(Res.string.preset_delete_body))
                        },
                        style = t.bodySmall,
                        color = c.onVariant,
                    )
                    ConfirmButtons(
                        stringResource(Res.string.action_delete),
                        { model.delete(preset.id); deleting = null },
                        stringResource(Res.string.action_cancel),
                        { deleting = null },
                    )
                }
            }
        },
    ) {
        Column(Modifier.gutter()) {
            if (state.loaded && state.presets.isEmpty()) {
                Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(Res.string.presets_empty_title), style = t.headline.copy(fontSize = 26.sp, lineHeight = 30.sp))
                    Text(
                        buildAnnotatedString {
                            append(stringResource(Res.string.presets_empty_before))
                            append(" ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.on)) { append(stringResource(Res.string.preset_save)) }
                            append(stringResource(Res.string.presets_empty_after))
                        },
                        style = t.bodySmall,
                        color = c.onVariant,
                    )
                }
            }
            for (preset in state.presets) {
                if (renaming == preset.id) {
                    RenameRow(model, preset, onDone = { renaming = null })
                } else {
                    PresetRow(preset, onRename = { renaming = preset.id }, onDelete = { deleting = preset })
                }
                Rule(strong = false)
            }
        }
    }
}

@Composable
private fun PresetRow(preset: Preset, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListRow(
        minHeight = 72.dp,
        trailing = {
            Box {
                GlyphButton("⋮", stringResource(Res.string.presets_menu, preset.name), { menu = true }, highlighted = menu)
                PoolMenu(menu, onDismiss = { menu = false }) {
                    PoolMenuItem(stringResource(Res.string.action_rename), { menu = false; onRename() })
                    PoolMenuItem(stringResource(Res.string.action_delete), { menu = false; onDelete() }, color = PoolTheme.colors.error)
                }
            }
        },
    ) {
        RowText(preset.name, Modifier.weight(1f), subtitle = presetSummary(preset), titleStyle = PoolTheme.type.rowTitle.copy(fontSize = 17.sp))
    }
}

/** Rename in place: the name in a field, the summary under it, Save and Cancel. */
@Composable
private fun RenameRow(model: PresetsModel, preset: Preset, onDone: () -> Unit) {
    var name by remember { mutableStateOf(preset.name) }
    var error by remember { mutableStateOf<ValidationError?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    fun save() {
        scope.launch {
            val result = model.rename(preset.id, name)
            if (result == null) onDone() else error = result
        }
    }
    Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PoolTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = stringResource(Res.string.action_rename),
            labelColor = if (error != null) PoolTheme.colors.error else PoolTheme.colors.primary,
            helper = presetSummary(preset),
            error = error?.let { presetErrorText(it) },
            textStyle = PoolTheme.type.rowTitle.copy(fontSize = 17.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            focusRequester = focus,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TextAction(stringResource(Res.string.action_save), ::save)
            TextAction(stringResource(Res.string.action_cancel), onDone, color = PoolTheme.colors.onVariant, style = PoolTheme.type.buttonSmall)
        }
    }
}

/** "tfppr1 · B/W · two-sided · 2 per sheet". */
@Composable
private fun presetSummary(preset: Preset): String {
    val s = preset.settings
    val printer = s.printer?.let { runCatching { Printers.resolve(it) }.getOrNull() } // a stored name is re-validated
    val duplex = when (Duplex.entries.firstOrNull { it.name == s.duplex }) {
        Duplex.LONG_EDGE -> stringResource(Res.string.presets_two_sided)
        Duplex.SHORT_EDGE -> stringResource(Res.string.presets_two_sided_short)
        else -> stringResource(Res.string.presets_one_sided)
    }
    val up = PagesPerSheet.entries.firstOrNull { it.count == s.pagesPerSheet }?.count ?: 1
    return listOfNotNull(
        printer?.name ?: stringResource(Res.string.presets_default_printer),
        printer?.let { printerTypeBadge(it.type) },
        duplex,
        if (up > 1) stringResource(Res.string.print_per_sheet, up) else null,
    ).joinToString(" · ")
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
