package com.yahyafati.poolprint.desktop

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

/*
 * Windows single instance and file hand-over (D-28, docs/security.md "Single instance and file hand-over").
 *
 * Nothing in this file logs: the token must never reach a log, and a dropped message needs no trace.
 */

private const val LOCK_FILE_NAME = "instance.lock"
private const val HAND_OVER_FILE_NAME = "instance.handover"
private val LOOPBACK: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

/** The wire format of one hand-over message: `<token> <absolute path>\n`, UTF-8, one line, at most [MAX_MESSAGE_BYTES]. */
internal object HandOverProtocol {
    const val MAX_MESSAGE_BYTES = 8 * 1024
    private const val TOKEN_BYTES = 32

    /** 256 bits from [SecureRandom], hex encoded (64 characters). */
    fun newToken(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun encode(token: String, file: File): ByteArray = "$token ${file.path}\n".toByteArray(StandardCharsets.UTF_8)

    /**
     * The PDF named by [raw] if (and only if) it is a well-formed message with the right [token] whose path is an
     * absolute, readable `*.pdf` file; null for everything else. The token is checked before the path is looked at.
     */
    fun parse(raw: ByteArray, token: String): File? {
        if (raw.isEmpty() || raw.size > MAX_MESSAGE_BYTES) return null
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(raw)).toString()
        } catch (_: CharacterCodingException) {
            return null
        }
        // Exactly one line, terminated by one '\n', and no control characters anywhere in it.
        if (!text.endsWith("\n")) return null
        val line = text.dropLast(1)
        if (line.any { it.isISOControl() }) return null
        val space = line.indexOf(' ')
        if (space <= 0) return null
        if (!constantTimeEquals(line.substring(0, space), token)) return null
        val path = line.substring(space + 1)
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.isAbsolute || !isPdfFile(file)) return null
        return file
    }

    /** Compares without leaking, through timing, how much of [given] matched. */
    fun constantTimeEquals(given: String, expected: String): Boolean =
        MessageDigest.isEqual(given.toByteArray(StandardCharsets.UTF_8), expected.toByteArray(StandardCharsets.UTF_8))
}

/**
 * The running instance's loopback listener. One connection at a time, each bounded in size and in time; whatever
 * is wrong with a message (token, shape, size, path) it is dropped without a reply and the listener carries on.
 */
internal class HandOverListener private constructor(
    private val server: ServerSocket,
    private val token: String,
    private val readTimeoutMs: Int,
    private val onPdf: (File) -> Unit,
) : AutoCloseable {
    val port: Int get() = server.localPort

    private val worker = thread(isDaemon = true, name = "pool-print-handover") {
        while (!server.isClosed) {
            try {
                server.accept().use(::handle)
            } catch (_: IOException) {
                // Closed, or a connection that broke: nothing to do either way.
            } catch (_: RuntimeException) {
                // A callback or a parser bug must not end the listener.
            }
        }
    }

    private fun handle(socket: Socket) {
        val deadline = System.nanoTime() + readTimeoutMs * 1_000_000L
        val buffer = ByteArray(HandOverProtocol.MAX_MESSAGE_BYTES + 1)
        var length = 0
        while (length < buffer.size) {
            val remainingMs = (deadline - System.nanoTime()) / 1_000_000L
            if (remainingMs <= 0) return
            socket.soTimeout = remainingMs.toInt()
            val read = try {
                socket.getInputStream().read(buffer, length, buffer.size - length)
            } catch (_: java.net.SocketTimeoutException) {
                return
            }
            if (read < 0) break
            length += read
        }
        if (length > HandOverProtocol.MAX_MESSAGE_BYTES) return // oversized: never even parsed
        HandOverProtocol.parse(buffer.copyOf(length), token)?.let(onPdf)
    }

    override fun close() {
        try {
            server.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        /** Listens on 127.0.0.1 only, on an ephemeral port. */
        fun start(token: String, readTimeoutMs: Int = 2_000, onPdf: (File) -> Unit): HandOverListener =
            HandOverListener(ServerSocket(0, 8, LOOPBACK), token, readTimeoutMs, onPdf)
    }
}

/** What the running instance publishes for a second one: the port and the token, in a file only the user can read. */
internal class HandOverEndpoint(val port: Int, val token: String) {
    companion object {
        fun file(dir: File) = File(dir, HAND_OVER_FILE_NAME)

        /**
         * Replaces the endpoint file. It is created fresh (owner-only on POSIX; on Windows it inherits the user-only
         * ACL of `%LOCALAPPDATA%`) and moved into place, so a reader never sees a half-written file.
         */
        fun write(dir: File, port: Int, token: String) {
            val target = file(dir).toPath()
            val temp = File(dir, "$HAND_OVER_FILE_NAME.tmp").toPath()
            Files.deleteIfExists(temp)
            val posix = "posix" in FileSystems.getDefault().supportedFileAttributeViews()
            if (posix) Files.createFile(temp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            else Files.createFile(temp)
            Files.write(temp, "$port\n$token\n".toByteArray(StandardCharsets.UTF_8))
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        fun delete(dir: File) {
            runCatching { Files.deleteIfExists(file(dir).toPath()) }
        }

        /** The published endpoint, or null if there is none or it is malformed. */
        fun read(dir: File): HandOverEndpoint? {
            val file = file(dir)
            if (!file.isFile || file.length() > 256) return null
            val lines = runCatching { file.readText(StandardCharsets.UTF_8).lines() }.getOrNull() ?: return null
            val port = lines.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val token = lines.getOrNull(1)?.trim()?.takeIf { t -> t.length in 16..128 && t.all { it in '0'..'9' || it in 'a'..'f' } } ?: return null
            return HandOverEndpoint(port, token)
        }
    }
}

/** The second instance's side: send one path to the running instance. Never touches the DataStore, keys or network. */
internal object HandOverClient {
    /**
     * Sends [file] (as an absolute path) to the running instance, retrying for up to [timeoutMs] because the first
     * instance may still be starting its listener. True once the message has been written and the connection closed
     * cleanly; the receiver decides silently whether it acts on it.
     */
    fun send(dir: File, file: File, timeoutMs: Long = 5_000): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        val absolute = file.absoluteFile
        while (true) {
            val endpoint = HandOverEndpoint.read(dir)
            if (endpoint != null && trySend(endpoint, absolute)) return true
            if (System.nanoTime() >= deadline) return false
            Thread.sleep(100)
        }
    }

    private fun trySend(endpoint: HandOverEndpoint, file: File): Boolean =
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(LOOPBACK, endpoint.port), 500)
                socket.soTimeout = 2_000
                socket.getOutputStream().apply {
                    write(HandOverProtocol.encode(endpoint.token, file))
                    flush()
                }
                socket.shutdownOutput()
                // Wait for the receiver to close, so the process doesn't exit with the message unread.
                while (socket.getInputStream().read() >= 0) { /* drain */ }
            }
            true
        } catch (_: IOException) {
            false
        }
}
