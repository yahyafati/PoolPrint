package com.yahyafati.poolprint.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

@Composable
actual fun rememberPdfPicker(onPicked: (PdfDocumentSource) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    return {
        SwingUtilities.invokeLater {
            // AWT's FileDialog is the native macOS open panel.
            val dialog = FileDialog(null as Frame?, "Choose a PDF", FileDialog.LOAD).apply {
                setFilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
                isVisible = true
            }
            val name = dialog.file
            val dir = dialog.directory
            if (name != null && dir != null) currentOnPicked(FilePdfSource(File(dir, name)))
        }
    }
}
