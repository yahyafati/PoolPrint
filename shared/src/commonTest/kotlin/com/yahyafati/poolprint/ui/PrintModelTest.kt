package com.yahyafati.poolprint.ui

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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PrintModelTest {
    private val t = TestDeps(user())
    private val doc = FakeDocument("slides; rm -rf ~.pdf", "%PDF-bytes".encodeToByteArray())
    private fun TestScope.model() = PrintModel(t.deps, doc, this)

    @Test
    fun `prints to the default printer with one copy`() = runTest {
        t.client.respond(Script.Result(SshResult(0, "request id is tfppr1-9 (1 file(s))\n", "")))
        val m = model()
        m.print(); advanceUntilIdle()
        assertEquals(PrintPhase.Sent("tfppr1", "tfppr1-9"), m.state.value.phase)
        val call = t.client.calls.single()
        assertEquals("'lpr' '-P' 'tfppr1'", call.command)
        assertContentEquals("%PDF-bytes".encodeToByteArray(), call.stdin)
    }

    @Test
    fun `printer and copies reach the command and the filename never does`() = runTest {
        val m = model()
        m.selectPrinter("tfppr4"); m.setCopies(3)
        m.print(); advanceUntilIdle()
        val command = t.client.calls.single().command
        assertEquals("'lpr' '-P' 'tfppr4' '-#' '3'", command)
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
}
