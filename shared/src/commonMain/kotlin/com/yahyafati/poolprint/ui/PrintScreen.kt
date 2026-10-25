package com.yahyafati.poolprint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yahyafati.poolprint.core.cost.CostRates
import com.yahyafati.poolprint.core.discovery.DiscoveredOption
import com.yahyafati.poolprint.core.discovery.PrinterState
import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.Orientation
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.model.PaperSize
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.core.model.PrinterType
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.service.FailureKind
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

@Composable
fun PrintScreen(model: PrintModel, onClose: () -> Unit, onShowKey: () -> Unit, onQueue: () -> Unit) {
    val state by model.state.collectAsState()
    // Looks at the server when the sheet opens and when the printer changes; failure is silent.
    LaunchedEffect(model, state.printer.name) { model.refreshDiscovery() }
    when (val phase = state.phase) {
        is PrintPhase.AskTrust -> TrustServerScreen(phase.key, onTrust = model::trustAndRetry, onCancel = model::declineHostKey)
        is PrintPhase.Failed if phase.failure.kind == FailureKind.HostKeyChanged ->
            HostKeyChangedScreen(phase.failure, onClose = model::dismissFailure)
        is PrintPhase.Uploading -> SendingScreen(state, phase, model::cancel)
        is PrintPhase.Sent -> SentScreen(state, phase, onDone = onClose, onQueue = { onClose(); onQueue() })
        else -> PrintSheet(state, model, onClose, onShowKey, onQueue)
    }
}

@Composable
private fun PrintSheet(state: PrintState, model: PrintModel, onClose: () -> Unit, onShowKey: () -> Unit, onQueue: () -> Unit) {
    val c = PoolTheme.colors
    val t = PoolTheme.type
    var saving by remember { mutableStateOf(false) }
    val failed = state.phase as? PrintPhase.Failed
    PhoneScreen(
        topBar = { CloseBar(onClose) { TextAction(stringResource(Res.string.queue_title_short), onQueue) } },
        bottomBar = {
            failed?.let { FailureCard(it.failure, onRetry = null, onShowKey = onShowKey) }
            state.estimate?.takeIf { it.exceedsWarningThreshold && failed == null }?.let { CostWarning(formatCents(it.costCents)) }
            if (state.pageRangesError != null) {
                Text(stringResource(Res.string.print_fix_ranges), style = t.label, color = c.error)
            } else {
                Text(rateLine(state), style = t.caption, color = c.onVariant)
            }
            BlockButton(
                printButtonLabel(state, retry = failed != null),
                model::print,
                height = 60.dp,
                trailing = if (failed != null) "↻" else "→",
                enabled = state.canPrint,
                textStyle = t.button.copy(fontSize = 18.sp),
            )
        },
        overlay = { if (saving) SavePresetPanel(model, onDismiss = { saving = false }) },
    ) {
        DocumentHeader(state, documentSummary(state))
        Column(Modifier.gutter()) {
            PresetRow(state, model, onSave = { saving = true })
            Rule(strong = false)
            PrinterRow(state, model)
            Rule(strong = false)
            CopiesRow(state.copies, model.maxCopies, model::decrementCopies, model::incrementCopies)
            Rule(strong = false)
            SwitchRow(
                stringResource(Res.string.print_duplex),
                checked = state.options.duplex != Duplex.OFF,
                onCheckedChange = { model.setDuplex(if (it) Duplex.LONG_EDGE else Duplex.OFF) },
                subtitle = duplexSubtitle(state),
            )
            Rule(strong = false)
            AdvancedOptions(state, model)
            Rule()
        }
    }
}

/** The 52dp bar with × on the left and an optional action on the right. */
@Composable
private fun CloseBar(onClose: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 12.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphButton("×", stringResource(Res.string.action_close), onClose)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun DocumentHeader(state: PrintState, summary: String) {
    Row(
        Modifier.fillMaxWidth().gutter().padding(top = 4.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DocThumb(colour = state.printer.type != PrinterType.BW)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.document.displayName, style = PoolTheme.type.documentTitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(summary, style = PoolTheme.type.body.copy(fontSize = 14.sp), color = PoolTheme.colors.onVariant)
        }
    }
    Rule()
}

/** The preset menu (when there are presets) and "Save as preset". */
@Composable
private fun PresetRow(state: PrintState, model: PrintModel, onSave: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val hasPresets = state.presets.isNotEmpty()
    val current = state.activePreset?.name
        ?: stringResource(if (hasPresets) Res.string.preset_custom else Res.string.preset_none)
    ListRow(trailing = { TextAction(stringResource(Res.string.preset_save), onSave) }) {
        Box(Modifier.weight(1f)) {
            RowText(
                current + if (hasPresets) " ▾" else "",
                Modifier.then(if (hasPresets) Modifier.clickable(role = Role.Button) { expanded = true } else Modifier),
                overline = stringResource(Res.string.preset_label),
            )
            PoolMenu(expanded, onDismiss = { expanded = false }) {
                for (preset in state.presets) {
                    PoolMenuItem(preset.name, { model.applyPreset(preset.id); expanded = false }, selected = preset == state.activePreset)
                }
            }
        }
    }
}

@Composable
private fun PrinterRow(state: PrintState, model: PrintModel) {
    var expanded by remember { mutableStateOf(false) }
    val printer = state.printer
    Box {
        ListRow(minHeight = 72.dp, onClick = { expanded = true }, trailing = { Chevron() }) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.print_printer), style = PoolTheme.type.label.copy(fontSize = 12.sp), color = PoolTheme.colors.onVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(printer.name, style = PoolTheme.type.rowTitle)
                    TypeBadge(printer.type)
                }
                printerDetail(printer, state.printerState(printer))?.let {
                    Text(it, style = PoolTheme.type.caption, color = PoolTheme.colors.onVariant)
                }
            }
        }
        PoolMenu(expanded, onDismiss = { expanded = false }) {
            for (item in state.knownPrinters) {
                PoolMenuItem(
                    printerLabel(item, state.printerState(item)),
                    { model.selectPrinter(item.name); expanded = false },
                    selected = item == printer,
                )
            }
        }
    }
}

@Composable
private fun CopiesRow(copies: Int, max: Int, onLess: () -> Unit, onMore: () -> Unit) {
    ListRow {
        Text(stringResource(Res.string.print_copies), style = PoolTheme.type.rowTitle, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton("−", stringResource(Res.string.print_copies_less), enabled = copies > 1, onClick = onLess)
            Text(copies.toString(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
            StepperButton("+", stringResource(Res.string.print_copies_more), enabled = copies < max, onClick = onMore)
        }
    }
}

@Composable
private fun StepperButton(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = PoolTheme.colors
    Box(
        Modifier.size(48.dp).border(2.dp, c.on).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 20.sp, color = if (enabled) c.on else c.outlineVariant)
    }
}

@Composable
private fun AdvancedOptions(state: PrintState, model: PrintModel) {
    val c = PoolTheme.colors
    val t = PoolTheme.type
    val options = state.options
    val untouched = PrintOptions()
    var expanded by remember {
        mutableStateOf(
            options.pagesPerSheet != untouched.pagesPerSheet || options.orientation != untouched.orientation ||
                options.fitToPage != untouched.fitToPage || options.paperSize != untouched.paperSize ||
                options.duplex == Duplex.SHORT_EDGE,
        )
    }
    // A bad page range must stay visible, because it disables Print.
    val open = expanded || state.pageRangesError != null
    ListRow(onClick = { expanded = !open }, trailing = { Text(if (open) "▴" else "▾", fontSize = 18.sp) }) {
        RowText(stringResource(Res.string.print_advanced), Modifier.weight(1f), subtitle = if (open) null else advancedSummary(state))
    }
    if (!open) return
    Column(Modifier.padding(bottom = 8.dp)) {
        OptionLabel(stringResource(Res.string.print_pages_per_sheet))
        Segmented(PagesPerSheet.entries, options.pagesPerSheet, { it.count.toString() }, model::setPagesPerSheet)
        val supporting = options.pagesPerSheet.takeIf { it != PagesPerSheet.ONE }
            ?.let { stringResource(Res.string.print_page_ranges_hint_layout, it.count) }
        PoolTextField(
            value = state.pageRangesText,
            onValueChange = model::setPageRanges,
            label = stringResource(Res.string.print_page_ranges),
            placeholder = stringResource(Res.string.print_page_ranges_placeholder),
            helper = supporting,
            error = state.pageRangesError?.let { pageRangesErrorText(it) },
            textStyle = t.mono,
            modifier = Modifier.padding(top = 20.dp),
        )
        OptionLabel(stringResource(Res.string.print_orientation))
        Segmented(
            Orientation.entries,
            options.orientation,
            { stringResource(if (it == Orientation.PORTRAIT) Res.string.print_orientation_portrait else Res.string.print_orientation_landscape) },
            model::setOrientation,
        )
        if (options.duplex != Duplex.OFF) {
            OptionLabel(stringResource(Res.string.print_duplex_flip))
            Segmented(
                listOf(Duplex.LONG_EDGE, Duplex.SHORT_EDGE),
                options.duplex,
                { duplexLabel(it) },
                model::setDuplex,
            )
        }
        Spacer(Modifier.height(14.dp))
        Rule(strong = false)
        SwitchRow(stringResource(Res.string.print_fit_to_page), options.fitToPage, model::setFitToPage, minHeight = 56.dp)
        Rule(strong = false)
        MenuRow(
            stringResource(Res.string.print_paper_size),
            items = PaperSize.entries,
            selected = options.paperSize,
            text = { it.cupsValue },
            onSelect = model::setPaperSize,
        )
        DiscoveredControls(state, model)
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(text, style = PoolTheme.type.label, color = PoolTheme.colors.onVariant, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

/** Menus generated from the printer's cached `lpoptions`; the default value is not sent. */
@Composable
private fun DiscoveredControls(state: PrintState, model: PrintModel) {
    val controls = state.discoveredControls
    if (controls.isNotEmpty()) {
        Rule(Modifier.padding(top = 16.dp))
        SectionLabel(stringResource(Res.string.print_printer_options, state.printer.name), Modifier.padding(top = 20.dp, bottom = 4.dp))
        for (option in controls) {
            val selected = state.selectedValue(option)
            MenuRow(
                option.label,
                items = option.values,
                selected = selected ?: option.values.first(),
                text = { optionValueText(option, it) },
                onSelect = { model.setDiscovered(option.key, it) },
            )
            Rule(strong = false)
        }
    }
    val hint = when {
        state.discoveryStatus == DiscoveryStatus.Refreshing && state.capabilities == null -> stringResource(Res.string.print_defaults_refresh)
        state.usingDefaults -> stringResource(Res.string.print_defaults_hint)
        else -> null
    }
    hint?.let { Text(it, style = PoolTheme.type.caption, color = PoolTheme.colors.onVariant, modifier = Modifier.padding(top = 12.dp)) }
}

/** A row whose value opens a menu: "Paper size … A4 ▾". */
@Composable
private fun <T> MenuRow(label: String, items: List<T>, selected: T, text: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ListRow(minHeight = 52.dp, onClick = { expanded = true }, trailing = { Text(text(selected) + " ▾", style = PoolTheme.type.body) }) {
            Text(label, style = PoolTheme.type.rowTitle, modifier = Modifier.weight(1f))
        }
        PoolMenu(expanded, onDismiss = { expanded = false }) {
            for (item in items) PoolMenuItem(text(item), { onSelect(item); expanded = false }, selected = item == selected)
        }
    }
}

@Composable
private fun optionValueText(option: DiscoveredOption, value: String): String =
    if (value == option.default) stringResource(Res.string.print_option_default, value) else value

/** The tinted note above the Print button when a job costs more than the warning threshold. */
@Composable
private fun CostWarning(cost: String) {
    val c = PoolTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.tertiaryContainer).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("i", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = c.onTertiaryContainer)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(Res.string.print_cost_warning_lead, cost)) }
                append(" ")
                append(stringResource(Res.string.print_cost_warning))
            },
            style = PoolTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = c.onTertiaryContainer,
        )
    }
}

@Composable
private fun SendingScreen(state: PrintState, phase: PrintPhase.Uploading, onCancel: () -> Unit) {
    val c = PoolTheme.colors
    val t = PoolTheme.type
    PhoneScreen(
        topBar = { Spacer(Modifier.height(52.dp)) },
        bottomBar = {
            BlockButton(stringResource(Res.string.action_cancel), onCancel, kind = ButtonKind.Outline, height = 60.dp, textStyle = t.button.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
        },
    ) {
        DocumentHeader(state, jobSummary(state))
        Column(Modifier.gutter().padding(top = 72.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.print_sending_to, state.printer.name), style = t.headline)
            val total = phase.totalBytes
            if (total != null && total > 0) {
                val fraction = (phase.sentBytes.toFloat() / total).coerceIn(0f, 1f)
                BarProgress(fraction, height = 8.dp)
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(Res.string.print_uploading_progress, formatBytes(phase.sentBytes), formatBytes(total)),
                        style = t.body.copy(fontSize = 14.sp),
                        color = c.onVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(fraction * 100).toInt()} %", style = t.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
            } else {
                BarProgress(null, height = 8.dp)
            }
        }
    }
}

@Composable
private fun SentScreen(state: PrintState, phase: PrintPhase.Sent, onDone: () -> Unit, onQueue: () -> Unit) {
    val c = PoolTheme.colors
    val t = PoolTheme.type
    PhoneScreen(
        bottomBar = {
            BlockButton(stringResource(Res.string.action_done), onDone, trailing = "→")
            BlockButton(stringResource(Res.string.print_view_queue), onQueue, kind = ButtonKind.Outline)
        },
    ) {
        Column(Modifier.gutter().padding(top = 120.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SuccessMark()
            Text(
                phase.jobId?.let { stringResource(Res.string.print_sent_job, phase.printerName, it) }
                    ?: stringResource(Res.string.print_sent, phase.printerName),
                style = t.display.copy(fontSize = 36.sp, lineHeight = 39.sp),
            )
            Text(stringResource(Res.string.print_sent_collect), style = t.lead.copy(fontSize = 18.sp))
        }
        Column(Modifier.gutter().padding(top = 28.dp)) {
            Rule()
            SummaryLine(stringResource(Res.string.print_document), state.document.displayName)
            state.estimate?.let { estimate ->
                Rule(strong = false)
                SummaryLine(stringResource(Res.string.print_cost), "${formatCents(estimate.costCents)} · ${pagesText(estimate.billablePages)}")
            }
            Rule(strong = false)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = PoolTheme.type.bodySmall, color = PoolTheme.colors.onVariant)
        Text(
            value,
            style = PoolTheme.type.bodySmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Saves the current printer and options under a name. Copies and page ranges aren't stored (D-15). */
@Composable
private fun SavePresetPanel(model: PrintModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<ValidationError?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    fun save() {
        scope.launch {
            val result = model.savePreset(name)
            if (result == null) onDismiss() else error = result
        }
    }
    ConfirmPanel(stringResource(Res.string.preset_save_title), onDismiss) {
        PoolTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = stringResource(Res.string.preset_name_label),
            helper = stringResource(Res.string.preset_save_note),
            error = error?.let { presetErrorText(it) },
            textStyle = PoolTheme.type.rowTitle.copy(fontSize = 17.sp),
            keyboardActions = KeyboardActions(onDone = { save() }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            focusRequester = focus,
        )
        ConfirmButtons(
            stringResource(Res.string.action_save),
            ::save,
            stringResource(Res.string.action_cancel),
            onDismiss,
            danger = false,
        )
    }
}

@Composable
private fun documentSummary(state: PrintState): String {
    val size = state.document.sizeBytes?.let(::formatBytes)
    return when (val count = state.pageCount) {
        PageCount.Counting -> listOfNotNull(stringResource(Res.string.print_pages_unknown), size)
        is PageCount.Known -> listOfNotNull(pagesText(count.pages), size)
        PageCount.Unknown -> listOfNotNull(size)
    }.joinToString(" · ")
}

/** "12 pages · tfppr1 · 0.60 €" while sending. */
@Composable
private fun jobSummary(state: PrintState): String {
    val estimate = state.estimate
    return listOfNotNull(
        estimate?.let { pagesText(it.billablePages) },
        state.printer.name,
        estimate?.let { formatCents(it.costCents) },
    ).joinToString(" · ")
}

@Composable
private fun pagesText(pages: Int): String =
    if (pages == 1) stringResource(Res.string.print_pages_one) else stringResource(Res.string.print_pages_many, pages)

@Composable
private fun printButtonLabel(state: PrintState, retry: Boolean): String {
    val estimate = state.estimate
    return when {
        estimate != null -> stringResource(
            if (retry) Res.string.print_retry_cost else Res.string.print_button_cost,
            pagesText(estimate.billablePages),
            formatCents(estimate.costCents),
        )
        state.pageCount == PageCount.Unknown || state.pageRangesError != null ->
            stringResource(if (retry) Res.string.print_retry_unknown else Res.string.print_button_unknown)
        else -> stringResource(if (retry) Res.string.print_retry else Res.string.print_button)
    }
}

/** "B/W · 5 ct per page", and "· 3 × 12 pages" with more than one copy. */
@Composable
private fun rateLine(state: PrintState): String {
    val type = state.printer.type
    val parts = mutableListOf(printerTypeBadge(type), stringResource(Res.string.print_rate, CostRates.centsPerPage(type)))
    val estimate = state.estimate
    if (estimate != null && state.copies > 1) {
        parts += stringResource(Res.string.print_rate_copies, state.copies, pagesText(estimate.billablePages / state.copies))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun duplexSubtitle(state: PrintState): String {
    if (state.options.duplex == Duplex.OFF) return stringResource(Res.string.print_duplex_off)
    val estimate = state.estimate ?: return duplexLabel(state.options.duplex)
    val sheets = estimate.sheets * state.copies
    return if (sheets == 1) stringResource(Res.string.print_sheets_one) else stringResource(Res.string.print_sheets_many, sheets)
}

/** "All pages · 1 per sheet · A4" under the collapsed Advanced options row. */
@Composable
private fun advancedSummary(state: PrintState): String {
    val options = state.options
    val pages = options.pageRanges?.canonical ?: stringResource(Res.string.print_all_pages)
    return listOf(pages, stringResource(Res.string.print_per_sheet, options.pagesPerSheet.count), options.paperSize.cupsValue).joinToString(" · ")
}

@Composable
private fun pageRangesErrorText(error: ValidationError): String = when (error) {
    ValidationError.PageRangeReversed -> stringResource(Res.string.validation_page_ranges_reversed)
    ValidationError.PageRangesTooLong -> stringResource(Res.string.validation_page_ranges_too_long)
    is ValidationError.PageRangeBeyondDocument -> stringResource(Res.string.validation_page_ranges_beyond, error.lastPage)
    else -> stringResource(Res.string.validation_page_ranges_invalid)
}

@Composable
private fun duplexLabel(duplex: Duplex): String = stringResource(
    when (duplex) {
        Duplex.OFF -> Res.string.print_duplex_off
        Duplex.LONG_EDGE -> Res.string.print_duplex_long
        Duplex.SHORT_EDGE -> Res.string.print_duplex_short
    },
)

@Composable
private fun printerStateText(state: PrinterState?): String? = when (state) {
    PrinterState.IDLE -> stringResource(Res.string.print_printer_state_idle)
    PrinterState.PRINTING -> stringResource(Res.string.print_printer_state_printing)
    PrinterState.DISABLED -> stringResource(Res.string.print_printer_state_disabled)
    PrinterState.UNKNOWN, null -> null
}

/** The line under the printer name: its state from discovery, and the billing note for an unknown type. */
@Composable
private fun printerDetail(printer: Printer, state: PrinterState?): String? = listOfNotNull(
    printerStateText(state),
    if (printer.type == PrinterType.UNKNOWN) stringResource(Res.string.print_printer_unknown_type) else null,
).joinToString(" · ").ifEmpty { null }

@Composable
private fun printerLabel(printer: Printer, state: PrinterState?): String =
    listOfNotNull(printer.name, printerTypeBadge(printer.type), printerStateText(state)).joinToString(" · ")

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${(bytes * 10 / (1024 * 1024)) / 10.0} MB"
}
