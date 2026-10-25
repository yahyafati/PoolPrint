package com.yahyafati.poolprint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.yahyafati.poolprint.app.AppDependencies
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.pdf.PdfDocumentSource
import com.yahyafati.poolprint.ui.HomeScreen
import com.yahyafati.poolprint.ui.OnboardingModel
import com.yahyafati.poolprint.ui.OnboardingScreen
import com.yahyafati.poolprint.ui.PoolPrintTheme
import com.yahyafati.poolprint.ui.PresetsModel
import com.yahyafati.poolprint.ui.PresetsScreen
import com.yahyafati.poolprint.ui.PrintModel
import com.yahyafati.poolprint.ui.PrintScreen
import com.yahyafati.poolprint.ui.QueueModel
import com.yahyafati.poolprint.ui.QueueScreen
import com.yahyafati.poolprint.ui.SettingsModel
import com.yahyafati.poolprint.ui.SettingsScreen

/** A full screen shown instead of home or the print sheet; Done returns to whichever was underneath. */
private enum class Overlay { None, Settings, Queue, Presets }

/**
 * @param incomingDocument a PDF handed over by the platform (share sheet, Open With); [onIncomingHandled]
 * is called once it has been taken, so it isn't delivered twice.
 */
@Composable
fun App(
    deps: AppDependencies,
    incomingDocument: PdfDocumentSource? = null,
    onIncomingHandled: () -> Unit = {},
) {
    PoolPrintTheme {
        val onboarded by deps.settings.onboardingComplete.collectAsState(initial = null)
        var document by remember { mutableStateOf<PdfDocumentSource?>(null) }
        var screen by remember { mutableStateOf(Overlay.None) }

        LaunchedEffect(incomingDocument) {
            if (incomingDocument != null) {
                document = incomingDocument
                onIncomingHandled()
            }
        }

        val scope = rememberCoroutineScope()
        when {
            onboarded == null -> Unit
            onboarded == false -> {
                val model = remember { OnboardingModel(deps, scope) }
                OnboardingScreen(model, onFinished = {})
            }
            screen == Overlay.Settings -> {
                val model = remember { SettingsModel(deps, scope) }
                SettingsScreen(model, onClose = { screen = Overlay.None })
            }
            screen == Overlay.Queue -> {
                val model = remember { QueueModel(deps, scope) }
                QueueScreen(model, onClose = { screen = Overlay.None }, onShowKey = { screen = Overlay.Settings })
            }
            screen == Overlay.Presets -> {
                val model = remember { PresetsModel(deps, scope) }
                PresetsScreen(model, onClose = { screen = Overlay.None })
            }
            document != null -> {
                val doc = document!!
                // The remembered printer and options load first (a quick local read), so the sheet opens filled in.
                // The cached printer list is read too, so a remembered discovered printer is still found.
                val remembered by produceState<PrintOptions?>(null, doc) {
                    value = deps.settings.getPrintDefaults(deps.discovery.cached().knownPrinters)
                }
                remembered?.let { initial ->
                    val model = remember(doc) { PrintModel(deps, doc, scope, initial) }
                    PrintScreen(model, onClose = { document = null }, onShowKey = { screen = Overlay.Settings })
                }
            }
            else -> HomeScreen(
                deps,
                onDocument = { document = it },
                onSettings = { screen = Overlay.Settings },
                onQueue = { screen = Overlay.Queue },
                onPresets = { screen = Overlay.Presets },
            )
        }
    }
}
