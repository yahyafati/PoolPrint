package com.yahyafati.poolprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.Orientation
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.model.PaperSize
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.core.model.PrinterType
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.validation.ValidationError
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

@Composable
fun PrintScreen(model: PrintModel, onClose: () -> Unit, onShowKey: () -> Unit) {
    val state by model.state.collectAsState()
    ScreenColumn {
        Text(stringResource(Res.string.print_title), style = MaterialTheme.typography.headlineMedium)
        Text(state.document.displayName, style = MaterialTheme.typography.titleMedium)
        Text(documentSummary(state), style = MaterialTheme.typography.bodySmall)

        when (val phase = state.phase) {
            is PrintPhase.Sent -> {
                Text(
                    phase.jobId?.let { stringResource(Res.string.print_sent_job, phase.printerName, it) }
                        ?: stringResource(Res.string.print_sent, phase.printerName),
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(onClick = onClose) { Text(stringResource(Res.string.action_done)) }
            }

            is PrintPhase.Uploading -> {
                val total = phase.totalBytes
                if (total != null && total > 0) {
                    LinearProgressIndicator(progress = { (phase.sentBytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(Res.string.print_uploading_progress, formatBytes(phase.sentBytes), formatBytes(total)))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(Res.string.print_uploading))
                }
                OutlinedButton(onClick = model::cancel) { Text(stringResource(Res.string.action_cancel)) }
            }

            else -> {
                val options = state.options
                DropdownRow(
                    label = stringResource(Res.string.print_printer),
                    items = Printers.all,
                    selected = options.printer,
                    text = { printerLabel(it) },
                    onSelect = { model.selectPrinter(it.name) },
                )
                CopiesStepper(state.copies, model.maxCopies, model::decrementCopies, model::incrementCopies)
                DropdownRow(
                    label = stringResource(Res.string.print_duplex),
                    items = Duplex.entries,
                    selected = options.duplex,
                    text = { duplexLabel(it) },
                    onSelect = model::setDuplex,
                )
                AdvancedOptions(state, model)
                state.estimate?.takeIf { it.exceedsWarningThreshold }?.let {
                    Text(
                        stringResource(Res.string.print_cost_warning, formatCents(it.costCents)),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (phase is PrintPhase.AskTrust) {
                    TrustHostKeyCard(phase.key, onTrust = model::trustAndRetry, onDecline = model::declineHostKey)
                }
                if (phase is PrintPhase.Failed) {
                    FailureCard(phase.failure, onRetry = model::print.takeIf { phase.failure.kind != com.yahyafati.poolprint.service.FailureKind.HostKeyChanged }, onShowKey = onShowKey)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onClose) { Text(stringResource(Res.string.action_cancel)) }
                    Button(onClick = model::print, enabled = state.canPrint && phase !is PrintPhase.AskTrust) {
                        Text(printButtonLabel(state))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedOptions(state: PrintState, model: PrintModel) {
    val options = state.options
    val untouched = PrintOptions()
    var expanded by remember {
        mutableStateOf(
            options.pagesPerSheet != untouched.pagesPerSheet || options.orientation != untouched.orientation ||
                options.fitToPage != untouched.fitToPage || options.paperSize != untouched.paperSize,
        )
    }
    TextButton(onClick = { expanded = !expanded }) {
        Text((if (expanded) "▾ " else "▸ ") + stringResource(Res.string.print_advanced))
    }
    // A bad page range must stay visible, because it disables Print.
    if (expanded || state.pageRangesError != null) {
        val supporting = state.pageRangesError?.let { pageRangesErrorText(it) }
            ?: options.pagesPerSheet.takeIf { it != PagesPerSheet.ONE }
                ?.let { stringResource(Res.string.print_page_ranges_hint_layout, it.count) }
        OutlinedTextField(
            value = state.pageRangesText,
            onValueChange = model::setPageRanges,
            label = { Text(stringResource(Res.string.print_page_ranges)) },
            placeholder = { Text(stringResource(Res.string.print_page_ranges_placeholder)) },
            supportingText = supporting?.let { { Text(it) } },
            isError = state.pageRangesError != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (expanded) {
        ChoiceRow(
            label = stringResource(Res.string.print_pages_per_sheet),
            items = PagesPerSheet.entries,
            selected = options.pagesPerSheet,
            text = { it.count.toString() },
            onSelect = model::setPagesPerSheet,
        )
        ChoiceRow(
            label = stringResource(Res.string.print_orientation),
            items = Orientation.entries,
            selected = options.orientation,
            text = { stringResource(if (it == Orientation.PORTRAIT) Res.string.print_orientation_portrait else Res.string.print_orientation_landscape) },
            onSelect = model::setOrientation,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.print_fit_to_page))
            Switch(checked = options.fitToPage, onCheckedChange = model::setFitToPage)
        }
        DropdownRow(
            label = stringResource(Res.string.print_paper_size),
            items = PaperSize.entries,
            selected = options.paperSize,
            text = { it.cupsValue },
            onSelect = model::setPaperSize,
        )
    }
}

@Composable
private fun documentSummary(state: PrintState): String = when (val count = state.pageCount) {
    PageCount.Counting -> listOfNotNull(state.document.sizeBytes?.let(::formatBytes), stringResource(Res.string.print_pages_unknown)).joinToString(" · ")
    is PageCount.Known -> listOfNotNull(state.document.sizeBytes?.let(::formatBytes), pagesText(count.pages)).joinToString(" · ")
    PageCount.Unknown -> state.document.sizeBytes?.let(::formatBytes).orEmpty()
}

@Composable
private fun pagesText(pages: Int): String =
    if (pages == 1) stringResource(Res.string.print_pages_one) else stringResource(Res.string.print_pages_many, pages)

@Composable
private fun printButtonLabel(state: PrintState): String {
    val estimate = state.estimate
    return when {
        estimate != null -> stringResource(Res.string.print_button_cost, pagesText(estimate.billablePages), formatCents(estimate.costCents))
        state.pageCount == PageCount.Unknown -> stringResource(Res.string.print_button_unknown)
        else -> stringResource(Res.string.print_button)
    }
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
private fun <T> DropdownRow(label: String, items: List<T>, selected: T, text: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(text(selected) + " ▾") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for (item in items) {
                    DropdownMenuItem(text = { Text(text(item)) }, onClick = { onSelect(item); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(label: String, items: List<T>, selected: T, text: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (item in items) {
                FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(text(item)) })
            }
        }
    }
}

@Composable
private fun printerLabel(printer: Printer): String {
    val type = stringResource(if (printer.type == PrinterType.BW) Res.string.print_printer_bw else Res.string.print_printer_color)
    return "${printer.name} · $type"
}

@Composable
private fun CopiesStepper(copies: Int, max: Int, onLess: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.print_copies))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onLess, enabled = copies > 1) { Text("–") }
            Text("  $copies  ", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onMore, enabled = copies < max) { Text("+") }
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${(bytes * 10 / (1024 * 1024)) / 10.0} MB"
}
