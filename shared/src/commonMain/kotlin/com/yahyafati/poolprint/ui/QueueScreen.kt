package com.yahyafati.poolprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yahyafati.poolprint.core.queue.QueueEntry
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

@Composable
fun QueueScreen(model: QueueModel, onClose: () -> Unit, onShowKey: () -> Unit) {
    val state by model.state.collectAsState()
    LaunchedEffect(model) { model.refresh() }
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.queue_title), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onClose) { Text(stringResource(Res.string.action_done)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(stringResource(Res.string.queue_printer) + ": " + state.printer.name + " ▾") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    for (printer in state.printers) {
                        DropdownMenuItem(text = { Text(printer.name) }, onClick = { model.selectPrinter(printer.name); expanded = false })
                    }
                }
            }
            OutlinedButton(onClick = model::refresh, enabled = !state.loading) { Text(stringResource(Res.string.action_refresh)) }
        }
        if (state.loading || state.cancelling) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        state.askTrust?.let { TrustHostKeyCard(it, onTrust = model::trustAndRetry, onDecline = model::declineTrust) }
        state.failure?.let { FailureCard(it, onRetry = model::refresh, onShowKey = onShowKey) }
        state.notice?.let {
            Text(
                stringResource(if (it == CancelNotice.Cancelled) Res.string.queue_notice_cancelled else Res.string.queue_notice_not_allowed),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        state.queue?.let { queue ->
            queue.statusLine?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (queue.entries.isEmpty()) Text(stringResource(Res.string.queue_empty))
            for (entry in queue.entries) {
                QueueRow(entry, own = state.isOwn(entry), onCancel = { model.askCancel(entry) })
            }
        }
    }
    state.confirming?.let { entry ->
        AlertDialog(
            onDismissRequest = model::dismissCancel,
            title = { Text(stringResource(Res.string.queue_cancel_title, entry.jobId)) },
            text = { Text(stringResource(Res.string.queue_cancel_body, state.printer.name)) },
            confirmButton = { TextButton(onClick = model::confirmCancel) { Text(stringResource(Res.string.queue_cancel_confirm)) } },
            dismissButton = { TextButton(onClick = model::dismissCancel) { Text(stringResource(Res.string.queue_cancel_keep)) } },
        )
    }
}

/** Own jobs are highlighted and are the only ones with Cancel. */
@Composable
private fun QueueRow(entry: QueueEntry, own: Boolean, onCancel: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = if (own) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.queue_entry, entry.jobId, entry.fileLabel), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(Res.string.queue_entry_detail, entry.rank, entry.owner, entry.sizeBytes?.let(::formatBytes).orEmpty()),
                style = MaterialTheme.typography.bodySmall,
            )
            if (own) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.queue_own), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = onCancel) { Text(stringResource(Res.string.queue_cancel_job)) }
                }
            }
        }
    }
}
