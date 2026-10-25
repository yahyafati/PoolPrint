package com.yahyafati.poolprint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yahyafati.poolprint.app.AppDependencies
import com.yahyafati.poolprint.core.discovery.DiscoverySnapshot
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
    val c = PoolTheme.colors
    val t = PoolTheme.type
    val username by deps.settings.username.collectAsState(initial = null)
    // Local reads only, for the row subtitles and the footer.
    val presetCount by produceState<Int?>(null) { value = runCatching { deps.presets.list().size }.getOrNull() }
    val discovery by produceState<DiscoverySnapshot?>(null) { value = deps.discovery.cached() }
    val pick = rememberPdfPicker(onDocument)
    PhoneScreen(
        topBar = {
            Row(Modifier.fillMaxWidth().height(64.dp).gutter(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.home_title), style = t.title, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(c.primary))
                    Text(stringResource(Res.string.home_status_ready), style = t.label)
                }
            }
            Rule()
        },
        spaceBetween = true,
    ) {
        Column(Modifier.gutter().padding(top = 72.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.home_hero), style = t.hero)
            Text(stringResource(Res.string.home_empty), style = t.lead, color = c.onVariant)
            BlockButton(stringResource(Res.string.home_pick), pick, Modifier.padding(top = 12.dp), trailing = "→")
        }
        Column(Modifier.gutter().padding(top = 48.dp, bottom = 12.dp)) {
            Rule()
            HomeRow(stringResource(Res.string.home_queue), stringResource(Res.string.home_queue_hint), onQueue)
            Rule(strong = false)
            HomeRow(
                stringResource(Res.string.home_presets),
                presetCount?.let { if (it == 0) stringResource(Res.string.home_presets_none) else stringResource(Res.string.home_presets_count, it) },
                onPresets,
            )
            Rule(strong = false)
            HomeRow(
                stringResource(Res.string.action_settings),
                listOfNotNull(username?.value, stringResource(Res.string.home_settings_hint)).joinToString(" · "),
                onSettings,
            )
            Rule(strong = false)
            discovery?.printersFetchedAtMillis?.let { fetched ->
                Text(
                    stringResource(Res.string.settings_discovery_updated, discovery?.printers?.size ?: 0, formatTimestamp(fetched)),
                    style = t.caption.copy(fontSize = 12.sp),
                    color = c.onVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeRow(title: String, subtitle: String?, onClick: () -> Unit) {
    ListRow(onClick = onClick, trailing = { Chevron() }) {
        RowText(title, Modifier.weight(1f), subtitle = subtitle, titleStyle = PoolTheme.type.rowTitle.copy(fontSize = 17.sp))
    }
}
