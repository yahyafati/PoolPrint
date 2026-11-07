package com.yahyafati.poolprint.desktop

import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandOverTest {
    private val dir: File = Files.createTempDirectory("handover").toFile().also { it.deleteOnExit() }
    private val closeables = mutableListOf<AutoCloseable>()
    private val token = HandOverProtocol.newToken()

    @AfterTest
    fun tearDown() {
        closeables.asReversed().forEach { runCatching { it.close() } }
        dir.deleteRecursively()
    }

    private fun pdf(name: String = "doc.pdf") = File(dir, name).also { it.writeBytes("%PDF-1.4".toByteArray()) }

    private fun listener(readTimeoutMs: Int = 2_000, received: MutableList<File>): HandOverListener =
        HandOverListener.start(token, readTimeoutMs) { received += it }.also { closeables += it }

    /** Sends [bytes] raw to the listener and closes our side; returns once the listener has closed the connection. */
    private fun sendRaw(port: Int, bytes: ByteArray) {
        Socket(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), port).use { socket ->
            socket.soTimeout = 5_000
            runCatching {
                socket.getOutputStream().write(bytes)
                socket.getOutputStream().flush()
                socket.shutdownOutput()
                while (socket.getInputStream().read() >= 0) { /* wait for the listener to finish */ }
            }
        }
    }

    private fun line(token: String, path: String) = "$token $path\n".toByteArray()

    /** Sends a good message last: because the listener works one connection at a time, everything before it has been decided. */
    private fun assertOnlySentinelArrives(port: Int, received: List<File>, vararg bad: ByteArray) {
        bad.forEach { sendRaw(port, it) }
        val sentinel = pdf("sentinel.pdf")
        sendRaw(port, line(token, sentinel.path))
        waitFor { received.isNotEmpty() }
        assertEquals(listOf(sentinel), received.toList())
    }

    private fun waitFor(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val end = System.nanoTime() + timeoutMs * 1_000_000
        while (!condition()) {
            check(System.nanoTime() < end) { "condition not met in time" }
            Thread.sleep(20)
        }
    }

    // --- protocol -------------------------------------------------------------------------------------------

    @Test
    fun `tokens are long, hex and different every time`() {
        val a = HandOverProtocol.newToken()
        val b = HandOverProtocol.newToken()
        assertEquals(64, a.length)
        assertTrue(a.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(a, b)
    }

    @Test
    fun `parse accepts a well formed message and returns the file`() {
        val file = pdf("my report.pdf")
        assertEquals(file, HandOverProtocol.parse(HandOverProtocol.encode(token, file), token))
    }

    @Test
    fun `parse keeps spaces and unicode in the path`() {
        val file = pdf("Übung 3 – Blatt.pdf")
        assertEquals(file, HandOverProtocol.parse(HandOverProtocol.encode(token, file), token))
    }

    @Test
    fun `parse rejects a wrong or missing token`() {
        val file = pdf()
        assertNull(HandOverProtocol.parse(HandOverProtocol.encode("0".repeat(64), file), token))
        assertNull(HandOverProtocol.parse(HandOverProtocol.encode(token.dropLast(1), file), token))
        assertNull(HandOverProtocol.parse("${file.path}\n".toByteArray(), token))
        assertNull(HandOverProtocol.parse(" ${file.path}\n".toByteArray(), token))
    }

    @Test
    fun `parse rejects garbage`() {
        val garbage = listOf(
            byteArrayOf(),
            byteArrayOf(0, -1, -2, 0x7f, 0),
            "\n".toByteArray(),
            "hello\n".toByteArray(),
            "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray(),
            token.toByteArray(), // no path, no newline
            "$token \n".toByteArray(), // blank path
            byteArrayOf(0xC3.toByte(), 0x28, 0x0A), // malformed UTF-8
        )
        garbage.forEach { assertNull(HandOverProtocol.parse(it, token), it.decodeToString()) }
    }

    @Test
    fun `parse rejects more than one line, a missing newline and control characters`() {
        val file = pdf()
        assertNull(HandOverProtocol.parse("$token ${file.path}".toByteArray(), token))
        assertNull(HandOverProtocol.parse("$token ${file.path}\n\n".toByteArray(), token))
        assertNull(HandOverProtocol.parse("$token ${file.path}\n$token ${file.path}\n".toByteArray(), token))
        assertNull(HandOverProtocol.parse("$token ${file.path}\r\n".toByteArray(), token))
        assertNull(HandOverProtocol.parse("$token ${file.path}\u0000\n".toByteArray(), token))
    }

    @Test
    fun `parse rejects an oversized message even with the right token and a real file`() {
        val file = pdf()
        val padded = "$token ${file.path}${" ".repeat(HandOverProtocol.MAX_MESSAGE_BYTES)}\n".toByteArray()
        assertTrue(padded.size > HandOverProtocol.MAX_MESSAGE_BYTES)
        assertNull(HandOverProtocol.parse(padded, token))
    }

    @Test
    fun `parse rejects non pdf, missing, relative and directory paths`() {
        val text = File(dir, "notes.txt").also { it.writeText("hi") }
        val folder = File(dir, "looks-like.pdf").also { it.mkdir() }
        assertNull(HandOverProtocol.parse(line(token, text.path), token))
        assertNull(HandOverProtocol.parse(line(token, File(dir, "missing.pdf").path), token))
        assertNull(HandOverProtocol.parse(line(token, folder.path), token))
        assertNull(HandOverProtocol.parse(line(token, "doc.pdf"), token))
        assertNull(HandOverProtocol.parse(line(token, "../doc.pdf"), token))
    }

    // --- listener -------------------------------------------------------------------------------------------

    @Test
    fun `the listener accepts only loopback connections on an ephemeral port`() {
        val listener = listener(received = CopyOnWriteArrayList())
        assertTrue(listener.port in 1024..65535)
        // Bound to 127.0.0.1 only: connecting works there ...
        Socket(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), listener.port).close()
    }

    @Test
    fun `the listener delivers a valid message`() {
        val received = CopyOnWriteArrayList<File>()
        val listener = listener(received = received)
        val file = pdf()
        sendRaw(listener.port, line(token, file.path))
        waitFor { received.isNotEmpty() }
        assertEquals(listOf(file), received.toList())
    }

    @Test
    fun `the listener drops a wrong token and carries on`() {
        val received = CopyOnWriteArrayList<File>()
        val listener = listener(received = received)
        assertOnlySentinelArrives(listener.port, received, line("f".repeat(64), pdf("secret.pdf").path))
    }

    @Test
    fun `the listener drops garbage and carries on`() {
        val received = CopyOnWriteArrayList<File>()
        val listener = listener(received = received)
        assertOnlySentinelArrives(
            listener.port, received,
            "GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray(),
            byteArrayOf(0, 1, 2, -1, -2, -3),
            byteArrayOf(),
        )
    }

    @Test
    fun `the listener drops an oversized message and carries on`() {
        val received = CopyOnWriteArrayList<File>()
        val listener = listener(received = received)
        val file = pdf("big.pdf")
        // Valid prefix, then far more than the limit; the file would be accepted if it were read at all.
        val big = "$token ${file.path}${" ".repeat(200_000)}\n".toByteArray()
        assertOnlySentinelArrives(listener.port, received, big)
    }

    @Test
    fun `the listener drops non pdf and nonexistent paths and carries on`() {
        val received = CopyOnWriteArrayList<File>()
        val listener = listener(received = received)
        val text = File(dir, "notes.txt").also { it.writeText("hi") }
        assertOnlySentinelArrives(
            listener.port, received,
            line(token, text.path),
            line(token, File(dir, "nope.pdf").path),
            line(token, "relative.pdf"),
            "$token ${pdf("a.pdf").path}\n$token ${pdf("b.pdf").path}\n".toByteArray(),
        )
    }

    @Test
    fun `the listener gives up on a silent client and serves the next one`() {
        val received = CopyOnWriteArrayList<File>()
        val listener = listener(readTimeoutMs = 300, received = received)
        Socket(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), listener.port).use {
            // Connected, never sends, never closes: the next message must still get through after the timeout.
            val file = pdf()
            sendRaw(listener.port, line(token, file.path))
            waitFor { received.isNotEmpty() }
            assertEquals(listOf(file), received.toList())
        }
    }

    @Test
    fun `a throwing callback does not end the listener`() {
        val received = CopyOnWriteArrayList<File>()
        var first = true
        val listener = HandOverListener.start(token) {
            if (first) { first = false; error("boom") }
            received += it
        }.also { closeables += it }
        sendRaw(listener.port, line(token, pdf("one.pdf").path))
        val second = pdf("two.pdf")
        sendRaw(listener.port, line(token, second.path))
        waitFor { received.isNotEmpty() }
        assertEquals(listOf(second), received.toList())
    }

    // --- endpoint file --------------------------------------------------------------------------------------

    @Test
    fun `the endpoint file round trips and is replaced on every write`() {
        HandOverEndpoint.write(dir, 4711, token)
        val first = assertNotNull(HandOverEndpoint.read(dir))
        assertEquals(4711, first.port)
        assertEquals(token, first.token)
        val other = HandOverProtocol.newToken()
        HandOverEndpoint.write(dir, 4712, other)
        assertEquals(4712, assertNotNull(HandOverEndpoint.read(dir)).port)
        assertEquals(other, HandOverEndpoint.read(dir)?.token)
        assertFalse(File(dir, "instance.handover.tmp").exists())
    }

    @Test
    fun `the endpoint file is readable by its owner only where permissions exist`() {
        HandOverEndpoint.write(dir, 4711, token)
        val file = HandOverEndpoint.file(dir).toPath()
        if ("posix" !in file.fileSystem.supportedFileAttributeViews()) return // Windows: inherited user-only ACL, checked by hand
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(file))
    }

    @Test
    fun `a malformed endpoint file reads as no endpoint`() {
        val file = HandOverEndpoint.file(dir)
        assertNull(HandOverEndpoint.read(dir))
        listOf("", "x\ny\n", "0\n$token\n", "70000\n$token\n", "4711\nshort\n", "4711\n${"g".repeat(64)}\n", "4711\n").forEach {
            file.writeText(it)
            assertNull(HandOverEndpoint.read(dir), it)
        }
        file.writeText("4711\n" + "a".repeat(5_000))
        assertNull(HandOverEndpoint.read(dir))
    }

    // --- lock and two-instance hand-over --------------------------------------------------------------------

    @Test
    fun `only one holder gets the instance lock until it is closed`() {
        val first = assertNotNull(SingleInstance.tryAcquire(dir))
        assertNull(SingleInstance.tryAcquire(dir))
        assertNull(SingleInstance.tryAcquire(dir))
        first.close()
        val again = assertNotNull(SingleInstance.tryAcquire(dir))
        again.close()
    }

    @Test
    fun `the lock is created in a data folder that does not exist yet`() {
        val fresh = File(dir, "new/Pool Print")
        SingleInstance.tryAcquire(fresh)?.close() ?: error("lock expected")
        assertTrue(File(fresh, "instance.lock").isFile)
    }

    @Test
    fun `listening publishes the endpoint and closing removes it`() {
        val instance = assertNotNull(SingleInstance.tryAcquire(dir))
        val listener = instance.listen { }
        val endpoint = assertNotNull(HandOverEndpoint.read(dir))
        assertEquals(listener.port, endpoint.port)
        instance.close()
        assertNull(HandOverEndpoint.read(dir))
    }

    @Test
    fun `a new instance removes a stale endpoint before it listens`() {
        HandOverEndpoint.write(dir, 1, "a".repeat(64)) // left by a crashed predecessor
        val instance = assertNotNull(SingleInstance.tryAcquire(dir))
        closeables += instance
        val listener = instance.listen { }
        val endpoint = assertNotNull(HandOverEndpoint.read(dir))
        assertEquals(listener.port, endpoint.port)
        assertNotEquals("a".repeat(64), endpoint.token)
    }

    @Test
    fun `a second instance hands its file to the first over loopback`() {
        val received = CopyOnWriteArrayList<File>()
        val first = assertNotNull(SingleInstance.tryAcquire(dir)).also { closeables += it }
        first.listen { received += it }
        assertNull(SingleInstance.tryAcquire(dir)) // the second one is turned away ...
        val file = pdf("from explorer.pdf")
        assertTrue(HandOverClient.send(dir, file)) // ... and hands over instead
        waitFor { received.isNotEmpty() }
        assertEquals(listOf(file), received.toList())
    }

    @Test
    fun `the client waits for a listener that is still starting`() {
        val received = CopyOnWriteArrayList<File>()
        val first = assertNotNull(SingleInstance.tryAcquire(dir)).also { closeables += it }
        val file = pdf()
        val starter = Thread {
            Thread.sleep(400) // lock is held, the endpoint is not there yet
            first.listen { received += it }
        }.also { it.start() }
        assertTrue(HandOverClient.send(dir, file, timeoutMs = 5_000))
        starter.join()
        waitFor { received.isNotEmpty() }
        assertEquals(listOf(file), received.toList())
    }

    @Test
    fun `the client gives up when nothing is listening`() {
        assertFalse(HandOverClient.send(dir, pdf(), timeoutMs = 300))
        HandOverEndpoint.write(dir, 1, "a".repeat(64)) // stale: nobody on port 1
        assertFalse(HandOverClient.send(dir, pdf(), timeoutMs = 300))
    }

    @Test
    fun `the client sends an absolute path`() {
        val received = CopyOnWriteArrayList<File>()
        val first = assertNotNull(SingleInstance.tryAcquire(dir)).also { closeables += it }
        first.listen { received += it }
        val relative = File(pdf("rel.pdf").absoluteFile.relativeTo(File("").absoluteFile).path)
        assertTrue(HandOverClient.send(dir, relative))
        waitFor { received.isNotEmpty() }
        assertTrue(received.single().isAbsolute)
        assertContentEquals("%PDF-1.4".toByteArray(), received.single().readBytes())
    }
}
