package com.yahyafati.poolprint.service

import com.yahyafati.poolprint.core.command.LprCommandBuilder
import com.yahyafati.poolprint.core.command.TestConnectionCommand
import com.yahyafati.poolprint.core.model.PrintJob
import com.yahyafati.poolprint.hostkeys.HostKeyStore
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.settings.AppSettings
import com.yahyafati.poolprint.transport.ByteSource
import com.yahyafati.poolprint.transport.ConnectionConfig
import com.yahyafati.poolprint.transport.PoolServer
import com.yahyafati.poolprint.transport.SshClient
import com.yahyafati.poolprint.transport.SshClientFactory
import com.yahyafati.poolprint.transport.SshError
import com.yahyafati.poolprint.transport.SshException
import kotlin.coroutines.cancellation.CancellationException

sealed interface TestConnectionResult {
    data object Success : TestConnectionResult

    /** First connection: the user has to check the fingerprint and trust it, then retry. */
    data class NeedsTrust(val key: PinnedHostKey) : TestConnectionResult

    data class Failed(val failure: UserFailure) : TestConnectionResult
}

sealed interface PrintResult {
    /** [jobId] is the CUPS request id (for example `tfppr1-123`) when `lpr` printed one. */
    data class Sent(val printerName: String, val jobId: String?) : PrintResult

    data class NeedsTrust(val key: PinnedHostKey) : PrintResult

    data class Failed(val failure: UserFailure) : PrintResult
}

/** Test connection and print, on top of [SshClient]. Holds no state. */
class PoolServices(
    private val settings: AppSettings,
    private val hostKeys: HostKeyStore,
    private val clients: SshClientFactory,
    private val host: String = PoolServer.HOST,
    private val port: Int = PoolServer.PORT,
) {
    private suspend fun client(): SshClient? {
        val username = settings.getUsername() ?: return null
        return clients.create(ConnectionConfig(username, host, port))
    }

    suspend fun testConnection(): TestConnectionResult {
        val client = client() ?: return TestConnectionResult.Failed(UserFailure(FailureKind.NotConfigured))
        return try {
            val result = client.exec(TestConnectionCommand.build())
            if (result.exitCode == 0 && result.stdout.trim() == TestConnectionCommand.EXPECTED_OUTPUT) {
                TestConnectionResult.Success
            } else if (result.exitCode != 0) {
                TestConnectionResult.Failed(
                    UserFailure(FailureKind.CommandFailed, exitCode = result.exitCode, details = result.stderr.trim().ifEmpty { null }),
                )
            } else {
                TestConnectionResult.Failed(UserFailure(FailureKind.UnexpectedOutput, details = result.stdout.take(200)))
            }
        } catch (e: SshException) {
            val error = e.error
            if (error is SshError.HostKeyUnknown) {
                TestConnectionResult.NeedsTrust(PinnedHostKey(error.keyType, error.fingerprint))
            } else {
                TestConnectionResult.Failed(ErrorCatalogue.fromTransport(error))
            }
        }
    }

    /**
     * Streams the document into `lpr`. Cancellation propagates as [CancellationException]: the client aborts
     * the connection, which is how "Cancel during upload" works.
     */
    suspend fun print(job: PrintJob, document: ByteSource, onUploaded: (Long) -> Unit): PrintResult {
        val client = client() ?: return PrintResult.Failed(UserFailure(FailureKind.NotConfigured))
        val printerName = job.options.printer.name
        return try {
            val result = client.exec(
                command = LprCommandBuilder.build(job),
                stdin = document,
                onUploaded = onUploaded,
                timeoutMs = PRINT_TIMEOUT_MS,
            )
            if (result.exitCode == 0) {
                PrintResult.Sent(printerName, parseJobId(result.stdout))
            } else {
                PrintResult.Failed(ErrorCatalogue.fromPrintExit(result.exitCode, result.stderr, printerName))
            }
        } catch (e: SshException) {
            val error = e.error
            if (error is SshError.HostKeyUnknown) {
                PrintResult.NeedsTrust(PinnedHostKey(error.keyType, error.fingerprint))
            } else {
                PrintResult.Failed(ErrorCatalogue.fromTransport(error))
            }
        } finally {
            runCatching { document.close() }
        }
    }

    /** Pins the key the user just confirmed. Only called after an explicit tap on "Trust". */
    suspend fun trust(key: PinnedHostKey) {
        hostKeys.pin(host, port, key)
    }

    companion object {
        /** A large PDF over a slow link takes a while; the uploading itself can be cancelled. */
        const val PRINT_TIMEOUT_MS = 10 * 60_000L

        private val RequestId = Regex("""request id is ([A-Za-z0-9_.-]+-\d+)""")

        fun parseJobId(stdout: String): String? = RequestId.find(stdout)?.groupValues?.get(1)
    }
}
