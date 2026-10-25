package com.yahyafati.poolprint.pdf

/** Counts a PDF's pages on the device: PdfRenderer on Android, PDFBox on the desktop (D-02). */
interface PdfPageCounter {
    /** The page count, or null when the file can't be read (corrupt, encrypted, not a PDF). Never throws. */
    suspend fun count(document: PdfDocumentSource): Int?
}
