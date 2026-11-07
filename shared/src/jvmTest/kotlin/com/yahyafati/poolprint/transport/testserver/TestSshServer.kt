package com.yahyafati.poolprint.transport.testserver

import java.nio.file.Files
import java.nio.file.Path
import java.security.PublicKey
import java.util.concurrent.CopyOnWriteArrayList
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.keyboard.InteractiveChallenge
import org.apache.sshd.server.auth.keyboard.KeyboardInteractiveAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.junit.Assume

/**
 * Test-only in-process SSH server (Apache MINA SSHD). Runs exec requests through a POSIX `sh -c` ([PosixShell]; on Windows
 * Git for Windows' `sh.exe`), like the real Linux server.
 * Records every authentication method a client attempts, so tests can prove password and
 * keyboard-interactive are never tried by regular commands, and are tried exactly once by the one-time
 * password key install. Synthetic; never talks to anything but loopback.
 */
class TestSshServer(
    hostKeyFile: Path? = null,
    private val acceptAnyPublicKey: Boolean = true,
    listenPort: Int = 0,
    /** Prepended to PATH for exec commands, so tests can supply a fake `lpr`. */
    private val pathPrefix: Path? = null,
    /** The only password the `password` method accepts; null rejects every password. */
    private val password: String? = null,
    /** False leaves the `password` method out entirely (a keyboard-interactive-only server). */
    passwordMethod: Boolean = true,
    /** The only answer the keyboard-interactive "Password:" prompt accepts; null rejects every answer. */
    private val keyboardInteractivePassword: String? = null,
    /**
     * HOME for exec commands, so `~/.ssh` is a temp dir and never the developer's own. When set and
     * [acceptAnyPublicKey] is false, a public key is accepted only if it is in `<homeDir>/.ssh/authorized_keys`.
     */
    private val homeDir: Path? = null,
) : AutoCloseable {
    val authAttempts = CopyOnWriteArrayList<String>()

    private val sshd: SshServer = SshServer.setUpDefaultServer().apply {
        host = "127.0.0.1"
        port = listenPort
        keyPairProvider = SimpleGeneratorHostKeyProvider(hostKeyFile).apply { algorithm = "EdDSA"; keySize = 256 }
        publickeyAuthenticator = org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator { _, key, _ ->
            authAttempts += "publickey:" + KeyUtils.getFingerPrint(key)
            acceptAnyPublicKey || isAuthorized(key)
        }
        passwordAuthenticator = if (!passwordMethod) null else org.apache.sshd.server.auth.password.PasswordAuthenticator { _, given, _ ->
            authAttempts += "password"
            password != null && given == password
        }
        keyboardInteractiveAuthenticator = object : KeyboardInteractiveAuthenticator {
            override fun generateChallenge(
                session: ServerSession, username: String, lang: String, subMethods: String,
            ): InteractiveChallenge {
                authAttempts += "keyboard-interactive"
                return InteractiveChallenge().apply { interactionName = "x"; addPrompt("Password: ", false) }
            }
            override fun authenticate(session: ServerSession, username: String, responses: MutableList<String>): Boolean =
                keyboardInteractivePassword != null && responses == listOf(keyboardInteractivePassword)
        }
        commandFactory = org.apache.sshd.server.command.CommandFactory { channel, command ->
            var prefixed = if (pathPrefix == null) command else "PATH='${PosixShell.path(pathPrefix)}':\"\$PATH\"; $command"
            if (homeDir != null) prefixed = "HOME='${PosixShell.path(homeDir)}'; export HOME; $prefixed"
            PosixShell.Factory(prefixed).createShell(channel)
        }
    }

    val port: Int get() = sshd.port

    private fun isAuthorized(key: PublicKey): Boolean {
        val file = homeDir?.resolve(".ssh/authorized_keys") ?: return false
        if (!Files.exists(file)) return false
        return AuthorizedKeyEntry.readAuthorizedKeys(file).any {
            KeyUtils.compareKeys(it.resolvePublicKey(null, PublicKeyEntryResolver.IGNORING), key)
        }
    }

    /** SHA-256 fingerprint of the server's Ed25519 host key, `SHA256:…`. */
    val hostKeyFingerprint: String
        get() = KeyUtils.getFingerPrint(sshd.keyPairProvider.loadKeys(null).first { it.public.algorithm.contains("Ed", true) }.public)

    /** Starts the server; skips the calling test (JUnit assumption) when there is no POSIX `sh` (Windows without Git). */
    fun start(): TestSshServer = apply {
        Assume.assumeTrue("needs a POSIX sh (on Windows: Git for Windows)", PosixShell.executable != null)
        sshd.start()
    }
    override fun close() = sshd.stop(true)
}
