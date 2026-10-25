package com.yahyafati.poolprint.transport

import com.yahyafati.poolprint.core.command.RemoteCommand
import com.yahyafati.poolprint.core.model.PublicKeyLine
import com.yahyafati.poolprint.core.validation.valueOrNull
import com.yahyafati.poolprint.fakes.FakeHostKeyStore
import com.yahyafati.poolprint.fakes.FakeKeyStore
import com.yahyafati.poolprint.fakes.FakeSettings
import com.yahyafati.poolprint.fakes.user
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.keys.BouncyCastleKeyGenerator
import com.yahyafati.poolprint.service.FailureKind
import com.yahyafati.poolprint.service.InstallKeyResult
import com.yahyafati.poolprint.service.PoolServices
import com.yahyafati.poolprint.service.TestConnectionResult
import com.yahyafati.poolprint.transport.testserver.TestSshServer
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The one-time password key install (D-20) with the real [SshjClient] against an in-process server on loopback.
 * Every server gets a temp HOME, so `~/.ssh` is never the developer's own.
 */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class SshjPasswordInstallTest {
    private val generated = BouncyCastleKeyGenerator().generate("pool-print@test")
    private val publicKey = PublicKeyLine.parse(generated.publicKeyLine).valueOrNull()!!
    private val keyStore = FakeKeyStore().also { runBlocking { it.save(generated.privateKeyPem) } }
    private val hostKeys = FakeHostKeyStore()
    private val home: Path = createTempDirectory("poolprint-home")
    private val authorizedKeys = home.resolve(".ssh/authorized_keys")

    @AfterTest
    fun cleanUp() = home.deleteRecursively()

    private fun server(
        password: String? = "correct horse",
        passwordMethod: Boolean = true,
        keyboardInteractivePassword: String? = null,
    ) = TestSshServer(
        acceptAnyPublicKey = false, password = password, passwordMethod = passwordMethod,
        keyboardInteractivePassword = keyboardInteractivePassword, homeDir = home,
    ).start()

    private fun client(server: TestSshServer) =
        SshjClient(ConnectionConfig(user(), "127.0.0.1", server.port, connectTimeoutMs = 3_000), keyStore, hostKeys)

    private fun pin(server: TestSshServer) = runBlocking {
        hostKeys.pin("127.0.0.1", server.port, PinnedHostKey("ssh-ed25519", server.hostKeyFingerprint))
    }

    @Test
    fun `installs the key with the password, after which the key logs in`() = runBlocking {
        server().use { server ->
            pin(server)
            val result = client(server).installKeyWithPassword("correct horse".toCharArray(), publicKey)
            assertEquals(0, result.exitCode, result.stderr)
            assertEquals("\n${publicKey.line}\n", authorizedKeys.readText())
            assertEquals(listOf("password"), server.authAttempts.toList())

            server.authAttempts.clear()
            assertEquals("ok\n", client(server).exec(RemoteCommand("echo ok")).stdout)
            assertTrue(server.authAttempts.all { it.startsWith("publickey:") }, "${server.authAttempts}")
        }
    }

    @Test
    fun `the password array is zeroed afterwards, on success and on failure`() = runBlocking {
        server().use { server ->
            pin(server)
            val good = "correct horse".toCharArray()
            client(server).installKeyWithPassword(good, publicKey)
            assertTrue(good.all { it == '\u0000' })

            val bad = "wrong".toCharArray()
            assertFailsWith<SshException> { client(server).installKeyWithPassword(bad, publicKey) }
            assertTrue(bad.all { it == '\u0000' })
        }
    }

    @Test
    fun `a wrong password is AuthRejected and is sent only once`() = runBlocking {
        server().use { server ->
            pin(server)
            val e = assertFailsWith<SshException> { client(server).installKeyWithPassword("wrong".toCharArray(), publicKey) }
            assertEquals(SshError.AuthRejected, e.error)
            assertEquals(listOf("password"), server.authAttempts.toList())
            assertFalse(authorizedKeys.exists())
        }
    }

    @Test
    fun `falls back to keyboard-interactive when the server has no password method`() = runBlocking {
        server(password = null, passwordMethod = false, keyboardInteractivePassword = "correct horse").use { server ->
            pin(server)
            val result = client(server).installKeyWithPassword("correct horse".toCharArray(), publicKey)
            assertEquals(0, result.exitCode, result.stderr)
            assertEquals(listOf("keyboard-interactive"), server.authAttempts.toList())
            assertTrue(authorizedKeys.readText().contains(publicKey.line))
        }
    }

    @Test
    fun `the password is never sent to a server that is not pinned yet`() = runBlocking {
        server().use { server ->
            val e = assertFailsWith<SshException> { client(server).installKeyWithPassword("correct horse".toCharArray(), publicKey) }
            assertEquals(SshError.HostKeyUnknown("ssh-ed25519", server.hostKeyFingerprint), e.error)
            assertEquals(emptyList(), server.authAttempts.toList())
            assertFalse(authorizedKeys.exists())
        }
    }

    @Test
    fun `the password is never sent to a server whose key changed`() = runBlocking {
        server().use { server ->
            hostKeys.pin("127.0.0.1", server.port, PinnedHostKey("ssh-ed25519", "SHA256:someOtherKey"))
            val e = assertFailsWith<SshException> { client(server).installKeyWithPassword("correct horse".toCharArray(), publicKey) }
            assertIs<SshError.HostKeyChanged>(e.error)
            assertEquals(emptyList(), server.authAttempts.toList())
        }
    }

    @Test
    fun `regular commands still never try the password, even on a server that would accept it`() = runBlocking {
        server().use { server ->
            pin(server)
            val e = assertFailsWith<SshException> { client(server).exec(RemoteCommand("echo ok")) }
            assertEquals(SshError.AuthRejected, e.error)
            assertTrue(server.authAttempts.all { it.startsWith("publickey:") }, "${server.authAttempts}")
        }
    }

    @Test
    fun `whole stack - trust, install with password, then the key test connection succeeds`() = runBlocking {
        server().use { server ->
            val settings = FakeSettings(user()).also { it.setPublicKey(generated.publicKeyLine) }
            val services = PoolServices(
                settings, hostKeys,
                { config -> SshjClient(config.copy(connectTimeoutMs = 3_000), keyStore, hostKeys) },
                host = "127.0.0.1", port = server.port,
            )
            val needsTrust = assertIs<InstallKeyResult.NeedsTrust>(services.installKeyWithPassword("correct horse".toCharArray()))
            services.trust(needsTrust.key)

            val wrong = assertIs<InstallKeyResult.Failed>(services.installKeyWithPassword("nope".toCharArray()))
            assertEquals(FailureKind.PasswordRejected, wrong.failure.kind)

            assertEquals(InstallKeyResult.Installed, services.installKeyWithPassword("correct horse".toCharArray()))
            assertEquals(TestConnectionResult.Success, services.testConnection())
        }
    }
}
