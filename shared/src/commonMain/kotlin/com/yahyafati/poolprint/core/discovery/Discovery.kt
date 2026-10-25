package com.yahyafati.poolprint.core.discovery

import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.validation.Patterns
import kotlinx.serialization.Serializable

enum class PrinterState { IDLE, PRINTING, DISABLED, UNKNOWN }

/** A printer found by `lpstat -p`. [reason] is the server's free text (display only, never sent back). */
@Serializable
data class DiscoveredPrinter(val name: String, val state: PrinterState, val reason: String? = null)

/**
 * One `lpoptions -l` option. [label] is display text from the server. [values] have passed the identifier
 * pattern, [default] is one of them (or null when the server's default was dropped).
 */
@Serializable
data class DiscoveredOption(
    val key: String,
    val label: String,
    val values: List<String>,
    val default: String? = null,
) {
    /** A single-value option offers no choice, so no control is generated for it. */
    val isChoosable: Boolean get() = values.size > 1
}

/** The cached options of one printer, with the time they were fetched (epoch millis). */
@Serializable
data class PrinterCapabilities(
    val printerName: String,
    val options: List<DiscoveredOption>,
    val fetchedAtMillis: Long,
) {
    fun option(key: String): DiscoveredOption? = options.firstOrNull { it.key == key }
}

/** Everything discovery has learned: what the UI reads (from memory) and what is persisted. */
@Serializable
data class DiscoverySnapshot(
    val printers: List<DiscoveredPrinter> = emptyList(),
    val printersFetchedAtMillis: Long? = null,
    val capabilities: Map<String, PrinterCapabilities> = emptyMap(),
) {
    /** Hardcoded printers first, then discovered ones that aren't in the table (type unknown), by name. */
    val knownPrinters: List<Printer>
        get() = Printers.all + printers.map { it.name }.filter { n -> Printers.all.none { it.name == n } }
            .distinct().sorted().map(Printers::resolve)

    fun stateOf(printer: Printer): PrinterState? = printers.firstOrNull { it.name == printer.name }?.state

    fun capabilitiesFor(printer: Printer): PrinterCapabilities? = capabilities[printer.name]

    /**
     * Re-applies the parser's rules to data read back from storage, so a tampered or stale file can't put
     * anything unsafe into the cache.
     */
    fun sanitized(): DiscoverySnapshot = copy(
        printers = printers.filter { Patterns.Identifier.matches(it.name) }.distinctBy { it.name },
        capabilities = capabilities.filter { (name, caps) -> Patterns.Identifier.matches(name) && caps.printerName == name }
            .mapValues { (_, caps) ->
                caps.copy(options = caps.options.mapNotNull { LpoptionsParser.sanitize(it) }.distinctBy { it.key })
            },
    )
}

object DiscoveryPolicy {
    /** Cached data older than this is refreshed in the background when the print sheet opens. */
    const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000

    fun isStale(fetchedAtMillis: Long?, nowMillis: Long): Boolean =
        fetchedAtMillis == null || nowMillis - fetchedAtMillis > STALE_AFTER_MILLIS || nowMillis < fetchedAtMillis
}
