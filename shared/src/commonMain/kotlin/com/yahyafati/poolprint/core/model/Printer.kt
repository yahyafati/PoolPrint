package com.yahyafati.poolprint.core.model

import com.yahyafati.poolprint.core.validation.Patterns

enum class PrinterType { BW, COLOR }

/** A pool printer. The name is validated on construction, so a hostile name can't become a [Printer]. */
data class Printer(val name: String, val type: PrinterType) {
    init {
        require(Patterns.Identifier.matches(name)) { "invalid printer name" }
    }
}

/** The hardcoded printer table: the fallback that always works (A-08). */
object Printers {
    val all: List<Printer> = listOf(
        Printer("tfppr1", PrinterType.BW),
        Printer("tfppr2", PrinterType.COLOR),
        Printer("tfppr3", PrinterType.BW),
        Printer("tfppr4", PrinterType.COLOR),
    )

    val default: Printer = all.first()
}
