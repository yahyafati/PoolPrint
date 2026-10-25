package com.yahyafati.poolprint.desktop

import com.yahyafati.poolprint.pdf.FilePdfSource
import com.yahyafati.poolprint.pdf.PdfDocumentSource
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** True for a readable regular file named `*.pdf`; the content is not inspected (a broken PDF still prints as "? pages"). */
internal fun isPdfFile(file: File): Boolean =
    file.isFile && file.canRead() && file.name.endsWith(".pdf", ignoreCase = true)

/** The files in a drag-and-drop [Transferable] (Finder offers a file list); empty for anything else. */
internal fun filesIn(transferable: Transferable): List<File> =
    try {
        if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) emptyList()
        else (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>).orEmpty().filterIsInstance<File>()
    } catch (_: Exception) {
        emptyList()
    }

/**
 * PDFs handed to the app from outside its window: dropped on it, or sent by Finder's "Open With".
 * They can arrive before the window exists, so the latest one is held until the UI takes it.
 * Only one document is open at a time: of several files, the first PDF wins.
 */
class IncomingPdfs {
    private val latest = MutableStateFlow<PdfDocumentSource?>(null)
    val pending: StateFlow<PdfDocumentSource?> = latest.asStateFlow()

    /** Takes the first PDF in [files]; false (and nothing changes) if there is none. */
    fun offer(files: List<File>): Boolean {
        val pdf = files.firstOrNull(::isPdfFile) ?: return false
        latest.value = FilePdfSource(pdf)
        return true
    }

    /** Hands over a document that is already built, e.g. from the file picker behind ⌘O. */
    fun offer(document: PdfDocumentSource) {
        latest.value = document
    }

    /** Called once the UI has opened [source], so it isn't delivered again. A newer document stays pending. */
    fun taken(source: PdfDocumentSource) {
        latest.compareAndSet(source, null)
    }

    /**
     * Registers for Finder's "Open With" and for files opened while the app is closed (macOS queues those
     * until a handler exists, so call this early in `main`). Returns false where the platform has no such event.
     */
    fun installOpenFileHandler(): Boolean {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) return false
        Desktop.getDesktop().setOpenFileHandler { event -> offer(event.files) }
        return true
    }
}
