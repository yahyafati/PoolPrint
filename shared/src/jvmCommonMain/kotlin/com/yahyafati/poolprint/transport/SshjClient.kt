package com.yahyafati.poolprint.transport

import com.yahyafati.poolprint.core.command.AuthorizeKeyCommand
import com.yahyafati.poolprint.core.command.RemoteCommand
import com.yahyafati.poolprint.core.model.PublicKeyLine
import com.yahyafati.poolprint.hostkeys.HostKeyPolicy
import com.yahyafati.poolprint.hostkeys.HostKeyStore
import com.yahyafati.poolprint.hostkeys.HostKeyVerdict
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.keys.PrivateKeyStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.PublicKey
import java.security.Security
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.util.Base64
import java.security.MessageDigest

/**
 * The real [SshClient] (sshj, D-01). One connection per call. Security rules (docs/security.md):
 * [exec] uses publickey only (password and keyboard-interactive are never attempted); only the one-time
 * [installKeyWithPassword] uses the password, and it runs nothing but the fixed key-install command (D-20).
 * No forwarding, exec only, fixed host-key algorithm preference, host key verified (TOFU) **before** authentication.
 */
class SshjClient(
    private val config: ConnectionConfig,
    private val privateKeys: PrivateKeyStore,
    private val hostKeys: HostKeyStore,
) : SshClient {

    override suspend fun exec(
        command: RemoteCommand,
        stdin: ByteSource?,
        onUploaded: (Long) -> Unit,
        timeoutMs: Long,
    ): SshResult = withContext(Dispatchers.IO) {
        val pem = privateKeys.load() ?: throw SshException(SshError.Protocol("No SSH key is set up."))
        try {
            session(
                timeoutMs,
                authenticate = { ssh ->
                    val keyProvider = try {
                        ssh.loadKeys(String(pem, Charsets.UTF_8), null, null)
                    } finally {
                        pem.fill(0)
                    }
                    // authPublickey offers only the publickey method.
                    ssh.authPublickey(config.username.value, keyProvider)
                },
            ) { ssh -> runExec(ssh, command, stdin, onUploaded) }
        } finally {
            pem.fill(0)
        }
    }

    override suspend fun installKeyWithPassword(password: CharArray, key: PublicKeyLine): SshResult =
        withContext(Dispatchers.IO) {
            val handedOut = mutableListOf<CharArray>()
            val finder = object : PasswordFinder {
                override fun reqPassword(resource: Resource<*>?): CharArray = password.copyOf().also { handedOut += it }
                override fun shouldRetry(resource: Resource<*>?) = false
            }
            try {
                session(
                    SshClient.DEFAULT_TIMEOUT_MS,
                    authenticate = { ssh -> authWithPassword(ssh, finder) },
                ) { ssh -> runExec(ssh, AuthorizeKeyCommand.build(), ByteArraySource(AuthorizeKeyCommand.stdin(key)), {}) }
            } finally {
                password.fill('\u0000')
                handedOut.forEach { it.fill('\u0000') }
            }
        }

    /**
     * `password` first, then `keyboard-interactive` (answering only a hidden "Password:" prompt). If the server
     * checked the password with the first method, a rejection is final: it is never sent a second time.
     */
    private fun authWithPassword(ssh: SSHClient, finder: PasswordFinder) {
        val user = config.username.value
        try {
            ssh.auth(user, AuthPassword(finder))
        } catch (e: UserAuthException) {
            if ("password" in ssh.userAuth.allowedMethods) throw e
            ssh.auth(user, AuthKeyboardInteractive(PasswordResponseProvider(finder)))
        }
    }

    /** Connects, verifies the host key before anything else, runs [authenticate], then [block]. Must run on IO. */
    private suspend fun session(
        timeoutMs: Long,
        authenticate: (SSHClient) -> Unit,
        block: suspend (SSHClient) -> SshResult,
    ): SshResult {
        val pinned = hostKeys.get(config.host, config.port)
        installBouncyCastle()

        val verdict = AtomicReference<HostKeyVerdict?>()
        val ssh = SSHClient(DefaultConfig().also { applyHostKeyPreference(it, pinned?.type) })
        val timedOut = AtomicBoolean(false)
        var connected = false
        try {
            return coroutineScope {
                // Blocking socket I/O ignores coroutine cancellation. The watcher closes the connection when the
                // deadline passes or the scope is cancelled (user Cancel), which unblocks every pending read or write.
                val watcher = launch(Dispatchers.Default) {
                    try {
                        delay(timeoutMs)
                        timedOut.set(true)
                    } finally {
                        // Socket first: a regular disconnect needs the transport's write lock, which a writer
                        // blocked on a full TCP buffer is holding.
                        runCatching { ssh.socket?.close() }
                        runCatching { ssh.close() }
                    }
                }
                try {
                    ssh.connectTimeout = config.connectTimeoutMs
                    ssh.timeout = 0 // the overall deadline is withTimeout, not a per-read socket timeout
                    ssh.addHostKeyVerifier(object : HostKeyVerifier {
                        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                            val offered = PinnedHostKey(KeyType.fromKey(key).toString(), fingerprintOf(key))
                            val result = HostKeyPolicy.evaluate(pinned, offered)
                            verdict.set(result)
                            return result is HostKeyVerdict.Trusted
                        }

                        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
                    })
                    ssh.connect(config.host, config.port)
                    connected = true
                    authenticate(ssh)
                    block(ssh)
                } finally {
                    watcher.cancel()
                }
            }
        } catch (e: Throwable) {
            if (timedOut.get()) throw SshException(SshError.Timeout, e)
            currentCoroutineContext().ensureActive()
            if (e is CancellationException) throw e
            throw mapFailure(e, verdict.get(), connected)
        } finally {
            runCatching { ssh.close() }
        }
    }

    private suspend fun runExec(ssh: SSHClient, command: RemoteCommand, stdin: ByteSource?, onUploaded: (Long) -> Unit): SshResult =
        ssh.startSession().use { session ->
            val cmd = session.exec(command.value)
            coroutineScope {
                val stdout = async(Dispatchers.IO) { cmd.inputStream.readCapped() }
                val stderr = async(Dispatchers.IO) { cmd.errorStream.readCapped() }
                if (stdin != null) {
                    // sshj's blocking write can wait on a window the server never opens, and ignores coroutine
                    // cancellation. A dedicated thread can be interrupted, a pooled one can't be (safely).
                    inDedicatedThread {
                        val buffer = ByteArray(32 * 1024)
                        var sent = 0L
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            val n = stdin.read(buffer)
                            if (n < 0) break
                            cmd.outputStream.write(buffer, 0, n)
                            sent += n
                            onUploaded(sent)
                        }
                    }
                }
                cmd.outputStream.close() // EOF on the remote stdin
                val out = stdout.await()
                val err = stderr.await()
                cmd.join(EXIT_STATUS_WAIT_SECONDS, TimeUnit.SECONDS)
                SshResult(cmd.exitStatus ?: -1, out, err)
            }
        }

    private suspend fun <T> inDedicatedThread(block: () -> T): T {
        val result = CompletableDeferred<T>()
        val thread = thread(name = "poolprint-ssh-upload", isDaemon = true) {
            try {
                result.complete(block())
            } catch (e: Throwable) {
                result.completeExceptionally(e)
            }
        }
        try {
            return result.await()
        } catch (e: CancellationException) {
            thread.interrupt()
            throw e
        }
    }

    private fun InputStream.readCapped(): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            if (out.size() < MAX_OUTPUT_BYTES) out.write(buffer, 0, minOf(n, MAX_OUTPUT_BYTES - out.size()))
        }
        return out.toString(Charsets.UTF_8)
    }

    internal companion object {
        private const val MAX_OUTPUT_BYTES = 256 * 1024
        private const val EXIT_STATUS_WAIT_SECONDS = 5L

        /** Preferred order. A pinned key type restricts the offer, so the key type can't flap between connections. */
        private val ALGORITHM_ORDER = listOf("ssh-ed25519", "ecdsa-sha2-nistp256", "rsa-sha2-512", "rsa-sha2-256", "ssh-rsa")

        private fun algorithmsFor(pinnedType: String?): List<String> = when (pinnedType) {
            null -> ALGORITHM_ORDER
            "ssh-ed25519" -> listOf("ssh-ed25519")
            "ecdsa-sha2-nistp256" -> listOf("ecdsa-sha2-nistp256")
            "ssh-rsa" -> listOf("rsa-sha2-512", "rsa-sha2-256", "ssh-rsa")
            else -> ALGORITHM_ORDER
        }

        internal fun applyHostKeyPreference(config: DefaultConfig, pinnedType: String?) {
            val allowed = algorithmsFor(pinnedType)
            config.keyAlgorithms = config.keyAlgorithms
                .filter { it.name in allowed }
                .sortedBy { allowed.indexOf(it.name) }
        }

        internal fun fingerprintOf(key: PublicKey): String {
            val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))
        }

        /**
         * Maps a failure to an [SshException]. A recorded host-key verdict wins over whatever sshj threw when
         * the verifier said no. [connected] separates "can't reach the server" from later protocol problems.
         */
        internal fun mapFailure(e: Throwable, verdict: HostKeyVerdict?, connected: Boolean): SshException {
            when (verdict) {
                is HostKeyVerdict.Unknown -> return SshException(SshError.HostKeyUnknown(verdict.offered.type, verdict.offered.fingerprint), e)
                is HostKeyVerdict.Changed -> return SshException(SshError.HostKeyChanged(verdict.expected.fingerprint, verdict.actual.fingerprint), e)
                else -> Unit
            }
            if (e is SshException) return e
            if (e is UserAuthException) return SshException(SshError.AuthRejected, e)
            if (!connected && (e is ConnectException || e is UnknownHostException || e is NoRouteToHostException ||
                    e is SocketTimeoutException || e is java.net.SocketException)
            ) {
                return SshException(SshError.HostUnreachable, e)
            }
            if (e is SocketTimeoutException) return SshException(SshError.Timeout, e)
            val message = if (e is IOException || e is net.schmizz.sshj.common.SSHException) e.message else null
            return SshException(SshError.Protocol(message ?: e.javaClass.simpleName), e)
        }

        private var installed = false

        /**
         * sshj looks Ed25519 up through the JCA provider named "BC". Android ships its own, older, stripped
         * "BC" without Ed25519, so replace it with the bundled one (process-wide, once). See D-01.
         */
        @Synchronized
        fun installBouncyCastle() {
            if (installed) return
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
            installed = true
        }
    }
}

class SshjClientFactory(
    private val privateKeys: PrivateKeyStore,
    private val hostKeys: HostKeyStore,
) : SshClientFactory {
    override fun create(config: ConnectionConfig): SshClient = SshjClient(config, privateKeys, hostKeys)
}
