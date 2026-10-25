package com.yahyafati.poolprint.transport.testserver

import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.keyboard.InteractiveChallenge
import org.apache.sshd.server.auth.keyboard.KeyboardInteractiveAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.shell.ProcessShellCommandFactory

/**
 * Test-only in-process SSH server (Apache MINA SSHD). Runs exec requests through `sh -c` on the dev machine.
 * Records every authentication method a client attempts, so tests can prove password and
 * keyboard-interactive are never tried. Synthetic; never talks to anything but loopback.
 */
class TestSshServer(
    hostKeyFile: Path? = null,
    private val acceptAnyPublicKey: Boolean = true,
    listenPort: Int = 0,
    /** Prepended to PATH for exec commands, so tests can supply a fake `lpr`. */
    private val pathPrefix: Path? = null,
) : AutoCloseable {
    val authAttempts = CopyOnWriteArrayList<String>()

    private val sshd: SshServer = SshServer.setUpDefaultServer().apply {
        host = "127.0.0.1"
        port = listenPort
        keyPairProvider = SimpleGeneratorHostKeyProvider(hostKeyFile).apply { algorithm = "EdDSA"; keySize = 256 }
        publickeyAuthenticator = org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator { _, key, _ ->
            authAttempts += "publickey:" + KeyUtils.getFingerPrint(key)
            acceptAnyPublicKey
        }
        passwordAuthenticator = org.apache.sshd.server.auth.password.PasswordAuthenticator { _, _, _ ->
            authAttempts += "password"
            false
        }
        keyboardInteractiveAuthenticator = object : KeyboardInteractiveAuthenticator {
            override fun generateChallenge(
                session: ServerSession, username: String, lang: String, subMethods: String,
            ): InteractiveChallenge {
                authAttempts += "keyboard-interactive"
                return InteractiveChallenge().apply { interactionName = "x"; addPrompt("Password: ", false) }
            }
            override fun authenticate(session: ServerSession, username: String, responses: MutableList<String>): Boolean =
                false
        }
        commandFactory = org.apache.sshd.server.command.CommandFactory { channel, command ->
            val prefixed = if (pathPrefix == null) command else "PATH='$pathPrefix':\"\$PATH\"; $command"
            ProcessShellCommandFactory.INSTANCE.createCommand(channel, prefixed)
        }
    }

    val port: Int get() = sshd.port

    /** SHA-256 fingerprint of the server's Ed25519 host key, `SHA256:…`. */
    val hostKeyFingerprint: String
        get() = KeyUtils.getFingerPrint(sshd.keyPairProvider.loadKeys(null).first { it.public.algorithm.contains("Ed", true) }.public)

    fun start(): TestSshServer = apply { sshd.start() }
    override fun close() = sshd.stop(true)
}
