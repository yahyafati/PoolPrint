package com.yahyafati.poolprint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.yahyafati.poolprint.app.AppDependencies
import com.yahyafati.poolprint.pdf.PdfDocumentSource
import com.yahyafati.poolprint.ui.HomeScreen
import com.yahyafati.poolprint.ui.OnboardingModel
import com.yahyafati.poolprint.ui.OnboardingScreen
import com.yahyafati.poolprint.ui.PoolPrintTheme
import com.yahyafati.poolprint.ui.PrintModel
import com.yahyafati.poolprint.ui.PrintScreen
import com.yahyafati.poolprint.ui.SettingsModel
import com.yahyafati.poolprint.ui.SettingsScreen

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
        var showSettings by remember { mutableStateOf(false) }

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
            showSettings -> {
                val model = remember { SettingsModel(deps, scope) }
                SettingsScreen(model, onClose = { showSettings = false })
            }
            document != null -> {
                val doc = document!!
                val model = remember(doc) { PrintModel(deps, doc, scope) }
                PrintScreen(model, onClose = { document = null }, onShowKey = { showSettings = true })
            }
            else -> HomeScreen(deps, onDocument = { document = it }, onSettings = { showSettings = true })
        }
    }
}
