package com.yahyafati.poolprint.core.command

import com.yahyafati.poolprint.core.Payloads
import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.Orientation
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.model.PaperSize
import com.yahyafati.poolprint.core.model.PrintJob
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.core.model.PrinterType
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.validation.Patterns
import com.yahyafati.poolprint.core.validation.Validators
import com.yahyafati.poolprint.core.validation.valueOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LprCommandBuilderTest {
    private fun ranges(raw: String) = Validators.pageRanges(raw).valueOrNull()

    private fun command(options: PrintOptions = PrintOptions(), name: String = "doc.pdf") =
        LprCommandBuilder.build(PrintJob(options, name)).value

    private val defaults = "'lpr' '-P' 'tfppr1' '-o' 'sides=one-sided' '-o' 'media=A4'"

    @Test
    fun `builds the default command with explicit sides and media`() {
        assertEquals(defaults, command())
    }

    @Test
    fun `adds the copies flag only when copies is above one`() {
        assertEquals(
            "'lpr' '-P' 'tfppr3' '-#' '2' '-o' 'sides=one-sided' '-o' 'media=A4'",
            command(PrintOptions(printer = Printers.all[2], copies = 2)),
        )
        assertFalse("'-#'" in command(PrintOptions(copies = 1)))
    }

    @Test
    fun `maps every duplex mode`() {
        assertEquals("one-sided", Duplex.OFF.cupsValue)
        assertTrue("'sides=one-sided'" in command(PrintOptions(duplex = Duplex.OFF)))
        assertTrue("'sides=two-sided-long-edge'" in command(PrintOptions(duplex = Duplex.LONG_EDGE)))
        assertTrue("'sides=two-sided-short-edge'" in command(PrintOptions(duplex = Duplex.SHORT_EDGE)))
    }

    @Test
    fun `adds number-up only above one page per sheet`() {
        assertFalse("number-up" in command(PrintOptions(pagesPerSheet = PagesPerSheet.ONE)))
        assertTrue("'-o' 'number-up=2'" in command(PrintOptions(pagesPerSheet = PagesPerSheet.TWO)))
        assertTrue("'-o' 'number-up=4'" in command(PrintOptions(pagesPerSheet = PagesPerSheet.FOUR)))
    }

    @Test
    fun `sends the canonical page ranges only when set`() {
        assertFalse("page-ranges" in command())
        assertTrue("'-o' 'page-ranges=1-5,7'" in command(PrintOptions(pageRanges = ranges("7, 1-4, 3-5"))))
    }

    @Test
    fun `adds landscape and fit-to-page only when on`() {
        assertFalse("landscape" in command(PrintOptions(orientation = Orientation.PORTRAIT)))
        assertFalse("fit-to-page" in command(PrintOptions(fitToPage = false)))
        assertTrue("'-o' 'landscape'" in command(PrintOptions(orientation = Orientation.LANDSCAPE)))
        assertTrue("'-o' 'fit-to-page'" in command(PrintOptions(fitToPage = true)))
    }

    @Test
    fun `maps every paper size`() {
        assertEquals(
            listOf("A4", "A3", "A5", "Letter"),
            PaperSize.entries.map { it.cupsValue },
        )
        for (size in PaperSize.entries) {
            assertTrue("'-o' 'media=${size.cupsValue}'" in command(PrintOptions(paperSize = size)))
        }
    }

    @Test
    fun `puts every option in the documented order`() {
        val options = PrintOptions(
            printer = Printers.all[3],
            copies = 3,
            pageRanges = ranges("2-6"),
            duplex = Duplex.SHORT_EDGE,
            pagesPerSheet = PagesPerSheet.FOUR,
            orientation = Orientation.LANDSCAPE,
            fitToPage = true,
            paperSize = PaperSize.LETTER,
        )
        assertEquals(
            "'lpr' '-P' 'tfppr4' '-#' '3' '-o' 'sides=two-sided-short-edge' '-o' 'number-up=4' " +
                "'-o' 'page-ranges=2-6' '-o' 'landscape' '-o' 'fit-to-page' '-o' 'media=Letter'",
            command(options),
        )
    }

    @Test
    fun `every combination of options builds a command of safe tokens in the documented shape`() {
        val rangeChoices = listOf(null, ranges("1"), ranges("3-9,12"))
        var checked = 0
        for (printer in Printers.all) for (copies in listOf(1, 2, 20)) for (pages in rangeChoices)
            for (duplex in Duplex.entries) for (up in PagesPerSheet.entries) for (orientation in Orientation.entries)
                for (fit in listOf(false, true)) for (paper in PaperSize.entries) {
                    val options = PrintOptions(printer, copies, pages, duplex, up, orientation, fit, paper)
                    val value = command(options)
                    val tokens = Regex("'([^']*)'").findAll(value).map { it.groupValues[1] }.toList()
                    assertEquals(value, tokens.joinToString(" ") { "'$it'" }, "no stray characters between tokens")
                    assertTrue(tokens.all { Patterns.SafeToken.matches(it) }, value)
                    assertEquals(listOf("lpr", "-P", printer.name), tokens.take(3))
                    val rest = tokens.drop(3).let { if (copies > 1) it.drop(2) else it }
                    val optionValues = rest.filterIndexed { i, _ -> i % 2 == 1 }
                    assertTrue(rest.filterIndexed { i, _ -> i % 2 == 0 }.all { it == "-o" }, value)
                    assertEquals(duplex.cupsValue, optionValues.first().removePrefix("sides="))
                    assertEquals("media=${paper.cupsValue}", optionValues.last())
                    assertEquals(up != PagesPerSheet.ONE, optionValues.any { it.startsWith("number-up=") })
                    assertEquals(pages != null, optionValues.any { it.startsWith("page-ranges=") })
                    assertEquals(orientation == Orientation.LANDSCAPE, "landscape" in optionValues)
                    assertEquals(fit, "fit-to-page" in optionValues)
                    checked++
                }
        assertEquals(4 * 3 * 3 * 3 * 3 * 2 * 2 * 4, checked)
    }

    @Test
    fun `never adds a file argument or a job title`() {
        val value = command(PrintOptions(copies = 3))
        assertFalse("'-T'" in value)
        assertFalse(".pdf" in value)
    }

    @Test
    fun `filename never appears in the command`() {
        for (payload in Payloads.all + "lecture-05.pdf" + "secret thesis.pdf") {
            assertEquals(defaults, command(name = payload), "payload: $payload")
        }
    }

    @Test
    fun `a printer with a hostile name can't be constructed`() {
        for (payload in Payloads.all) {
            assertFailsWith<IllegalArgumentException>("payload: $payload") { Printer(payload, PrinterType.BW) }
        }
    }

    @Test
    fun `copies outside the allowed range can't be constructed`() {
        for (copies in listOf(0, -1, 21, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertFailsWith<IllegalArgumentException>("copies: $copies") { PrintOptions(copies = copies) }
        }
    }

    @Test
    fun `hostile page ranges are rejected, and whatever is accepted is digits, commas and dashes`() {
        val suffixes = Payloads.all.filter { it.isNotEmpty() }
        val inputs = Payloads.all + suffixes.map { "1-4$it" } + suffixes.map { "1,$it" } + suffixes.map { "$it,3" }
        for (input in inputs) {
            val pages = Validators.pageRanges(input).valueOrNull() ?: continue
            assertTrue(Regex("^[0-9,-]+$").matches(pages.canonical), "input: $input -> ${pages.canonical}")
            assertEquals("'-o' 'page-ranges=${pages.canonical}'", Regex("'-o' 'page-ranges=[^']*'").find(command(PrintOptions(pageRanges = pages)))?.value)
        }
        for (payload in suffixes.filter { p -> p.any { it !in "0123456789,- " } }) {
            for (input in listOf("1-4$payload", "1,$payload", "$payload,3", payload)) {
                assertNull(Validators.pageRanges(input).valueOrNull(), "input: $input")
            }
        }
    }

    @Test
    fun `the final token check rejects anything outside the safe pattern`() {
        // "#", "--help" and "-tfppr1" are inside the pattern (the builder itself needs `-P` and `-#`);
        // layer 2 keeps them out of printer names, which is covered above.
        for (payload in Payloads.all.filterNot { Patterns.SafeToken.matches(it) }) {
            assertFailsWith<IllegalArgumentException>("payload: $payload") { RemoteCommand.fromTokens(listOf("lpr", payload)) }
        }
    }

    @Test
    fun `test connection command is echo ok`() {
        assertEquals("'echo' 'ok'", TestConnectionCommand.build().value)
    }
}
