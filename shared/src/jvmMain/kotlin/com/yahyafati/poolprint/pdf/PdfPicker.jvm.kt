package com.yahyafati.poolprint.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.yahyafati.poolprint.desktop.DesktopOs
import com.yahyafati.poolprint.desktop.isPdfFile
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

/**
 * The initial file-name pattern that makes the native dialog show PDFs only. Windows' dialog ignores
 * `FilenameFilter`, but treats a wildcard in the file name as its filter; macOS honours the filter and would
 * show the pattern as a literal name, so it gets none.
 */
internal fun pickerFilePattern(os: DesktopOs): String? = if (os == DesktopOs.Windows) "*.pdf" else null

/** The picked PDF, or null if the dialog was cancelled or the file isn't a readable `*.pdf` (the filter is only a hint). */
internal fun pickedPdf(directory: String?, name: String?): PdfDocumentSource? {
    if (directory == null || name == null) return null
    val file = File(directory, name)
    return if (isPdfFile(file)) FilePdfSource(file) else null
}

@Composable
actual fun rememberPdfPicker(onPicked: (PdfDocumentSource) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    return {
        SwingUtilities.invokeLater {
            // AWT's FileDialog is the native open panel on both macOS and Windows.
            val dialog = FileDialog(null as Frame?, "Choose a PDF", FileDialog.LOAD).apply {
                setFilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
                pickerFilePattern(DesktopOs.current())?.let { file = it }
                isVisible = true
            }
            pickedPdf(dialog.directory, dialog.file)?.let(currentOnPicked)
        }
    }
}
