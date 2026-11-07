package com.yahyafati.poolprint

import androidx.compose.runtime.LaunchedEffect
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
import com.yahyafati.poolprint.desktop.StartupResult
import com.yahyafati.poolprint.desktop.UnsupportedOsException
import com.yahyafati.poolprint.desktop.startDesktop
import com.yahyafati.poolprint.pdf.rememberPdfPicker
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Only macOS and Windows are supported (D-25): refuse anything else before a window or a store is touched.
    val os = try {
        DesktopOs.current()
    } catch (e: UnsupportedOsException) {
        System.err.println(e.message)
        exitProcess(1)
    }
    val incoming = IncomingPdfs()
    // Command-line files go to `incoming`; on Windows a second copy hands its file to the running one and exits here,
    // before the DataStore is opened (D-28).
    when (val start = startDesktop(os, args, incoming)) {
        is StartupResult.Exit -> {
            start.message?.let(System.err::println)
            exitProcess(start.status)
        }
        is StartupResult.Run -> Unit
    }
    // Registered before the window exists: files opened from Finder while the app is closed arrive here.
    incoming.installOpenFileHandler()
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
            // A file handed over by a second copy (or dropped from elsewhere) should be seen: bring the window forward.
            LaunchedEffect(pending) {
                if (pending != null) {
                    if (window.state == java.awt.Frame.ICONIFIED) window.state = java.awt.Frame.NORMAL
                    window.toFront()
                }
            }
            val pick = rememberPdfPicker(incoming::offer)
            PoolPrintMenuBar(os, commands, onOpenPdf = pick)
            PdfDropTarget(onFiles = { incoming.offer(it) }) {
                App(deps, appVersion = BuildInfo.VERSION_NAME, incomingDocument = pending, onIncomingHandled = { pending?.let(incoming::taken) }, commands = commands)
            }
        }
    }
}
