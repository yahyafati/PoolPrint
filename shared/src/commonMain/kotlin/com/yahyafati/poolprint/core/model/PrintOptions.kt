package com.yahyafati.poolprint.core.model

object PrintLimits {
    /** Safety cap: 6 EUR buys only 120 b/w pages. */
    const val MAX_COPIES = 20

    /** Page-range input longer than this is rejected before parsing. */
    const val MAX_PAGE_RANGES_LENGTH = 200

    const val MAX_PRESET_NAME = 40
    const val MAX_PRESETS = 30
}

/** Each enum carries the only strings that can reach `lpr` for it (docs/print-options.md). */
enum class Duplex(val cupsValue: String) {
    OFF("one-sided"),
    LONG_EDGE("two-sided-long-edge"),
    SHORT_EDGE("two-sided-short-edge"),
}

enum class PagesPerSheet(val count: Int) {
    ONE(1),
    TWO(2),
    FOUR(4),
}

enum class Orientation { PORTRAIT, LANDSCAPE }

enum class PaperSize(val cupsValue: String) {
    A4("A4"),
    A3("A3"),
    A5("A5"),
    LETTER("Letter"),
}

/**
 * A validated, canonical set of 1-based page ranges: sorted, merged, non-overlapping. Only [parse] in
 * the validators makes one, so [canonical] is always safe to put on a command line.
 */
class PageRanges internal constructor(val ranges: List<IntRange>) {
    init {
        require(ranges.isNotEmpty() && ranges.all { it.first >= 1 && it.first <= it.last })
    }

    /** `1-5,7`: what is sent to CUPS and what the estimate uses. */
    val canonical: String =
        ranges.joinToString(",") { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" }

    val highestPage: Int get() = ranges.last().last

    /** How many of the pages `1..pageCount` the ranges select. */
    fun countWithin(pageCount: Int): Int =
        ranges.sumOf { r -> (minOf(r.last, pageCount) - r.first + 1).coerceAtLeast(0) }

    override fun equals(other: Any?) = other is PageRanges && other.canonical == canonical
    override fun hashCode() = canonical.hashCode()
    override fun toString() = canonical
}

/** Everything the user can choose on the print sheet. Defaults follow docs/print-options.md. */
data class PrintOptions(
    val printer: Printer = Printers.default,
    val copies: Int = 1,
    val pageRanges: PageRanges? = null,
    val duplex: Duplex = Duplex.OFF,
    val pagesPerSheet: PagesPerSheet = PagesPerSheet.ONE,
    val orientation: Orientation = Orientation.PORTRAIT,
    val fitToPage: Boolean = false,
    val paperSize: PaperSize = PaperSize.A4,
    /** Discovered printer-specific options that differ from the printer's default (M3), sorted by key. */
    val discovered: List<DiscoveredSetting> = emptyList(),
) {
    init {
        require(copies in 1..PrintLimits.MAX_COPIES) { "copies out of range" }
        require(discovered.all { it.printerName == printer.name }) { "discovered setting for another printer" }
    }
}

/**
 * What to print. [documentName] is for the UI only; the command builders never read it, so a filename
 * can't reach the remote shell.
 */
data class PrintJob(val options: PrintOptions, val documentName: String)
