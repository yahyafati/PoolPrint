package com.yahyafati.poolprint.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import com.yahyafati.poolprint.app.AppCommands
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

/** The File menu with ⌘O (open a PDF) and ⌘P (print). Print is enabled only while the print sheet can print. */
@Composable
fun FrameWindowScope.PoolPrintMenuBar(commands: AppCommands, onOpenPdf: () -> Unit) {
    val print by commands.print.collectAsState()
    MenuBar {
        Menu(stringResource(Res.string.menu_file)) {
            Item(stringResource(Res.string.menu_open_pdf), shortcut = KeyShortcut(Key.O, meta = true), onClick = onOpenPdf)
            Item(stringResource(Res.string.menu_print), shortcut = KeyShortcut(Key.P, meta = true), enabled = print != null) {
                print?.invoke()
            }
        }
    }
}
