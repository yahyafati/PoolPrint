package com.yahyafati.poolprint.transport.testserver

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.shell.InvertedShell
import org.apache.sshd.server.shell.InvertedShellWrapper
import org.apache.sshd.server.shell.ProcessShellFactory

/**
 * The POSIX `sh` the test server runs exec requests through, like the real (Linux) pool server. MINA SSHD's own
 * factory uses `cmd.exe /C` on Windows, which can't run the commands the app sends, so on Windows this is Git for
 * Windows' `sh.exe` (present on GitHub's `windows-latest` runners), and paths handed to it are written in its
 * `/c/Users/…` form. Without an `sh` the SSH tests are skipped, not failed ([TestSshServer.start]).
 */
object PosixShell {
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /** The `sh` executable, or null on a Windows machine without Git for Windows. */
    val executable: String? by lazy {
        if (!windows) return@lazy "/bin/sh"
        val programFiles = listOfNotNull(System.getenv("ProgramFiles"), System.getenv("ProgramW6432"), "C:\\Program Files")
        val candidates = programFiles.map { File(it, "Git\\bin\\sh.exe") } +
            System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }.map { File(it, "sh.exe") }
        candidates.firstOrNull { it.isFile }?.absolutePath
    }

    /** [path] as the shell sees it: unchanged on Unix, `C:\a\b` → `/c/a/b` on Windows. */
    fun path(path: Path): String {
        val text = path.toAbsolutePath().toString()
        if (!windows) return text
        val drive = Regex("^([A-Za-z]):\\\\(.*)$").find(text) ?: return text.replace('\\', '/')
        return "/" + drive.groupValues[1].lowercase() + "/" + drive.groupValues[2].replace('\\', '/')
    }

    /** Runs each exec request as `<sh> -c <command>`. */
    internal class Factory(command: String) : ProcessShellFactory(command, command) {
        override fun resolveEffectiveCommand(channel: ChannelSession, rawCommand: String, parsedElements: List<String>?) =
            listOf(checkNotNull(executable) { "No POSIX sh" }, "-c", rawCommand)

        override fun createShell(channel: ChannelSession): Command = OpenSshLikeShellWrapper(createInvertedShell(channel))
    }

    /**
     * MINA's wrapper gives up on the whole shell when writing the client's data to a process that has stopped reading
     * stdin fails (broken pipe), and the process's last stdout/stderr is lost. OpenSSH instead stops feeding stdin
     * but still sends the output and the exit status, then closes the channel under the client's upload. This does
     * the same, so the client's handling of a command that exits mid-upload (lpr rejecting a job) is what is tested.
     */
    private class OpenSshLikeShellWrapper(shell: InvertedShell) : InvertedShellWrapper(shell) {
        @Volatile private var stdinBroken = false
        private var channelIn: InputStream? = null

        override fun setInputStream(input: InputStream) {
            // Once stdin is broken, unread client data no longer counts as "still in transit", so the shell can end.
            val wrapped = object : FilterInputStream(input) {
                override fun available(): Int = if (stdinBroken) 0 else super.available()
            }
            channelIn = wrapped
            super.setInputStream(wrapped)
        }

        override fun pumpStream(input: InputStream, out: OutputStream, buffer: ByteArray): Boolean {
            if (input !== channelIn) return super.pumpStream(input, out, buffer)
            if (stdinBroken) return false
            return try {
                super.pumpStream(input, out, buffer)
            } catch (e: IOException) {
                stdinBroken = true
                runCatching { out.close() }
                false
            }
        }
    }
}
