package com.yahyafati.poolprint.transport

import com.yahyafati.poolprint.core.command.RemoteCommand
import com.yahyafati.poolprint.core.model.PoolUsername

/** Streaming without `java.io`. Platform code wraps a file or a content URI. */
interface ByteSource : AutoCloseable {
    /** Reads up to buffer.size bytes; returns -1 at the end. */
    fun read(buffer: ByteArray): Int
}

class ByteArraySource(private val bytes: ByteArray) : ByteSource {
    private var position = 0

    override fun read(buffer: ByteArray): Int {
        if (position >= bytes.size) return -1
        val count = minOf(buffer.size, bytes.size - position)
        bytes.copyInto(buffer, 0, position, position + count)
        position += count
        return count
    }

    override fun close() {}
}

/** A finished command. A non-zero exit code is a remote failure, not a transport error. */
data class SshResult(val exitCode: Int, val stdout: String, val stderr: String)

/** Transport-level failures. Every one of them is raised as an [SshException]. */
sealed interface SshError {
    /** DNS failure, no route, refused, or a timeout while connecting. */
    data object HostUnreachable : SshError

    /** The key is not in `authorized_keys` (or was refused). */
    data object AuthRejected : SshError

    /** First connection: nothing is pinned yet. Raised before authentication. */
    data class HostKeyUnknown(val keyType: String, val fingerprint: String) : SshError

    /** The server's key differs from the pinned one. Raised before authentication. */
    data class HostKeyChanged(val expected: String, val actual: String) : SshError

    data object Timeout : SshError

    data class Protocol(val message: String) : SshError
}

class SshException(val error: SshError, cause: Throwable? = null) : Exception(error.toString(), cause)

object PoolServer {
    const val HOST = "login.informatik.uni-freiburg.de"
    const val PORT = 22
}

data class ConnectionConfig(
    val username: PoolUsername,
    val host: String = PoolServer.HOST,
    val port: Int = PoolServer.PORT,
    val connectTimeoutMs: Int = 10_000,
)

interface SshClient {
    /**
     * Runs [command] over an exec channel, streaming [stdin] (if any) and closing it at the end.
     * [onUploaded] receives the number of bytes sent so far. Cancelling the coroutine aborts the connection.
     * @throws SshException for transport failures.
     */
    suspend fun exec(
        command: RemoteCommand,
        stdin: ByteSource? = null,
        onUploaded: (Long) -> Unit = {},
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): SshResult

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}

/** Creates a client for one operation (one SSH connection per operation, D-09). */
fun interface SshClientFactory {
    fun create(config: ConnectionConfig): SshClient
}
