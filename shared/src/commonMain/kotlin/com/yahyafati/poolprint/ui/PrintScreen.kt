package com.yahyafati.poolprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.core.model.PrinterType
import com.yahyafati.poolprint.core.model.Printers
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

@Composable
fun PrintScreen(model: PrintModel, onClose: () -> Unit, onShowKey: () -> Unit) {
    val state by model.state.collectAsState()
    ScreenColumn {
        Text(stringResource(Res.string.print_title), style = MaterialTheme.typography.headlineMedium)
        Text(state.document.displayName, style = MaterialTheme.typography.titleMedium)
        state.document.sizeBytes?.let { Text(formatBytes(it), style = MaterialTheme.typography.bodySmall) }

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
                PrinterPicker(state.printer, model::selectPrinter)
                CopiesStepper(state.copies, model.maxCopies, model::decrementCopies, model::incrementCopies)
                if (phase is PrintPhase.AskTrust) {
                    TrustHostKeyCard(phase.key, onTrust = model::trustAndRetry, onDecline = model::declineHostKey)
                }
                if (phase is PrintPhase.Failed) {
                    FailureCard(phase.failure, onRetry = model::print.takeIf { phase.failure.kind != com.yahyafati.poolprint.service.FailureKind.HostKeyChanged }, onShowKey = onShowKey)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onClose) { Text(stringResource(Res.string.action_cancel)) }
                    Button(onClick = model::print, enabled = state.canPrint && phase !is PrintPhase.AskTrust) {
                        Text(stringResource(Res.string.print_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrinterPicker(selected: Printer, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.print_printer))
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { expanded = true }) { Text(printerLabel(selected) + " ▾") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for (printer in Printers.all) {
                    DropdownMenuItem(text = { Text(printerLabel(printer)) }, onClick = { onSelect(printer.name); expanded = false })
                }
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
