package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.Orientation
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.model.PaperSize
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.fakes.FakeDocument
import com.yahyafati.poolprint.fakes.FakeSshClient.Script
import com.yahyafati.poolprint.fakes.TestDeps
import com.yahyafati.poolprint.fakes.user
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.service.FailureKind
import com.yahyafati.poolprint.transport.PoolServer
import com.yahyafati.poolprint.transport.SshError
import com.yahyafati.poolprint.transport.SshResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrintModelTest {
    private val t = TestDeps(user())
    private val DEFAULT_COMMAND = "'lpr' '-P' 'tfppr1' '-o' 'sides=one-sided' '-o' 'media=A4'"
    private val doc = FakeDocument("slides; rm -rf ~.pdf", "%PDF-bytes".encodeToByteArray())
    private fun TestScope.model() = PrintModel(t.deps, doc, this)

    @Test
    fun `prints to the default printer with one copy`() = runTest {
        t.client.respond(Script.Result(SshResult(0, "request id is tfppr1-9 (1 file(s))\n", "")))
        val m = model()
        m.print(); advanceUntilIdle()
        assertEquals(PrintPhase.Sent("tfppr1", "tfppr1-9"), m.state.value.phase)
        val call = t.client.calls.single()
        assertEquals(DEFAULT_COMMAND, call.command)
        assertContentEquals("%PDF-bytes".encodeToByteArray(), call.stdin)
    }

    @Test
    fun `printer and copies reach the command and the filename never does`() = runTest {
        val m = model()
        m.selectPrinter("tfppr4"); m.setCopies(3)
        m.print(); advanceUntilIdle()
        val command = t.client.calls.single().command
        assertEquals("'lpr' '-P' 'tfppr4' '-#' '3' '-o' 'sides=one-sided' '-o' 'media=A4'", command)
        assertTrue("rm" !in command && "slides" !in command)
    }

    @Test
    fun `copies stay between one and the cap, and unknown printers are ignored`() = runTest {
        val m = model()
        m.decrementCopies()
        assertEquals(1, m.state.value.copies)
        m.setCopies(20); m.incrementCopies()
        assertEquals(20, m.state.value.copies)
        m.setCopies(0); m.setCopies(99)
        assertEquals(20, m.state.value.copies)
        m.selectPrinter("tfppr1;id"); m.selectPrinter("nope")
        assertEquals("tfppr1", m.state.value.printer.name)
    }

    @Test
    fun `a remote failure keeps stderr and can be dismissed`() = runTest {
        t.client.respond(Script.Result(SshResult(1, "", "lpr: The printer or class does not exist.")))
        val m = model()
        m.print(); advanceUntilIdle()
        val failed = assertIs<PrintPhase.Failed>(m.state.value.phase)
        assertEquals(FailureKind.UnknownPrinter, failed.failure.kind)
        assertEquals("lpr: The printer or class does not exist.", failed.failure.details)
        m.dismissFailure()
        assertEquals(PrintPhase.Idle, m.state.value.phase)
    }

    @Test
    fun `an unknown host key asks, and trusting retries the print with a fresh stream`() = runTest {
        t.client.respond(
            Script.Error(SshError.HostKeyUnknown("ssh-ed25519", "SHA256:abc")),
            Script.Result(SshResult(0, "request id is tfppr1-2 (1 file(s))", "")),
        )
        val m = model()
        m.print(); advanceUntilIdle()
        assertEquals(PrintPhase.AskTrust(PinnedHostKey("ssh-ed25519", "SHA256:abc")), m.state.value.phase)
        assertTrue(t.hostKeys.pins.isEmpty())
        m.trustAndRetry(); advanceUntilIdle()
        assertEquals(PinnedHostKey("ssh-ed25519", "SHA256:abc"), t.hostKeys.get(PoolServer.HOST, PoolServer.PORT))
        assertIs<PrintPhase.Sent>(m.state.value.phase)
        assertEquals(2, doc.opened)
        assertContentEquals("%PDF-bytes".encodeToByteArray(), t.client.calls.last().stdin)
    }

    @Test
    fun `cancel during the upload aborts and returns to idle`() = runTest {
        t.client.respond(Script.Hang)
        val m = model()
        m.print(); advanceUntilIdle()
        assertIs<PrintPhase.Uploading>(m.state.value.phase)
        m.cancel(); advanceUntilIdle()
        assertEquals(PrintPhase.Idle, m.state.value.phase)
        assertTrue(m.state.value.canPrint)
    }

    @Test
    fun `a second tap on print while uploading is ignored`() = runTest {
        t.client.respond(Script.Hang)
        val m = model()
        m.print(); advanceUntilIdle()
        m.print(); advanceUntilIdle()
        m.cancel(); advanceUntilIdle()
        assertEquals(1, t.client.calls.size) // only the first tap started a connection
    }

    @Test
    fun `counts the pages and estimates the cost`() = runTest {
        t.pageCounter.pages = 10
        val m = model()
        assertEquals(PageCount.Counting, m.state.value.pageCount)
        assertNull(m.state.value.estimate)
        advanceUntilIdle()
        assertEquals(PageCount.Known(10), m.state.value.pageCount)
        assertEquals(50, m.state.value.estimate?.costCents)
        m.selectPrinter("tfppr2"); m.setCopies(2)
        assertEquals(200, m.state.value.estimate?.costCents)
    }

    @Test
    fun `an unreadable document has no estimate and can still be printed`() = runTest {
        t.pageCounter.pages = null
        val m = model()
        advanceUntilIdle()
        assertEquals(PageCount.Unknown, m.state.value.pageCount)
        assertNull(m.state.value.estimate)
        assertTrue(m.state.value.canPrint)
        m.print(); advanceUntilIdle()
        assertIs<PrintPhase.Sent>(m.state.value.phase)
    }

    @Test
    fun `every option reaches the command`() = runTest {
        val m = model(); advanceUntilIdle()
        m.selectPrinter("tfppr2"); m.setCopies(2)
        m.setPageRanges(" 7, 1-4, 3-5 ")
        m.setDuplex(Duplex.LONG_EDGE); m.setPagesPerSheet(PagesPerSheet.TWO)
        m.setOrientation(Orientation.LANDSCAPE); m.setFitToPage(true); m.setPaperSize(PaperSize.LETTER)
        m.print(); advanceUntilIdle()
        assertEquals(
            "'lpr' '-P' 'tfppr2' '-#' '2' '-o' 'sides=two-sided-long-edge' '-o' 'number-up=2' " +
                "'-o' 'page-ranges=1-5,7' '-o' 'landscape' '-o' 'fit-to-page' '-o' 'media=Letter'",
            t.client.calls.single().command,
        )
    }

    @Test
    fun `invalid page ranges disable printing and never reach the command`() = runTest {
        val m = model(); advanceUntilIdle()
        for (bad in listOf("1-4;id", "5-3", "0", "1-4\$(id)", "99")) {
            m.setPageRanges(bad)
            assertTrue(m.state.value.pageRangesError != null, bad)
            assertFalse(m.state.value.canPrint, bad)
            assertNull(m.state.value.estimate, bad)
            m.print(); advanceUntilIdle()
        }
        assertTrue(t.client.calls.isEmpty())
        m.setPageRanges("1-4")
        assertNull(m.state.value.pageRangesError)
        assertTrue(m.state.value.canPrint)
    }

    @Test
    fun `page ranges are checked against the page count with the layout in mind`() = runTest {
        t.pageCounter.pages = 8
        val m = model(); advanceUntilIdle()
        m.setPageRanges("5-8")
        assertNull(m.state.value.pageRangesError)
        // Two per sheet leaves 4 output pages, so 5-8 no longer exists (output-page semantics).
        m.setPagesPerSheet(PagesPerSheet.TWO)
        assertEquals(ValidationError.PageRangeBeyondDocument(4), m.state.value.pageRangesError)
        m.setPagesPerSheet(PagesPerSheet.ONE)
        assertNull(m.state.value.pageRangesError)
        assertEquals("5-8", m.state.value.options.pageRanges?.canonical)
    }

    @Test
    fun `ranges typed while the pages are still being counted are checked once the count arrives`() = runTest {
        t.pageCounter.pages = 5
        t.pageCounter.gate = kotlinx.coroutines.CompletableDeferred()
        val m = model()
        m.setPageRanges("9")
        assertNull(m.state.value.pageRangesError)
        t.pageCounter.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(ValidationError.PageRangeBeyondDocument(5), m.state.value.pageRangesError)
    }

    @Test
    fun `the warning flag follows the threshold`() = runTest {
        t.pageCounter.pages = 41
        val m = model(); advanceUntilIdle()
        assertTrue(m.state.value.estimate!!.exceedsWarningThreshold)
        m.selectPrinter("tfppr1"); m.setPageRanges("1-40")
        assertFalse(m.state.value.estimate!!.exceedsWarningThreshold)
    }

    @Test
    fun `printing remembers printer and layout but not copies or ranges`() = runTest {
        val m = model(); advanceUntilIdle()
        m.selectPrinter("tfppr3"); m.setCopies(5); m.setPageRanges("1-2")
        m.setDuplex(Duplex.SHORT_EDGE); m.setPagesPerSheet(PagesPerSheet.FOUR); m.setPaperSize(PaperSize.A3)
        m.print(); advanceUntilIdle()
        val saved = t.settings.printDefaults
        assertEquals("tfppr3", saved.printer.name)
        assertEquals(Duplex.SHORT_EDGE, saved.duplex)
        assertEquals(PagesPerSheet.FOUR, saved.pagesPerSheet)
        assertEquals(PaperSize.A3, saved.paperSize)
        assertEquals(1, saved.copies)
        assertNull(saved.pageRanges)
    }

    @Test
    fun `the sheet opens with the remembered options`() = runTest {
        t.settings.printDefaults = PrintOptions(printer = Printers.all[3], duplex = Duplex.LONG_EDGE, orientation = Orientation.LANDSCAPE)
        val m = PrintModel(t.deps, doc, this, t.settings.getPrintDefaults())
        advanceUntilIdle()
        m.print(); advanceUntilIdle()
        assertEquals(
            "'lpr' '-P' 'tfppr4' '-o' 'sides=two-sided-long-edge' '-o' 'landscape' '-o' 'media=A4'",
            t.client.calls.single().command,
        )
    }
}
