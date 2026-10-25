package com.yahyafati.poolprint.pdf

import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.RandomAccessReadBuffer

/** Desktop page counting with PDFBox (D-02). Files are read in place; other sources are buffered in memory. */
class PdfBoxPageCounter : PdfPageCounter {
    override suspend fun count(document: PdfDocumentSource): Int? = withContext(Dispatchers.IO) {
        try {
            val file = (document as? FilePdfSource)?.file
            if (file != null) {
                Loader.loadPDF(file).use { it.numberOfPages }
            } else {
                document.open().use { source ->
                    Loader.loadPDF(RandomAccessReadBuffer(source.asInputStream())).use { it.numberOfPages }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // corrupt, encrypted or not a PDF: the UI shows "? pages" and printing stays possible
        }
    }
}

private fun com.yahyafati.poolprint.transport.ByteSource.asInputStream(): InputStream = object : InputStream() {
    override fun read(): Int {
        val one = ByteArray(1)
        while (true) {
            val n = this@asInputStream.read(one)
            if (n < 0) return -1
            if (n > 0) return one[0].toInt() and 0xff
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val buffer = if (off == 0 && len == b.size) b else ByteArray(len)
        val n = this@asInputStream.read(buffer)
        if (n > 0 && buffer !== b) buffer.copyInto(b, off, 0, n)
        return n
    }
}
