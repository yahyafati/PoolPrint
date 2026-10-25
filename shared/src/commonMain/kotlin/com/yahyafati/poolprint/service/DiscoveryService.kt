package com.yahyafati.poolprint.service

import com.yahyafati.poolprint.core.command.LpoptionsCommandBuilder
import com.yahyafati.poolprint.core.command.LpstatCommandBuilder
import com.yahyafati.poolprint.core.discovery.DiscoverySnapshot
import com.yahyafati.poolprint.core.discovery.LpoptionsParser
import com.yahyafati.poolprint.core.discovery.LpstatParser
import com.yahyafati.poolprint.core.discovery.PrinterCapabilities
import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.settings.DiscoveryStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface DiscoveryOutcome {
    /** The cache was refreshed; [snapshot] is the new state. */
    data class Updated(val snapshot: DiscoverySnapshot) : DiscoveryOutcome

    /** Nothing was learned. The old cache stays; the app silently keeps using it or the defaults. */
    data class Unavailable(val failure: UserFailure?) : DiscoveryOutcome

    /** First connection: discovery never trusts a host key by itself. The print flow asks the user. */
    data class NeedsTrust(val key: PinnedHostKey) : DiscoveryOutcome
}

/**
 * Fetches `lpstat -p` and `lpoptions -p <printer> -l`, parses them and keeps a persistent cache
 * (docs/print-options.md, "Discovery"). Failure is never an error for the app: the old cache or the
 * hardcoded printers and standard options stay in use.
 */
class DiscoveryService(
    private val services: PoolServices,
    private val store: DiscoveryStore,
    private val clock: () -> Long,
) {
    private val lock = Mutex()

    fun now(): Long = clock()

    /** The cache, or an empty snapshot. Never throws. */
    suspend fun cached(): DiscoverySnapshot = runCatching { store.load() }.getOrDefault(DiscoverySnapshot())

    suspend fun refreshPrinters(): DiscoveryOutcome {
        val text = when (val outcome = services.run(LpstatCommandBuilder.build())) {
            is RemoteOutcome.NeedsTrust -> return DiscoveryOutcome.NeedsTrust(outcome.key)
            is RemoteOutcome.Failed -> return DiscoveryOutcome.Unavailable(outcome.failure)
            is RemoteOutcome.Done -> outcome.result.takeIf { it.exitCode == 0 }?.stdout
                ?: return DiscoveryOutcome.Unavailable(
                    UserFailure(FailureKind.CommandFailed, exitCode = outcome.result.exitCode, details = outcome.result.stderr.trim().ifEmpty { null }),
                )
        }
        val printers = LpstatParser.parse(text)
        if (printers.isEmpty()) return DiscoveryOutcome.Unavailable(UserFailure(FailureKind.UnexpectedOutput, details = text.take(200)))
        return update { it.copy(printers = printers, printersFetchedAtMillis = clock()) }
    }

    suspend fun refreshOptions(printer: Printer): DiscoveryOutcome {
        val text = when (val outcome = services.run(LpoptionsCommandBuilder.build(printer))) {
            is RemoteOutcome.NeedsTrust -> return DiscoveryOutcome.NeedsTrust(outcome.key)
            is RemoteOutcome.Failed -> return DiscoveryOutcome.Unavailable(outcome.failure)
            is RemoteOutcome.Done -> outcome.result.takeIf { it.exitCode == 0 }?.stdout
                ?: return DiscoveryOutcome.Unavailable(
                    UserFailure(FailureKind.CommandFailed, exitCode = outcome.result.exitCode, details = outcome.result.stderr.trim().ifEmpty { null }),
                )
        }
        val parsed = LpoptionsParser.parse(text)
        if (parsed.recognizedLines == 0) return DiscoveryOutcome.Unavailable(UserFailure(FailureKind.UnexpectedOutput, details = text.take(200)))
        val capabilities = PrinterCapabilities(printer.name, parsed.options, clock())
        return update { it.copy(capabilities = it.capabilities + (printer.name to capabilities)) }
    }

    /** Printers first, then the options of [printer] when it is known (the manual "Refresh" in Settings). */
    suspend fun refreshAll(printer: Printer): DiscoveryOutcome {
        val printers = refreshPrinters()
        if (printers !is DiscoveryOutcome.Updated) return printers
        return refreshOptions(printer)
    }

    private suspend fun update(transform: (DiscoverySnapshot) -> DiscoverySnapshot): DiscoveryOutcome = lock.withLock {
        val updated = transform(cached())
        runCatching { store.save(updated) }
        DiscoveryOutcome.Updated(updated)
    }
}
