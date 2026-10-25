package com.yahyafati.poolprint.core.queue

/**
 * Parses `lpq -P <printer>` (docs/print-options.md): a status line, a `Rank Owner Job File(s) Total Size`
 * header, then one row per job, or `no entries`. Rows that don't have the shape are skipped. Never throws.
 */
object LpqParser {
    private val Row = Regex("""^(active|\d+(?:st|nd|rd|th))\s+(\S+)\s+(\d{1,9})\s+(.*?)\s+(\d+)\s+bytes$""")
    private const val MAX_ENTRIES = 200

    /** Null when the text doesn't look like `lpq` output at all (no status line, header or rows). */
    fun parse(printerName: String, text: String): QueueSnapshot? {
        val lines = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val entries = mutableListOf<QueueEntry>()
        var sawHeader = false
        var sawNoEntries = false
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Rank") && "Owner" in trimmed -> sawHeader = true
                trimmed.equals("no entries", ignoreCase = true) -> sawNoEntries = true
                else -> Row.matchEntire(trimmed)?.let { m ->
                    if (entries.size < MAX_ENTRIES) {
                        entries += QueueEntry(
                            rank = m.groupValues[1],
                            owner = m.groupValues[2],
                            jobId = m.groupValues[3],
                            fileLabel = m.groupValues[4].filter { !it.isISOControl() }.take(120),
                            sizeBytes = m.groupValues[5].toLongOrNull(),
                        )
                    }
                }
            }
        }
        if (entries.isEmpty() && !sawHeader && !sawNoEntries) return null
        val status = lines.first().trim().takeIf { !it.startsWith("Rank") && !it.equals("no entries", true) && Row.matchEntire(it) == null }
        return QueueSnapshot(printerName, status?.take(200), entries)
    }
}
