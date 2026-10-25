package com.yahyafati.poolprint.pdf

import com.yahyafati.poolprint.fakes.FakeDocument
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage

class PdfBoxPageCounterTest {
    private val counter = PdfBoxPageCounter()

    private fun pdfBytes(pages: Int): ByteArray = ByteArrayOutputStream().also { out ->
        PDDocument().use { doc ->
            repeat(pages) { doc.addPage(PDPage()) }
            doc.save(out)
        }
    }.toByteArray()

    private fun tempFile(bytes: ByteArray) = File.createTempFile("count", ".pdf").also { it.deleteOnExit(); it.writeBytes(bytes) }

    @Test
    fun `counts the pages of a file`() = runBlocking {
        assertEquals(7, counter.count(FilePdfSource(tempFile(pdfBytes(7)))))
        assertEquals(1, counter.count(FilePdfSource(tempFile(pdfBytes(1)))))
    }

    @Test
    fun `counts the pages of a source that is not a file`() = runBlocking {
        assertEquals(12, counter.count(FakeDocument("x.pdf", pdfBytes(12))))
    }

    @Test
    fun `a corrupt file or one that is not a pdf has no count`() = runBlocking {
        assertNull(counter.count(FilePdfSource(tempFile("definitely not a pdf".encodeToByteArray()))))
        assertNull(counter.count(FilePdfSource(tempFile(pdfBytes(5).copyOf(40)))))
        assertNull(counter.count(FakeDocument("x.pdf", ByteArray(0))))
    }

    @Test
    fun `a missing file has no count`() = runBlocking {
        assertNull(counter.count(FilePdfSource(File("/nonexistent/none.pdf"))))
    }
}
