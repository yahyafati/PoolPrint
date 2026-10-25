package com.yahyafati.poolprint.core.discovery

import com.yahyafati.poolprint.core.validation.Patterns

/**
 * Parses `lpstat -p` (docs/print-options.md). Lines `printer <name> …` give a printer and its state;
 * indented lines after one are its status reason. Names that fail the identifier pattern are dropped.
 * Never throws; text it doesn't understand (for example a localised server) gives an empty list.
 */
object LpstatParser {
    private val PrinterLine = Regex("""^printer\s+(\S+)\s*(.*)$""")
    private const val MAX_PRINTERS = 100
    private const val MAX_REASON = 200

    fun parse(text: String): List<DiscoveredPrinter> {
        val result = mutableListOf<DiscoveredPrinter>()
        var current: DiscoveredPrinter? = null
        fun flush() {
            current?.let { if (result.size < MAX_PRINTERS && result.none { p -> p.name == it.name }) result += it }
            current = null
        }
        for (line in text.lines()) {
            val header = PrinterLine.matchEntire(line.trimEnd())
            if (header != null) {
                flush()
                val name = header.groupValues[1]
                if (Patterns.Identifier.matches(name)) current = DiscoveredPrinter(name, stateOf(header.groupValues[2]))
            } else if (line.isNotEmpty() && line[0].isWhitespace() && line.isNotBlank()) {
                current?.let { if (it.reason == null) current = it.copy(reason = line.trim().take(MAX_REASON)) }
            } else {
                flush()
            }
        }
        flush()
        return result
    }

    private fun stateOf(rest: String): PrinterState {
        val text = rest.trimStart()
        return when {
            text.startsWith("is idle") -> PrinterState.IDLE
            text.startsWith("now printing") -> PrinterState.PRINTING
            text.startsWith("disabled") -> PrinterState.DISABLED
            else -> PrinterState.UNKNOWN
        }
    }
}
