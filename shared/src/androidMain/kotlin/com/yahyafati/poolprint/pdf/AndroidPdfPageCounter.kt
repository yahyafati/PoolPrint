package com.yahyafati.poolprint.pdf

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android page counting with the platform `PdfRenderer` (D-02). It needs a seekable descriptor: a content
 * URI usually gives one; otherwise the document is copied to a cache file that is deleted straight after.
 */
class AndroidPdfPageCounter(private val cacheDir: File) : PdfPageCounter {
    override suspend fun count(document: PdfDocumentSource): Int? = withContext(Dispatchers.IO) {
        try {
            (document as? UriPdfSource)?.openFileDescriptor()?.let { fd -> countOrNull(fd) } ?: countViaCacheFile(document)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Closes [fd]. A renderer failure (not seekable, corrupt, password-protected) gives null. */
    private fun countOrNull(fd: ParcelFileDescriptor): Int? = try {
        fd.use { PdfRenderer(it).use { renderer -> renderer.pageCount } }
    } catch (e: Exception) {
        null
    }

    private fun countViaCacheFile(document: PdfDocumentSource): Int? {
        val file = File.createTempFile("pagecount", ".pdf", cacheDir)
        try {
            document.open().use { source ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                    }
                }
            }
            return countOrNull(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        } finally {
            file.delete()
        }
    }
}
