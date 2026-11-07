package com.yahyafati.poolprint.desktop

import com.yahyafati.poolprint.pdf.pickedPdf
import com.yahyafati.poolprint.pdf.pickerFilePattern
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopStartupTest {
    private val dir: File = Files.createTempDirectory("startup").toFile().also { it.deleteOnExit() }
    private val open = mutableListOf<AutoCloseable>()

    @AfterTest
    fun tearDown() {
        open.asReversed().forEach { runCatching { it.close() } }
        dir.deleteRecursively()
    }

    private fun pdf(name: String) = File(dir, name).also { it.writeBytes("%PDF-1.4".toByteArray()) }

    private fun start(os: DesktopOs, incoming: IncomingPdfs, vararg args: String): StartupResult =
        startDesktop(os, arrayOf(*args), incoming, File(dir, "data")).also { r ->
            (r as? StartupResult.Run)?.instance?.let { open += it }
        }

    private fun waitFor(condition: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000
        while (!condition()) {
            check(System.nanoTime() < end) { "condition not met in time" }
            Thread.sleep(20)
        }
    }

    // --- arguments --------------------------------------------------------------------------------------------

    @Test
    fun `a pdf on the command line is offered before the window exists on macOS`() {
        val incoming = IncomingPdfs()
        val result = start(DesktopOs.MacOs, incoming, pdf("a.pdf").path)
        assertNull(assertIs<StartupResult.Run>(result).instance) // macOS has no hand-over: LaunchServices keeps one process
        assertEquals("a.pdf", incoming.pending.value?.displayName)
    }

    @Test
    fun `the first pdf among the arguments wins and junk is ignored`() {
        val incoming = IncomingPdfs()
        start(DesktopOs.MacOs, incoming, "-psn_0_12345", "", "missing.pdf", pdf("notes.txt").path, pdf("a.pdf").path, pdf("b.pdf").path)
        assertEquals("a.pdf", incoming.pending.value?.displayName)
    }

    @Test
    fun `no arguments and no pdf arguments leave nothing pending`() {
        val incoming = IncomingPdfs()
        start(DesktopOs.MacOs, incoming)
        start(DesktopOs.MacOs, incoming, pdf("notes.txt").path)
        assertNull(incoming.pending.value)
    }

    @Test
    fun `the first Windows instance runs, listens and takes its own arguments`() {
        val incoming = IncomingPdfs()
        val result = start(DesktopOs.Windows, incoming, pdf("a.pdf").path)
        assertNotNull(assertIs<StartupResult.Run>(result).instance)
        assertEquals("a.pdf", incoming.pending.value?.displayName)
        assertNotNull(HandOverEndpoint.read(File(dir, "data")))
    }

    // --- Windows hand-over ------------------------------------------------------------------------------------

    @Test
    fun `a second Windows instance hands its pdf over and exits`() {
        val first = IncomingPdfs()
        assertIs<StartupResult.Run>(start(DesktopOs.Windows, first))
        val second = IncomingPdfs()
        val result = start(DesktopOs.Windows, second, pdf("late.pdf").path)
        assertEquals(0, assertIs<StartupResult.Exit>(result).status)
        waitFor { first.pending.value != null }
        assertEquals("late.pdf", first.pending.value?.displayName)
        assertNull(second.pending.value) // the second instance never opens a document itself
    }

    @Test
    fun `a second Windows instance without a pdf exits quietly and changes nothing`() {
        val first = IncomingPdfs()
        assertIs<StartupResult.Run>(start(DesktopOs.Windows, first))
        val result = assertIs<StartupResult.Exit>(start(DesktopOs.Windows, IncomingPdfs(), pdf("notes.txt").path))
        assertEquals(0, result.status)
        assertNull(result.message)
        assertNull(first.pending.value)
    }

    @Test
    fun `a second Windows instance reports when the first cannot be reached`() {
        // Lock held, but no listener was ever started (or it died): the second one must still not open the store.
        val holder = assertNotNull(SingleInstance.tryAcquire(File(dir, "data"))).also { open += it }
        val result = start(DesktopOs.Windows, IncomingPdfs(), pdf("a.pdf").path)
        val exit = assertIs<StartupResult.Exit>(result)
        assertEquals(1, exit.status)
        assertTrue(exit.message.orEmpty().contains("a.pdf"))
        holder.close()
    }

    // --- picker -----------------------------------------------------------------------------------------------

    @Test
    fun `only Windows gets a file name pattern for the native dialog`() {
        assertEquals("*.pdf", pickerFilePattern(DesktopOs.Windows))
        assertNull(pickerFilePattern(DesktopOs.MacOs))
    }

    @Test
    fun `the picker result is re-checked`() {
        val ok = pdf("ok.pdf")
        assertEquals("ok.pdf", pickedPdf(dir.path, ok.name)?.displayName)
        assertNull(pickedPdf(dir.path, pdf("typed-anyway.txt").name))
        assertNull(pickedPdf(dir.path, "gone.pdf"))
        assertNull(pickedPdf(null, null)) // cancelled
        assertNull(pickedPdf(dir.path, null))
    }
}
