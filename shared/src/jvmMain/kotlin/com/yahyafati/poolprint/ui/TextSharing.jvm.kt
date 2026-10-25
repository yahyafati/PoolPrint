package com.yahyafati.poolprint.ui

import androidx.compose.runtime.Composable

@Composable
actual fun rememberTextSharer(): ((String) -> Unit)? = null

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
actual suspend fun androidx.compose.ui.platform.Clipboard.setText(text: String) {
    setClipEntry(androidx.compose.ui.platform.ClipEntry(java.awt.datatransfer.StringSelection(text)))
}
