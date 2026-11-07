package com.yahyafati.poolprint

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yahyafati.poolprint.app.AppCommands
import com.yahyafati.poolprint.app.createDesktopAppDependencies
import com.yahyafati.poolprint.desktop.DesktopOs
import com.yahyafati.poolprint.desktop.IncomingPdfs
import com.yahyafati.poolprint.desktop.PdfDropTarget
import com.yahyafati.poolprint.desktop.PoolPrintMenuBar
import com.yahyafati.poolprint.desktop.UnsupportedOsException
import com.yahyafati.poolprint.pdf.rememberPdfPicker
import kotlin.system.exitProcess

fun main() {
    // Only macOS and Windows are supported (D-25): refuse anything else before a window or a store is touched.
    val os = try {
        DesktopOs.current()
    } catch (e: UnsupportedOsException) {
        System.err.println(e.message)
        exitProcess(1)
    }
    // Registered before the window exists: files opened from Finder while the app is closed arrive here.
    val incoming = IncomingPdfs().also { it.installOpenFileHandler() }
    application {
        val deps = remember { createDesktopAppDependencies(os) }
        val commands = remember { AppCommands() }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Pool Print",
            state = rememberWindowState(width = 560.dp, height = 800.dp),
        ) {
            window.minimumSize = java.awt.Dimension(420, 560)
            val pending by incoming.pending.collectAsState()
            val pick = rememberPdfPicker(incoming::offer)
            PoolPrintMenuBar(os, commands, onOpenPdf = pick)
            PdfDropTarget(onFiles = { incoming.offer(it) }) {
                App(deps, appVersion = BuildInfo.VERSION_NAME, incomingDocument = pending, onIncomingHandled = { pending?.let(incoming::taken) }, commands = commands)
            }
        }
    }
}
