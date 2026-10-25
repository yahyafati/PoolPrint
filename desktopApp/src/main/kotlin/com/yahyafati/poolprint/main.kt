package com.yahyafati.poolprint

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yahyafati.poolprint.app.createDesktopAppDependencies

fun main() = application {
    val deps = remember { createDesktopAppDependencies() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pool Print",
        state = rememberWindowState(width = 560.dp, height = 800.dp),
    ) {
        App(deps)
    }
}
