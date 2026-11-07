package com.yahyafati.poolprint.desktop

import com.yahyafati.poolprint.app.desktopDataDir
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

/**
 * The running instance on Windows (D-28): whoever holds the exclusive lock on `instance.lock` in the data folder.
 * The OS drops the lock when the process dies, so a crash leaves nothing stale. [close] releases it.
 */
internal class SingleInstance private constructor(
    private val dir: File,
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    private var listener: HandOverListener? = null

    /** Starts the loopback listener and publishes its port and token; every valid PDF path is passed to [onPdf]. */
    fun listen(onPdf: (File) -> Unit): HandOverListener {
        val token = HandOverProtocol.newToken()
        // A stale endpoint from a crashed predecessor must not be there while the new listener starts.
        HandOverEndpoint.delete(dir)
        val started = HandOverListener.start(token, onPdf = onPdf)
        HandOverEndpoint.write(dir, started.port, token)
        listener = started
        return started
    }

    @Synchronized
    override fun close() {
        listener?.close()
        listener = null
        HandOverEndpoint.delete(dir)
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        /** The instance lock if this process can have it; null if another instance (or another holder in this JVM) has it. */
        fun tryAcquire(dir: File): SingleInstance? {
            dir.mkdirs()
            val channel = FileChannel.open(File(dir, "instance.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null // held by this very JVM
            } catch (e: IOException) {
                channel.close()
                throw e
            }
            if (lock == null) {
                channel.close()
                return null
            }
            return SingleInstance(dir, channel, lock)
        }
    }
}

/** What `main` does after [startDesktop]. */
sealed interface StartupResult {
    /** Carry on and open the window; close [instance] (if any) when the app exits. */
    class Run(val instance: AutoCloseable?) : StartupResult

    /** Exit the process with [status] without opening a window or the DataStore; print [message] to stderr if present. */
    class Exit(val status: Int, val message: String? = null) : StartupResult
}

/**
 * Everything `main(args)` does before the window exists:
 * - files named on the command line are offered to [incoming] on every OS (macOS passes none, Open With arrives
 *   through the open-file handler);
 * - on Windows, only one instance runs (D-28): the first one listens for hand-overs, a later one passes its first
 *   PDF to it and exits ([StartupResult.Exit]) **before** anything opens the DataStore, the key store or the network.
 *
 * [dataDir] is injectable for tests.
 */
fun startDesktop(
    os: DesktopOs,
    args: Array<String>,
    incoming: IncomingPdfs,
    dataDir: File = desktopDataDir(os, System.getProperty("user.home"), System::getenv),
): StartupResult {
    val files = args.filter { it.isNotBlank() }.map { File(it).absoluteFile }
    if (os != DesktopOs.Windows) {
        incoming.offer(files)
        return StartupResult.Run(null)
    }

    val instance = try {
        SingleInstance.tryAcquire(dataDir)
    } catch (e: IOException) {
        return StartupResult.Exit(1, "Pool Print could not check for a running copy of itself (${e.javaClass.simpleName}); not starting.")
    }
    if (instance != null) {
        incoming.offer(files)
        try {
            instance.listen { incoming.offer(listOf(it)) }
        } catch (e: IOException) {
            // Without a listener the app still works; only "Open with" while it runs can't reach it.
            System.err.println("Pool Print: could not start the hand-over listener (${e.javaClass.simpleName}).")
        }
        Runtime.getRuntime().addShutdownHook(Thread { instance.close() })
        return StartupResult.Run(instance)
    }

    // Another instance runs: pass it the first PDF (the one document the app would open anyway) and leave.
    val pdf = files.firstOrNull(::isPdfFile) ?: return StartupResult.Exit(0)
    return if (HandOverClient.send(dataDir, pdf)) StartupResult.Exit(0)
    else StartupResult.Exit(1, "Pool Print is already running, but it could not be reached to open ${pdf.name}.")
}
