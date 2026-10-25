package com.yahyafati.poolprint.core.validation

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
}
