package com.yahyafati.poolprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yahyafati.poolprint.app.AppDependencies
import com.yahyafati.poolprint.pdf.PdfDocumentSource
import com.yahyafati.poolprint.pdf.rememberPdfPicker
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

@Composable
fun HomeScreen(
    deps: AppDependencies,
    onDocument: (PdfDocumentSource) -> Unit,
    onSettings: () -> Unit,
    onQueue: () -> Unit,
    onPresets: () -> Unit,
) {
    val username by deps.settings.username.collectAsState(initial = null)
    val pick = rememberPdfPicker(onDocument)
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.home_title), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onSettings) { Text(stringResource(Res.string.action_settings)) }
        }
        username?.let { AssistChip(onClick = onSettings, label = { Text(stringResource(Res.string.home_status_ready, it.value)) }) }
        Text(stringResource(Res.string.home_empty))
        Button(onClick = pick) { Text(stringResource(Res.string.home_pick)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onQueue) { Text(stringResource(Res.string.home_queue)) }
            OutlinedButton(onClick = onPresets) { Text(stringResource(Res.string.home_presets)) }
        }
    }
}
