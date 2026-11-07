package com.yahyafati.poolprint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.yahyafati.poolprint.app.AppCommands
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
import com.yahyafati.poolprint.ui.PrintPhase
import com.yahyafati.poolprint.ui.PrintScreen
import com.yahyafati.poolprint.ui.QueueModel
import com.yahyafati.poolprint.ui.QueueScreen
import com.yahyafati.poolprint.ui.SettingsModel
import com.yahyafati.poolprint.ui.SettingsScreen

/** A full screen shown instead of home or the print sheet; Done returns to whichever was underneath. */
private enum class Overlay { None, Settings, Queue, Presets }

/**
 * @param appVersion the full version name ("1.0.0-rc.1") from gradle.properties, passed in by the app module
 * (docs/release.md, Phase 2) and shown in Settings → About.
 * @param incomingDocument a PDF handed over by the platform (share sheet, Open With); [onIncomingHandled]
 * is called once it has been taken, so it isn't delivered twice. A document that arrives while a job is
 * being sent is dropped, so the sheet doesn't change under a running upload.
 * @param commands lets the host window trigger the current screen's actions (menu shortcuts).
 */
@Composable
fun App(
    deps: AppDependencies,
    appVersion: String,
    incomingDocument: PdfDocumentSource? = null,
    onIncomingHandled: () -> Unit = {},
    commands: AppCommands = remember { AppCommands() },
) {
    PoolPrintTheme {
        val onboarded by deps.settings.onboardingComplete.collectAsState(initial = null)
        var document by remember { mutableStateOf<PdfDocumentSource?>(null) }
        var screen by remember { mutableStateOf(Overlay.None) }
        var sending by remember { mutableStateOf(false) }

        LaunchedEffect(incomingDocument) {
            if (incomingDocument != null) {
                if (!sending) document = incomingDocument
                onIncomingHandled()
            }
        }

        val scope = rememberCoroutineScope()
        // The print sheet's model lives up here, so opening Queue or Settings from the sheet keeps its options.
        // The remembered printer and options load first (a quick local read), so the sheet opens filled in.
        // The cached printer list is read too, so a remembered discovered printer is still found.
        val doc = document
        val remembered by produceState<Pair<PdfDocumentSource, PrintOptions>?>(null, doc) {
            value = doc?.let { it to deps.settings.getPrintDefaults(deps.discovery.cached().knownPrinters) }
        }
        val printModel = remembered?.takeIf { it.first == doc }?.let { (d, initial) -> remember(d) { PrintModel(deps, d, scope, initial) } }
        when {
            onboarded == null -> Unit
            onboarded == false -> {
                val model = remember { OnboardingModel(deps, scope) }
                OnboardingScreen(model, onFinished = {})
            }
            screen == Overlay.Settings -> {
                val model = remember { SettingsModel(deps, scope) }
                SettingsScreen(model, appVersion, onClose = { screen = Overlay.None })
            }
            screen == Overlay.Queue -> {
                val model = remember { QueueModel(deps, scope) }
                QueueScreen(model, onClose = { screen = Overlay.None }, onShowKey = { screen = Overlay.Settings })
            }
            screen == Overlay.Presets -> {
                val model = remember { PresetsModel(deps, scope) }
                PresetsScreen(model, onClose = { screen = Overlay.None })
            }
            doc != null -> printModel?.let { model ->
                val state by model.state.collectAsState()
                val busy = state.phase is PrintPhase.Uploading || state.phase is PrintPhase.AskTrust
                DisposableEffect(model, state.canPrint) {
                    val clear = commands.registerPrint(if (state.canPrint) model::print else null)
                    onDispose { clear() }
                }
                DisposableEffect(busy) {
                    sending = busy
                    onDispose { sending = false }
                }
                PrintScreen(
                    model,
                    onClose = { document = null },
                    onShowKey = { screen = Overlay.Settings },
                    onQueue = { screen = Overlay.Queue },
                )
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
