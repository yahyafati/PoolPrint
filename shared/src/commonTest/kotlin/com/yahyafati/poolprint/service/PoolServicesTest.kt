package com.yahyafati.poolprint.service

import com.yahyafati.poolprint.core.model.PrintJob
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.fakes.FakeHostKeyStore
import com.yahyafati.poolprint.fakes.FakeSettings
import com.yahyafati.poolprint.fakes.FakeSshClient.Script
import com.yahyafati.poolprint.fakes.FakeSshClientFactory
import com.yahyafati.poolprint.fakes.user
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.transport.ByteArraySource
import com.yahyafati.poolprint.transport.PoolServer
import com.yahyafati.poolprint.transport.SshError
import com.yahyafati.poolprint.transport.SshResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PoolServicesTest {
    private val factory = FakeSshClientFactory()
    private val client = factory.client
    private val hostKeys = FakeHostKeyStore()
    private val settings = FakeSettings(user())
    private val services = PoolServices(settings, hostKeys, factory)
    private val job = PrintJob(PrintOptions(Printers.all[0], copies = 2), "lecture.pdf")
    private val pdf = "%PDF-1.7 fake".encodeToByteArray()

    // --- test connection ---

    @Test
    fun `test connection succeeds on ok`() = runTest {
        client.respond(Script.Result(SshResult(0, "ok\n", "")))
        assertEquals(TestConnectionResult.Success, services.testConnection())
        assertEquals("'echo' 'ok'", client.calls.single().command)
        assertEquals(user(), factory.configs.single().username)
    }

    @Test
    fun `test connection on first contact asks to trust the key`() = runTest {
        client.respond(Script.Error(SshError.HostKeyUnknown("ssh-ed25519", "SHA256:abc")))
        assertEquals(TestConnectionResult.NeedsTrust(PinnedHostKey("ssh-ed25519", "SHA256:abc")), services.testConnection())
    }

    @Test
    fun `trusting pins the key for the pool host`() = runTest {
        val key = PinnedHostKey("ssh-ed25519", "SHA256:abc")
        services.trust(key)
        assertEquals(key, hostKeys.get(PoolServer.HOST, PoolServer.PORT))
    }

    @Test
    fun `test connection reports AuthRejected`() = runTest {
        client.respond(Script.Error(SshError.AuthRejected))
        assertEquals(FailureKind.AuthRejected, (services.testConnection() as TestConnectionResult.Failed).failure.kind)
    }

    @Test
    fun `test connection reports HostKeyChanged with both fingerprints`() = runTest {
        client.respond(Script.Error(SshError.HostKeyChanged("SHA256:old", "SHA256:new")))
        val failure = (services.testConnection() as TestConnectionResult.Failed).failure
        assertEquals(FailureKind.HostKeyChanged, failure.kind)
        assertEquals("SHA256:old", failure.expectedFingerprint)
        assertEquals("SHA256:new", failure.actualFingerprint)
    }

    @Test
    fun `test connection maps unreachable and timeout`() = runTest {
        client.respond(Script.Error(SshError.HostUnreachable))
        assertEquals(FailureKind.HostUnreachable, (services.testConnection() as TestConnectionResult.Failed).failure.kind)
        client.respond(Script.Error(SshError.Timeout))
        assertEquals(FailureKind.Timeout, (services.testConnection() as TestConnectionResult.Failed).failure.kind)
    }

    @Test
    fun `test connection flags unexpected output and a failing command`() = runTest {
        client.respond(Script.Result(SshResult(0, "Welcome to the pool\n", "")))
        assertEquals(FailureKind.UnexpectedOutput, (services.testConnection() as TestConnectionResult.Failed).failure.kind)
        client.respond(Script.Result(SshResult(1, "", "boom")))
        val failure = (services.testConnection() as TestConnectionResult.Failed).failure
        assertEquals(FailureKind.CommandFailed, failure.kind)
        assertEquals("boom", failure.details)
    }

    @Test
    fun `without a username nothing is sent`() = runTest {
        val empty = PoolServices(FakeSettings(null), hostKeys, factory)
        assertEquals(FailureKind.NotConfigured, (empty.testConnection() as TestConnectionResult.Failed).failure.kind)
        assertTrue(client.calls.isEmpty())
    }

    // --- printing ---

    @Test
    fun `print streams the pdf into lpr stdin and parses the job id`() = runTest {
        client.respond(Script.Result(SshResult(0, "request id is tfppr1-123 (1 file(s))\n", "")))
        val progress = mutableListOf<Long>()
        val result = services.print(job, ByteArraySource(pdf)) { progress += it }
        assertEquals(PrintResult.Sent("tfppr1", "tfppr1-123"), result)
        val call = client.calls.single()
        assertEquals("'lpr' '-P' 'tfppr1' '-#' '2'", call.command)
        assertTrue(pdf.contentEquals(call.stdin))
        assertEquals(pdf.size.toLong(), progress.last())
    }

    @Test
    fun `print succeeds without a parsable job id`() = runTest {
        client.respond(Script.Result(SshResult(0, "", "")))
        assertEquals(PrintResult.Sent("tfppr1", null), services.print(job, ByteArraySource(pdf)) {})
    }

    @Test
    fun `print with HostKeyUnknown asks to trust and sends nothing`() = runTest {
        client.respond(Script.Error(SshError.HostKeyUnknown("ssh-ed25519", "SHA256:abc")))
        assertIs<PrintResult.NeedsTrust>(services.print(job, ByteArraySource(pdf)) {})
    }

    @Test
    fun `print with a changed host key fails with both fingerprints`() = runTest {
        client.respond(Script.Error(SshError.HostKeyChanged("SHA256:old", "SHA256:new")))
        val failure = (services.print(job, ByteArraySource(pdf)) {} as PrintResult.Failed).failure
        assertEquals(FailureKind.HostKeyChanged, failure.kind)
    }

    @Test
    fun `print with AuthRejected fails`() = runTest {
        client.respond(Script.Error(SshError.AuthRejected))
        assertEquals(FailureKind.AuthRejected, (services.print(job, ByteArraySource(pdf)) {} as PrintResult.Failed).failure.kind)
    }

    @Test
    fun `non-zero exit keeps stderr and the exit code`() = runTest {
        client.respond(Script.Result(SshResult(1, "", "lpr: something odd\n")))
        val failure = (services.print(job, ByteArraySource(pdf)) {} as PrintResult.Failed).failure
        assertEquals(FailureKind.PrintFailed, failure.kind)
        assertEquals(1, failure.exitCode)
        assertEquals("lpr: something odd", failure.details)
    }

    @Test
    fun `stderr is matched best-effort into catalogue rows`() {
        fun kind(exit: Int, stderr: String) = ErrorCatalogue.fromPrintExit(exit, stderr, "tfppr1").kind
        assertEquals(FailureKind.UnknownPrinter, kind(1, "lpr: The printer or class does not exist."))
        assertEquals(FailureKind.UnknownPrinter, kind(1, "lpr: Unknown printer tfppr9"))
        assertEquals(FailureKind.QuotaExceeded, kind(1, "lpr: job quota exceeded"))
        assertEquals(FailureKind.LprMissing, kind(127, ""))
        assertEquals(FailureKind.LprMissing, kind(1, "bash: lpr: command not found"))
        assertEquals(FailureKind.PrintFailed, kind(2, "garbage"))
    }

    @Test
    fun `the document is closed after printing`() = runTest {
        var closed = false
        val source = object : com.yahyafati.poolprint.transport.ByteSource {
            override fun read(buffer: ByteArray) = -1
            override fun close() { closed = true }
        }
        services.print(job, source) {}
        assertTrue(closed)
    }

    @Test
    fun `cancelling while the server hangs cancels the print and reports nothing`() = runTest {
        client.respond(Script.Hang)
        var result: PrintResult? = null
        val deferred = launch(start = CoroutineStart.UNDISPATCHED) { result = services.print(job, ByteArraySource(pdf)) {} }
        advanceUntilIdle()
        deferred.cancel()
        advanceUntilIdle()
        assertTrue(deferred.isCancelled)
        assertNull(result)
    }

    @Test
    fun `job id parser`() {
        assertEquals("tfppr2-4711", PoolServices.parseJobId("request id is tfppr2-4711 (1 file(s))"))
        assertNull(PoolServices.parseJobId("nothing here"))
        assertNull(PoolServices.parseJobId("request id is ; rm -rf"))
    }
}
