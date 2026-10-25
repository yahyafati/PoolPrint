package com.yahyafati.poolprint.core.validation

import com.yahyafati.poolprint.core.model.PageRanges
import com.yahyafati.poolprint.core.model.PrintLimits
import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.core.model.Printers

/** Turns raw UI input into typed values. Invalid input is rejected, never sanitised into acceptance. */
object Validators {
    /** Only a name that is in [known] **and** matches the identifier pattern gives a [Printer]. */
    fun printer(raw: String, known: List<Printer> = Printers.all): Validated<Printer> {
        if (!Patterns.Identifier.matches(raw)) return Validated.Invalid(ValidationError.PrinterUnknown)
        val match = known.firstOrNull { it.name == raw }
            ?: return Validated.Invalid(ValidationError.PrinterUnknown)
        return Validated.Valid(match)
    }

    /** Digits only: `2 -o x`, `1e3`, `+2`, `-1` and full-width digits are all rejected. */
    fun copies(raw: String): Validated<Int> {
        if (raw.isEmpty() || raw.length > 4 || !raw.all { it in '0'..'9' }) {
            return Validated.Invalid(ValidationError.CopiesNotANumber)
        }
        return copies(raw.toInt())
    }

    fun copies(value: Int): Validated<Int> =
        if (value in 1..PrintLimits.MAX_COPIES) {
            Validated.Valid(value)
        } else {
            Validated.Invalid(ValidationError.CopiesOutOfRange(1, PrintLimits.MAX_COPIES))
        }

    /**
     * Page ranges like `1-4, 7` (docs/print-options.md grammar). Spaces are dropped; anything else outside
     * the grammar is rejected. Blank input means "all pages" and gives `Valid(null)`. The result is sorted
     * and merged (`7,1-4,3-5` → `1-5,7`). With [lastPage] set, a page past it is rejected.
     */
    fun pageRanges(raw: String, lastPage: Int? = null): Validated<PageRanges?> {
        if (raw.length > PrintLimits.MAX_PAGE_RANGES_LENGTH) return Validated.Invalid(ValidationError.PageRangesTooLong)
        val compact = raw.replace(" ", "")
        if (compact.isEmpty()) return Validated.Valid(null)
        val parsed = mutableListOf<IntRange>()
        for (part in compact.split(',')) {
            val match = Patterns.PageRange.matchEntire(part) ?: return Validated.Invalid(ValidationError.PageRangesInvalid)
            val first = match.groupValues[1].toInt()
            val last = match.groupValues[2].ifEmpty { null }?.toInt() ?: first
            if (first > last) return Validated.Invalid(ValidationError.PageRangeReversed)
            parsed += first..last
        }
        val merged = mutableListOf<IntRange>()
        for (range in parsed.sortedBy { it.first }) {
            val previous = merged.lastOrNull()
            if (previous != null && range.first <= previous.last + 1) {
                merged[merged.lastIndex] = previous.first..maxOf(previous.last, range.last)
            } else {
                merged += range
            }
        }
        val ranges = PageRanges(merged)
        if (lastPage != null && ranges.highestPage > lastPage) {
            return Validated.Invalid(ValidationError.PageRangeBeyondDocument(lastPage))
        }
        return Validated.Valid(ranges)
    }
}
