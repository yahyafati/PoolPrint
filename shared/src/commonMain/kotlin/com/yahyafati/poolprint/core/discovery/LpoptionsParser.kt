package com.yahyafati.poolprint.core.discovery

import com.yahyafati.poolprint.core.validation.Patterns

/** [recognizedLines] counts lines that had the `Key/Label: values` shape, whether or not an option survived. */
data class LpoptionsResult(val options: List<DiscoveredOption>, val recognizedLines: Int)

/**
 * Parses `lpoptions -p <printer> -l` (docs/print-options.md). `Custom.*` values, keys or values that fail the
 * identifier pattern, and options that collide with a standard control are dropped; so are options left
 * without values. Unparseable lines are skipped. Never throws.
 */
object LpoptionsParser {
    private val OptionLine = Regex("""^([^/\s:]+)/([^:]*):\s*(.*)$""")
    private const val MAX_OPTIONS = 60
    private const val MAX_VALUES = 40
    private const val MAX_LABEL = 80

    /** The standard controls win over a discovered option of the same name (compared ignoring case). */
    private val Reserved = setOf(
        "pagesize", "pageregion", "media", "duplex", "sides", "number-up", "page-ranges", "copies",
        "orientation-requested", "landscape", "fit-to-page",
    )

    fun collidesWithStandard(key: String): Boolean = key.lowercase() in Reserved

    fun parse(text: String): LpoptionsResult {
        val options = mutableListOf<DiscoveredOption>()
        var recognized = 0
        for (line in text.lines()) {
            val match = OptionLine.matchEntire(line.trim()) ?: continue
            recognized++
            if (options.size >= MAX_OPTIONS) continue
            val key = match.groupValues[1]
            if (options.any { it.key == key }) continue
            var default: String? = null
            val values = mutableListOf<String>()
            for (raw in match.groupValues[3].split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                val isDefault = raw.startsWith("*")
                val value = raw.removePrefix("*")
                if (value.startsWith("Custom.") || !Patterns.Identifier.matches(value) || value in values) continue
                if (values.size >= MAX_VALUES) break
                values += value
                if (isDefault) default = value
            }
            val option = DiscoveredOption(key, cleanLabel(match.groupValues[2], key), values, default)
            sanitize(option)?.let { options += it }
        }
        return LpoptionsResult(options, recognized)
    }

    /** The rules for one option, also applied to cached data on load. Null means "drop it". */
    fun sanitize(option: DiscoveredOption): DiscoveredOption? {
        if (!Patterns.Identifier.matches(option.key) || collidesWithStandard(option.key)) return null
        val values = option.values.filter { Patterns.Identifier.matches(it) }.distinct()
        if (values.isEmpty()) return null
        return option.copy(
            label = cleanLabel(option.label, option.key),
            values = values,
            default = option.default?.takeIf { it in values },
        )
    }

    private fun cleanLabel(raw: String, key: String): String =
        raw.filter { !it.isISOControl() }.trim().take(MAX_LABEL).ifEmpty { key }
}
