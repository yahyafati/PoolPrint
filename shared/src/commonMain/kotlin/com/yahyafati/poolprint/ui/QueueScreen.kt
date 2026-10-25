package com.yahyafati.poolprint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yahyafati.poolprint.core.queue.QueueEntry
import com.yahyafati.poolprint.service.FailureKind
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

@Composable
fun QueueScreen(model: QueueModel, onClose: () -> Unit, onShowKey: () -> Unit) {
    val state by model.state.collectAsState()
    LaunchedEffect(model) { model.refresh() }
    state.askTrust?.let {
        TrustServerScreen(it, onTrust = model::trustAndRetry, onCancel = model::declineTrust)
        return
    }
    state.failure?.takeIf { it.kind == FailureKind.HostKeyChanged }?.let {
        HostKeyChangedScreen(it, onClose = model::dismissFailure)
        return
    }
    val c = PoolTheme.colors
    val t = PoolTheme.type
    PhoneScreen(
        topBar = {
            TopBar(stringResource(Res.string.queue_title), onBack = onClose) {
                IconButton48(PoolIcons.Refresh, stringResource(Res.string.action_refresh), model::refresh, enabled = !state.loading && !state.cancelling)
            }
        },
        bottomBar = state.notice?.let { notice ->
            {
                InverseBar(
                    stringResource(if (notice == CancelNotice.Cancelled) Res.string.queue_notice_cancelled else Res.string.queue_notice_not_allowed),
                    action = stringResource(Res.string.action_ok),
                    onAction = model::dismissNotice,
                )
            }
        },
        overlay = {
            state.confirming?.let { entry ->
                ConfirmPanel(stringResource(Res.string.queue_cancel_title), onDismiss = model::dismissCancel) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.on)) { append(entry.fileLabel) }
                            append(" ")
                            append(stringResource(Res.string.queue_cancel_body, state.printer.name))
                        },
                        style = t.bodySmall,
                        color = c.onVariant,
                    )
                    ConfirmButtons(
                        stringResource(Res.string.queue_cancel_confirm, entry.jobId),
                        model::confirmCancel,
                        stringResource(Res.string.queue_cancel_keep),
                        model::dismissCancel,
                    )
                }
            }
        },
    ) {
        Column(Modifier.gutter()) {
            PrinterPicker(state, model)
            Rule()
            if (state.loading || state.cancelling) BarProgress(null)
            val queue = state.queue
            when {
                state.failure != null -> FailureCard(
                    state.failure!!,
                    onRetry = model::refresh,
                    onShowKey = onShowKey,
                    retryLabel = stringResource(Res.string.action_refresh),
                    modifier = Modifier.padding(top = 20.dp),
                )
                queue == null -> if (state.loading) LoadingRows()
                queue.entries.isEmpty() -> Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(Res.string.queue_empty_title), style = t.title)
                    Text(stringResource(Res.string.queue_empty, state.printer.name), style = t.bodySmall, color = c.onVariant)
                    queue.statusLine?.let { Text(it, style = t.caption, color = c.onVariant) }
                }
                else -> {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel(
                            if (queue.entries.size == 1) stringResource(Res.string.queue_count_one) else stringResource(Res.string.queue_count_many, queue.entries.size),
                        )
                        queue.statusLine?.let {
                            Text(it, style = t.caption, color = c.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                    queue.entries.forEachIndexed { index, entry ->
                        val own = state.isOwn(entry)
                        if (!own && (index == 0 || !state.isOwn(queue.entries[index - 1]))) Rule(strong = false)
                        QueueRow(index + 1, entry, own = own, onCancel = { model.askCancel(entry) })
                    }
                    if (queue.entries.lastOrNull()?.let(state::isOwn) == false) Rule(strong = false)
                }
            }
        }
    }
}

@Composable
private fun PrinterPicker(state: QueueState, model: QueueModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ListRow(minHeight = 72.dp, onClick = { expanded = true }, trailing = { Text("▾", fontSize = 18.sp) }) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.queue_printer), style = PoolTheme.type.label.copy(fontSize = 12.sp), color = PoolTheme.colors.onVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.printer.name, style = PoolTheme.type.rowTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold))
                    TypeBadge(state.printer.type)
                }
            }
        }
        PoolMenu(expanded, onDismiss = { expanded = false }) {
            for (printer in state.printers) {
                PoolMenuItem(printer.name, { model.selectPrinter(printer.name); expanded = false }, selected = printer == state.printer)
            }
        }
    }
}

/** Own jobs are highlighted, tagged YOU, and are the only ones with Cancel. */
@Composable
private fun QueueRow(position: Int, entry: QueueEntry, own: Boolean, onCancel: () -> Unit) {
    val c = PoolTheme.colors
    val t = PoolTheme.type
    val ink = if (own) c.onPrimaryContainer else c.on
    val detail = listOfNotNull(
        entry.owner,
        entry.sizeBytes?.let(::formatBytes),
        stringResource(if (entry.rank == "active") Res.string.queue_state_printing else Res.string.queue_state_waiting),
        if (own) stringResource(Res.string.queue_job, entry.jobId) else null,
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().then(if (own) Modifier.background(c.primaryContainer).padding(horizontal = 12.dp) else Modifier)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(position.toString(), fontWeight = FontWeight.ExtraBold, color = if (own) ink else c.onVariant, modifier = Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    entry.fileLabel,
                    style = t.rowTitle.copy(fontWeight = if (own) FontWeight.ExtraBold else FontWeight.Bold),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (own) OutlineTag(stringResource(Res.string.queue_own), ink)
            }
            Text(detail, style = t.caption, color = if (own) ink else c.onVariant)
        }
        if (own) {
            TextAction(
                stringResource(Res.string.action_cancel),
                onCancel,
                color = ink,
                style = t.action.copy(textDecoration = TextDecoration.Underline),
            )
        }
    }
}

/** Placeholder lines while the first `lpq` runs. */
@Composable
private fun LoadingRows() {
    val c = PoolTheme.colors
    Column(Modifier.padding(top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(2) {
            Box(Modifier.fillMaxWidth(if (it == 0) 0.7f else 0.6f).height(14.dp).background(c.surfaceHigh))
            Box(Modifier.fillMaxWidth(if (it == 0) 0.45f else 0.4f).height(10.dp).background(c.surface))
        }
    }
}
