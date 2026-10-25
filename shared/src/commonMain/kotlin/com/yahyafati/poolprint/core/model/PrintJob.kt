package com.yahyafati.poolprint.core.model

object PrintLimits {
    /** Safety cap: 6 EUR buys only 120 b/w pages. */
    const val MAX_COPIES = 20
}

/** M1 options. The full set arrives in M2. */
data class PrintOptions(
    val printer: Printer = Printers.default,
    val copies: Int = 1,
) {
    init {
        require(copies in 1..PrintLimits.MAX_COPIES) { "copies out of range" }
    }
}

/**
 * What to print. [documentName] is for the UI only; the command builders never read it, so a filename
 * can't reach the remote shell.
 */
data class PrintJob(val options: PrintOptions, val documentName: String)
