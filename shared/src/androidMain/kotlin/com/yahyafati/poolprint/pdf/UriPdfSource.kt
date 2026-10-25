package com.yahyafati.poolprint.pdf

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.yahyafati.poolprint.transport.ByteSource
import java.io.InputStream

/** A PDF behind a content URI (share sheet or the system picker). No storage permission is needed. */
class UriPdfSource(private val resolver: ContentResolver, private val uri: Uri) : PdfDocumentSource {
    private val columns: Pair<String?, Long?> = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(c::getString)
            val size = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong)
            name to size
        }
    }.getOrNull() ?: (null to null)

    override val displayName: String = columns.first ?: uri.lastPathSegment ?: "document.pdf"
    override val sizeBytes: Long? = columns.second

    override fun open(): ByteSource {
        val stream = resolver.openInputStream(uri) ?: throw java.io.FileNotFoundException("Cannot open the document")
        return StreamByteSource(stream)
    }
}

private class StreamByteSource(private val stream: InputStream) : ByteSource {
    override fun read(buffer: ByteArray): Int = stream.read(buffer)
    override fun close() = stream.close()
}
