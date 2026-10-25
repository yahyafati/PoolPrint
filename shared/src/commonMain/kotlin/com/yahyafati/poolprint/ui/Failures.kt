package com.yahyafati.poolprint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.service.FailureKind
import com.yahyafati.poolprint.service.UserFailure
import com.yahyafati.poolprint.transport.PoolServer
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

/** The catalogue headline for a failure (docs/ux.md). */
@Composable
fun failureTitle(failure: UserFailure): String = when (failure.kind) {
    FailureKind.NotConfigured -> stringResource(Res.string.error_not_configured_title)
    FailureKind.HostUnreachable -> stringResource(Res.string.error_host_unreachable_title)
    FailureKind.AuthRejected -> stringResource(Res.string.error_auth_rejected_title)
    FailureKind.PasswordRejected -> stringResource(Res.string.error_password_rejected_title)
    FailureKind.HostKeyChanged -> stringResource(Res.string.host_changed_title)
    FailureKind.Timeout -> stringResource(Res.string.error_timeout_title)
    FailureKind.UnknownPrinter -> stringResource(Res.string.error_unknown_printer_title, failure.printerName.orEmpty())
    FailureKind.QuotaExceeded -> stringResource(Res.string.error_quota_title)
    FailureKind.LprMissing -> stringResource(Res.string.error_lpr_missing_title)
    FailureKind.PrintFailed -> stringResource(Res.string.error_print_failed_title)
    FailureKind.CommandFailed -> stringResource(Res.string.error_command_failed_title)
    FailureKind.UnexpectedOutput -> stringResource(Res.string.error_unexpected_output_title)
    FailureKind.Protocol -> stringResource(Res.string.error_protocol_title)
}

/** What to do next, under the headline; null when the headline says it all. */
@Composable
fun failureBody(failure: UserFailure): String? = when (failure.kind) {
    FailureKind.NotConfigured -> stringResource(Res.string.error_not_configured)
    FailureKind.HostUnreachable -> stringResource(Res.string.error_host_unreachable)
    FailureKind.AuthRejected -> stringResource(Res.string.error_auth_rejected)
    FailureKind.PasswordRejected -> stringResource(Res.string.error_password_rejected)
    FailureKind.HostKeyChanged -> stringResource(Res.string.host_changed_body)
    FailureKind.Timeout -> stringResource(Res.string.error_timeout)
    FailureKind.UnknownPrinter -> stringResource(Res.string.error_unknown_printer)
    FailureKind.QuotaExceeded -> stringResource(Res.string.error_quota)
    FailureKind.LprMissing -> stringResource(Res.string.error_lpr_missing)
    FailureKind.PrintFailed -> failure.exitCode?.let { stringResource(Res.string.error_print_failed_code, it) }
    FailureKind.CommandFailed -> failure.exitCode?.let { stringResource(Res.string.error_command_failed, it) }
    FailureKind.UnexpectedOutput -> stringResource(Res.string.error_unexpected_output)
    FailureKind.Protocol -> stringResource(Res.string.error_protocol)
}

/**
 * A catalogue error with its inline actions: Show my key (key not authorized) and Retry, plus Details for the raw
 * stderr. A changed host key gets the full-screen [HostKeyChangedScreen] from the caller; here it is only a short
 * block without any way to continue (docs/security.md).
 */
@Composable
fun FailureCard(
    failure: UserFailure,
    onRetry: (() -> Unit)?,
    onShowKey: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    showKeyLabel: String? = null,
) {
    if (failure.kind == FailureKind.HostKeyChanged) {
        ErrorBlock(failureTitle(failure), failureBody(failure), modifier)
        return
    }
    ErrorBlock(failureTitle(failure), failureBody(failure), modifier, details = failure.details) {
        if (failure.kind == FailureKind.AuthRejected && onShowKey != null) {
            TextAction(showKeyLabel ?: stringResource(Res.string.action_show_key), onShowKey)
        }
        if (onRetry != null) TextAction(retryLabel ?: stringResource(Res.string.action_retry), onRetry)
    }
}

/** `SHA256:abcd…` as the prefix on its own line, then groups of four, four groups per line, so it can be compared. */
internal fun groupFingerprint(fingerprint: String): String {
    val prefix = fingerprint.substringBefore(':', missingDelimiterValue = "")
    val digest = fingerprint.substringAfter(':')
    val lines = digest.chunked(4).chunked(4).joinToString("\n") { it.joinToString(" ") }
    return if (prefix.isEmpty()) lines else "$prefix:\n$lines"
}

/**
 * First connection: shows the server's fingerprint and pins it only after an explicit "It matches · Trust".
 * [header] is the onboarding step header, or nothing elsewhere.
 */
@Composable
fun TrustServerScreen(key: PinnedHostKey, onTrust: () -> Unit, onCancel: () -> Unit, header: @Composable () -> Unit = {}) {
    val c = PoolTheme.colors
    val t = PoolTheme.type
    PhoneScreen(
        topBar = header,
        bottomBar = {
            BlockButton(stringResource(Res.string.trust_accept), onTrust, trailing = "→")
            BlockButton(stringResource(Res.string.action_cancel), onCancel, kind = ButtonKind.Outline)
        },
    ) {
        Column(Modifier.gutter().padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.trust_title), style = t.headline)
            Text(stringResource(Res.string.trust_body), style = t.body, color = c.onVariant)
        }
        Column(Modifier.gutter().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${PoolServer.HOST} · ${key.type}", style = t.label, color = c.onVariant)
            Column {
                Rule()
                SelectionContainer {
                    Text(
                        groupFingerprint(key.fingerprint),
                        style = t.mono.copy(fontSize = 17.sp, lineHeight = 29.sp, letterSpacing = 0.02.em),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                Rule()
            }
        }
    }
}

/**
 * The server presented a different key than the pinned one: a blocking red screen. There is deliberately no way to
 * continue from here (docs/security.md); only Close and a copy of the details for the admins.
 */
@Composable
fun HostKeyChangedScreen(failure: UserFailure, onClose: () -> Unit) {
    val c = PoolTheme.colors
    val t = PoolTheme.type
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val report = stringResource(
        Res.string.host_changed_report,
        PoolServer.HOST,
        failure.expectedFingerprint.orEmpty(),
        failure.actualFingerprint.orEmpty(),
    )
    val sides = WindowInsetsSides.Horizontal
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Box(
            Modifier.fillMaxWidth().background(c.error).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + sides)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(PoolIcons.Alert, null, Modifier.size(48.dp), tint = c.bg)
                Text(stringResource(Res.string.host_changed_title), style = t.headline.copy(fontSize = 34.sp, lineHeight = 36.sp), color = c.bg)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + sides)),
            ) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        buildAnnotatedString {
                            append(stringResource(Res.string.host_changed_body))
                            append(" ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(Res.string.host_changed_nothing_sent)) }
                        },
                        style = t.body,
                    )
                    SelectionContainer {
                        Column {
                            Rule()
                            FingerprintLine(stringResource(Res.string.host_changed_trusted), failure.expectedFingerprint.orEmpty(), c.onVariant)
                            Rule(strong = false)
                            FingerprintLine(stringResource(Res.string.host_changed_received), failure.actualFingerprint.orEmpty(), c.error)
                            Rule(strong = false)
                        }
                    }
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(Res.string.host_changed_todo_lead)) }
                            append(" ")
                            append(stringResource(Res.string.host_changed_todo))
                        },
                        style = t.bodySmall,
                    )
                }
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BlockButton(stringResource(Res.string.action_close), onClose, kind = ButtonKind.Ink)
                    BlockButton(
                        stringResource(if (copied) Res.string.host_changed_copied else Res.string.host_changed_copy),
                        onClick = { scope.launch { clipboard.setText(report) }; copied = true },
                        kind = ButtonKind.Outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun FingerprintLine(label: String, fingerprint: String, labelColor: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label.uppercase(), style = PoolTheme.type.caps, color = labelColor)
        Text(fingerprint, style = PoolTheme.type.monoSmall)
    }
}
