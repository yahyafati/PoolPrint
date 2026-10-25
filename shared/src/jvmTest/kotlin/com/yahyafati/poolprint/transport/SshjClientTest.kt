package com.yahyafati.poolprint.transport

import com.yahyafati.poolprint.core.command.RemoteCommand
import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.Orientation
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.model.PaperSize
import com.yahyafati.poolprint.core.model.PrintJob
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.validation.Validators
import com.yahyafati.poolprint.core.validation.valueOrNull
import com.yahyafati.poolprint.fakes.FakeHostKeyStore
import com.yahyafati.poolprint.fakes.FakeKeyStore
import com.yahyafati.poolprint.fakes.FakeSettings
import com.yahyafati.poolprint.fakes.user
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.keys.BouncyCastleKeyGenerator
import com.yahyafati.poolprint.service.FailureKind
import com.yahyafati.poolprint.service.PoolServices
import com.yahyafati.poolprint.service.PrintResult
import com.yahyafati.poolprint.service.TestConnectionResult
import com.yahyafati.poolprint.transport.testserver.TestSshServer
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** The real [SshjClient] against an in-process SSH server on loopback. Never touches an external host. */
class SshjClientTest {
    private val keyStore = FakeKeyStore().also { store ->
        runBlocking { store.save(BouncyCastleKeyGenerator().generate("pool-print@test").privateKeyPem) }
    }
    private val hostKeys = FakeHostKeyStore()

    private fun cmd(text: String) = RemoteCommand(text)

    private fun client(server: TestSshServer, keys: FakeKeyStore = keyStore) =
        SshjClient(ConnectionConfig(user(), "127.0.0.1", server.port, connectTimeoutMs = 3_000), keys, hostKeys)

    private fun pin(server: TestSshServer) = runBlocking {
        hostKeys.pin("127.0.0.1", server.port, PinnedHostKey("ssh-ed25519", server.hostKeyFingerprint))
    }

    @Test
    fun `exec returns stdout, stderr and the exit code`() = runBlocking {
        TestSshServer().start().use { server ->
            pin(server)
            val r = client(server).exec(cmd("echo out; echo err >&2; exit 3"))
            assertEquals(SshResult(3, "out\n", "err\n"), r)
        }
    }

    @Test
    fun `an unpinned server key is reported before authentication`() = runBlocking {
        TestSshServer().start().use { server ->
            val e = assertFailsWith<SshException> { client(server).exec(cmd("echo ok")) }
            assertEquals(SshError.HostKeyUnknown("ssh-ed25519", server.hostKeyFingerprint), e.error)
            assertEquals(emptyList(), server.authAttempts.toList())
        }
    }

    @Test
    fun `a changed server key is reported before authentication`() = runBlocking {
        TestSshServer().start().use { server ->
            hostKeys.pin("127.0.0.1", server.port, PinnedHostKey("ssh-ed25519", "SHA256:someOtherKey"))
            val e = assertFailsWith<SshException> { client(server).exec(cmd("echo ok")) }
            assertEquals(SshError.HostKeyChanged("SHA256:someOtherKey", server.hostKeyFingerprint), e.error)
            assertEquals(emptyList(), server.authAttempts.toList())
        }
    }

    @Test
    fun `a rejected key is AuthRejected and no other auth method is tried`() = runBlocking {
        TestSshServer(acceptAnyPublicKey = false).start().use { server ->
            pin(server)
            val e = assertFailsWith<SshException> { client(server).exec(cmd("echo ok")) }
            assertEquals(SshError.AuthRejected, e.error)
            assertTrue(server.authAttempts.isNotEmpty() && server.authAttempts.all { it.startsWith("publickey:") }, "${server.authAttempts}")
        }
    }

    @Test
    fun `a successful login only ever uses publickey`() = runBlocking {
        TestSshServer().start().use { server ->
            pin(server)
            client(server).exec(cmd("echo ok"))
            assertTrue(server.authAttempts.all { it.startsWith("publickey:") }, "${server.authAttempts}")
        }
    }

    @Test
    fun `a closed port is HostUnreachable`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val e = assertFailsWith<SshException> {
            SshjClient(ConnectionConfig(user(), "127.0.0.1", port, connectTimeoutMs = 2_000), keyStore, hostKeys).exec(cmd("echo ok"))
        }
        assertEquals(SshError.HostUnreachable, e.error)
    }

    @Test
    fun `an unknown host name is HostUnreachable`() = runBlocking {
        val e = assertFailsWith<SshException> {
            SshjClient(ConnectionConfig(user(), "no-such-host.invalid", 22, connectTimeoutMs = 2_000), keyStore, hostKeys).exec(cmd("echo ok"))
        }
        assertEquals(SshError.HostUnreachable, e.error)
    }

    @Test
    fun `streams 20 MB into stdin and the remote sees EOF`() = runBlocking {
        val bytes = ByteArray(20 * 1024 * 1024).also { Random(1).nextBytes(it) }
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        TestSshServer().start().use { server ->
            pin(server)
            var last = 0L
            val r = client(server).exec(cmd("shasum -a 256 | cut -d' ' -f1"), ByteArraySource(bytes), { last = it }, timeoutMs = 60_000)
            assertEquals(expected, r.stdout.trim())
            assertEquals(bytes.size.toLong(), last)
        }
    }

    @Test
    fun `exceeding the deadline is a Timeout and returns promptly`() = runBlocking {
        TestSshServer().start().use { server ->
            pin(server)
            val started = System.nanoTime()
            val e = assertFailsWith<SshException> { client(server).exec(cmd("sleep 20"), timeoutMs = 700) }
            assertEquals(SshError.Timeout, e.error)
            val took = (System.nanoTime() - started) / 1_000_000
            assertTrue(took < 5_000, "took too long to give up: $took ms")
        }
    }

    @Test
    fun `cancelling the coroutine aborts a blocked exec promptly`() = runBlocking {
        TestSshServer().start().use { server ->
            pin(server)
            val job = launch(Dispatchers.Default, start = CoroutineStart.DEFAULT) { client(server).exec(cmd("sleep 20"), timeoutMs = 60_000) }
            Thread.sleep(800)
            val started = System.nanoTime()
            withTimeout(5_000) { job.cancel(); job.join() }
            assertTrue(job.isCancelled)
            assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000)
        }
    }

    @Test
    fun `cancelling during a large upload stops it`() = runBlocking {
        TestSshServer().start().use { server ->
            pin(server)
            val endless = object : ByteSource {
                override fun read(buffer: ByteArray): Int = buffer.size
                override fun close() {}
            }
            var sent = 0L
            val job = launch(Dispatchers.Default) { client(server).exec(cmd("sleep 20"), endless, { sent = it }, timeoutMs = 60_000) }
            Thread.sleep(800)
            withTimeout(5_000) { job.cancel(); job.join() }
            assertTrue(job.isCancelled)
            assertTrue(sent > 0)
        }
    }

    @Test
    fun `without a stored key nothing is attempted`() = runBlocking {
        TestSshServer().start().use { server ->
            val e = assertFailsWith<SshException> { client(server, FakeKeyStore()).exec(cmd("echo ok")) }
            assertIs<SshError.Protocol>(e.error)
            assertEquals(emptyList(), server.authAttempts.toList())
        }
    }

    // --- the whole stack: PoolServices -> LprCommandBuilder -> SshjClient -> server -> fake lpr ---

    private fun services(server: TestSshServer) = PoolServices(
        FakeSettings(user()), hostKeys,
        { config -> SshjClient(config.copy(connectTimeoutMs = 3_000), keyStore, hostKeys) },
        host = "127.0.0.1", port = server.port,
    )

    @Test
    fun `first connection then trust then test connection and print end to end`() = runBlocking {
        val lpr = FakeLpr()
        TestSshServer(pathPrefix = lpr.dir).start().use { server ->
            val services = services(server)
            val needsTrust = assertIs<TestConnectionResult.NeedsTrust>(services.testConnection())
            assertEquals(server.hostKeyFingerprint, needsTrust.key.fingerprint)

            services.trust(needsTrust.key)
            assertEquals(TestConnectionResult.Success, services.testConnection())

            val pdf = "%PDF-1.7 hello".encodeToByteArray() + ByteArray(5000) { it.toByte() }
            val job = PrintJob(PrintOptions(Printers.all[0], copies = 2), "ignored name; id.pdf")
            val result = services.print(job, ByteArraySource(pdf)) {}
            assertEquals(PrintResult.Sent("tfppr1", "tfppr1-7"), result)
            assertEquals(listOf("-P", "tfppr1", "-#", "2", "-o", "sides=one-sided", "-o", "media=A4"), lpr.args())
            assertTrue(pdf.contentEquals(lpr.input()))
        }
    }

    @Test
    fun `print failure from lpr maps stderr to a catalogue row`() = runBlocking {
        val lpr = FakeLpr().also { it.failWithUnknownPrinter() }
        TestSshServer(pathPrefix = lpr.dir).start().use { server ->
            pin(server)
            val failure = (services(server).print(PrintJob(PrintOptions(), "x.pdf"), ByteArraySource(ByteArray(10))) {} as PrintResult.Failed).failure
            assertEquals(FailureKind.UnknownPrinter, failure.kind)
            assertEquals(1, failure.exitCode)
        }
    }

    @Test
    fun `every option arrives as separate arguments through a real shell`() = runBlocking {
        val lpr = FakeLpr()
        TestSshServer(pathPrefix = lpr.dir).start().use { server ->
            pin(server)
            val options = PrintOptions(
                printer = Printers.all[3], copies = 3, pageRanges = Validators.pageRanges("7, 1-4, 3-5").valueOrNull(),
                duplex = Duplex.SHORT_EDGE, pagesPerSheet = PagesPerSheet.FOUR, orientation = Orientation.LANDSCAPE,
                fitToPage = true, paperSize = PaperSize.LETTER,
            )
            val result = services(server).print(PrintJob(options, "x; rm -rf ~.pdf"), ByteArraySource(ByteArray(10))) {}
            assertIs<PrintResult.Sent>(result)
            assertEquals(
                listOf(
                    "-P", "tfppr4", "-#", "3", "-o", "sides=two-sided-short-edge", "-o", "number-up=4", "-o", "page-ranges=1-5,7",
                    "-o", "landscape", "-o", "fit-to-page", "-o", "media=Letter",
                ),
                lpr.args(),
            )
        }
    }
}
