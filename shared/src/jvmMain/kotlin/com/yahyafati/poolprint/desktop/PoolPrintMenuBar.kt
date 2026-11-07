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

/** The menu shortcut for [key]: ⌘ on macOS, Ctrl on Windows. */
internal fun menuShortcut(os: DesktopOs, key: Key): KeyShortcut =
    when (os) {
        DesktopOs.MacOs -> KeyShortcut(key, meta = true)
        DesktopOs.Windows -> KeyShortcut(key, ctrl = true)
    }

/**
 * The File menu with Open PDF (⌘O / Ctrl+O) and Print (⌘P / Ctrl+P). Print is enabled only while the print sheet
 * can print. On macOS the menu is the system menu bar; on Windows Compose draws it inside the window.
 */
@Composable
fun FrameWindowScope.PoolPrintMenuBar(os: DesktopOs, commands: AppCommands, onOpenPdf: () -> Unit) {
    val print by commands.print.collectAsState()
    MenuBar {
        Menu(stringResource(Res.string.menu_file)) {
            Item(stringResource(Res.string.menu_open_pdf), shortcut = menuShortcut(os, Key.O), onClick = onOpenPdf)
            Item(stringResource(Res.string.menu_print), shortcut = menuShortcut(os, Key.P), enabled = print != null) {
                print?.invoke()
            }
        }
    }
}
