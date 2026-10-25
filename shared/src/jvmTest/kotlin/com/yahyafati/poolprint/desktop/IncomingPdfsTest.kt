package com.yahyafati.poolprint.desktop

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncomingPdfsTest {
    private val dir: File = Files.createTempDirectory("incoming").toFile().also { it.deleteOnExit() }

    private fun file(name: String, bytes: ByteArray = "%PDF-1.4".toByteArray()) =
        File(dir, name).also { it.writeBytes(bytes); it.deleteOnExit() }

    private fun fileList(files: List<File>) = object : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
        override fun getTransferData(flavor: DataFlavor): Any = files
    }

    @Test
    fun `offer takes a pdf and keeps its name`() {
        val incoming = IncomingPdfs()
        assertTrue(incoming.offer(listOf(file("scan.pdf"))))
        assertEquals("scan.pdf", incoming.pending.value?.displayName)
    }

    @Test
    fun `offer accepts an upper case extension`() {
        assertTrue(IncomingPdfs().offer(listOf(file("SLIDES.PDF"))))
    }

    @Test
    fun `offer takes the first pdf of several files`() {
        val incoming = IncomingPdfs()
        assertTrue(incoming.offer(listOf(file("notes.txt"), file("a.pdf"), file("b.pdf"))))
        assertEquals("a.pdf", incoming.pending.value?.displayName)
    }

    @Test
    fun `offer rejects non pdf files, missing files and directories`() {
        val incoming = IncomingPdfs()
        val folder = File(dir, "looks-like.pdf").also { it.mkdir(); it.deleteOnExit() }
        assertFalse(incoming.offer(listOf(file("image.png"), File(dir, "missing.pdf"), folder)))
        assertFalse(incoming.offer(emptyList()))
        assertNull(incoming.pending.value)
    }

    @Test
    fun `a rejected offer leaves an earlier document pending`() {
        val incoming = IncomingPdfs()
        incoming.offer(listOf(file("first.pdf")))
        assertFalse(incoming.offer(listOf(file("other.txt"))))
        assertEquals("first.pdf", incoming.pending.value?.displayName)
    }

    @Test
    fun `a newer offer replaces an older pending one`() {
        val incoming = IncomingPdfs()
        incoming.offer(listOf(file("first.pdf")))
        incoming.offer(listOf(file("second.pdf")))
        assertEquals("second.pdf", incoming.pending.value?.displayName)
    }

    @Test
    fun `taken clears the document that was delivered`() {
        val incoming = IncomingPdfs()
        incoming.offer(listOf(file("first.pdf")))
        incoming.taken(assertNotNull(incoming.pending.value))
        assertNull(incoming.pending.value)
    }

    @Test
    fun `taken does not clear a document that arrived meanwhile`() {
        val incoming = IncomingPdfs()
        incoming.offer(listOf(file("first.pdf")))
        val delivered = assertNotNull(incoming.pending.value)
        incoming.offer(listOf(file("second.pdf")))
        incoming.taken(delivered)
        assertEquals("second.pdf", incoming.pending.value?.displayName)
    }

    @Test
    fun `filesIn reads a dragged file list`() {
        val a = file("a.pdf")
        val b = file("b.txt")
        assertEquals(listOf(a, b), filesIn(fileList(listOf(a, b))))
    }

    @Test
    fun `filesIn is empty for dragged text`() {
        assertEquals(emptyList(), filesIn(StringSelection("hello")))
    }
}
