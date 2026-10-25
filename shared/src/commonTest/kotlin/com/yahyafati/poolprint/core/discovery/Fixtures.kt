package com.yahyafati.poolprint.core.discovery

/**
 * SYNTHETIC samples of `lpstat -p`, `lpoptions -l` and `lpq` output, modelled on the CUPS documentation.
 * They are NOT real captures from the pool server. Replace them when the owner provides real output
 * (docs/decisions.md, open question 1). Owners are anonymised.
 */
object Fixtures {
    val lpstat = """
        printer tfppr1 is idle.  enabled since Mon 06 Oct 2026 09:12:01 CEST
        printer tfppr2 now printing tfppr2-4711.  enabled since Mon 06 Oct 2026 09:12:01 CEST
        printer tfppr3 disabled since Fri 03 Oct 2026 17:00:00 CEST -
        	Paper jam
        printer tfppr4 is idle.  enabled since Mon 06 Oct 2026 09:12:01 CEST
        printer extra-lab is idle.  enabled since Mon 06 Oct 2026 09:12:01 CEST
    """.trimIndent()

    val lpoptions = """
        PageSize/Media Size: *A4 A5 Letter Legal Custom.WIDTHxHEIGHT
        Duplex/2-Sided Printing: *None DuplexNoTumble DuplexTumble
        ColorModel/Color Mode: Gray *RGB
        HPEconoMode/EconoMode: *False True
        Resolution/Resolution: 300dpi *600dpi 1200dpi
    """.trimIndent()

    val lpqEmpty = """
        tfppr1 is ready
        no entries
    """.trimIndent()

    val lpqJobs = """
        tfppr1 is ready and printing
        Rank    Owner   Job     File(s)                         Total Size
        active  jdoe    1234    (stdin)                         482304 bytes
        1st     muster  1235    slides final.pdf                1048576 bytes
        2nd     jdoe    1236    (stdin)                         2048 bytes
    """.trimIndent()
}
