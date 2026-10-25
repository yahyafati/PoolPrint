package com.yahyafati.poolprint.settings

import com.yahyafati.poolprint.core.Payloads
import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.Orientation
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.model.PaperSize
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.validation.Validators
import com.yahyafati.poolprint.core.validation.valueOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

class StoredPrintOptionsTest {
    private val everything = PrintOptions(
        printer = Printers.all[1], copies = 4, pageRanges = Validators.pageRanges("1-3").valueOrNull(),
        duplex = Duplex.SHORT_EDGE, pagesPerSheet = PagesPerSheet.FOUR, orientation = Orientation.LANDSCAPE,
        fitToPage = true, paperSize = PaperSize.LETTER,
    )

    @Test
    fun `round trips the layout options through json and drops copies and ranges`() {
        val restored = StoredPrintOptions.fromJson(StoredPrintOptions.from(everything).toJson())
        assertEquals(everything.copy(copies = 1, pageRanges = null), restored)
    }

    @Test
    fun `nothing saved gives the defaults`() {
        assertEquals(PrintOptions(), StoredPrintOptions.fromJson(null))
    }

    @Test
    fun `unreadable json gives the defaults`() {
        for (text in listOf("", "not json", "[]", "{\"duplex\": 3}")) {
            assertEquals(PrintOptions(), StoredPrintOptions.fromJson(text), text)
        }
    }

    @Test
    fun `unknown or hostile values fall back field by field`() {
        val stored = StoredPrintOptions(printer = "tfppr9", duplex = "SIDEWAYS", pagesPerSheet = 3, orientation = "UPSIDE_DOWN", paperSize = "A0", fitToPage = true)
        assertEquals(PrintOptions(fitToPage = true), stored.toOptions())
        for (payload in Payloads.all) {
            assertEquals(PrintOptions(), StoredPrintOptions(printer = payload, duplex = payload, orientation = payload, paperSize = payload).toOptions(), payload)
        }
    }

    @Test
    fun `ignores unknown keys from a newer version`() {
        val restored = StoredPrintOptions.fromJson("""{"printer":"tfppr3","somethingNew":1}""")
        assertEquals(Printers.all[2], restored.printer)
    }
}
