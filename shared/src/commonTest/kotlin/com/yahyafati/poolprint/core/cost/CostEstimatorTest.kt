package com.yahyafati.poolprint.core.cost

import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.validation.Validators
import com.yahyafati.poolprint.core.validation.valueOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CostEstimatorTest {
    private val bw = Printers.all[0]
    private val colour = Printers.all[1]
    private val outputPages = CostPolicy(BillingUnit.PER_SIDE, PageRangeSemantics.OUTPUT_PAGES)
    private val inputPages = CostPolicy(BillingUnit.PER_SIDE, PageRangeSemantics.INPUT_PAGES)
    private val perSheet = CostPolicy(BillingUnit.PER_SHEET, PageRangeSemantics.OUTPUT_PAGES)

    private fun options(
        ranges: String = "",
        copies: Int = 1,
        duplex: Duplex = Duplex.OFF,
        up: PagesPerSheet = PagesPerSheet.ONE,
        printer: com.yahyafati.poolprint.core.model.Printer = bw,
    ) = PrintOptions(printer, copies, Validators.pageRanges(ranges).valueOrNull(), duplex, up)

    private fun cost(pages: Int, options: PrintOptions, policy: CostPolicy = CostPolicy()) =
        CostEstimator.estimate(options, pages, policy)

    @Test
    fun `the defaults are per side and output pages`() {
        assertEquals(CostPolicy(BillingUnit.PER_SIDE, PageRangeSemantics.OUTPUT_PAGES), CostPolicy())
    }

    @Test
    fun `no ranges bills every page at the printer rate`() {
        val estimate = cost(10, options())
        assertEquals(10, estimate.billablePages)
        assertEquals(50, estimate.costCents)
    }

    @Test
    fun `colour printers cost twice as much`() {
        assertEquals(100, cost(10, options(printer = colour)).costCents)
    }

    @Test
    fun `ranges bill only the selected pages`() {
        assertEquals(4, cost(10, options("2-4,9")).billablePages)
        assertEquals(20, cost(10, options("2-4,9")).costCents)
    }

    @Test
    fun `ranges past the end of the document are clamped`() {
        assertEquals(3, cost(10, options("8-20")).billablePages)
        assertEquals(0, cost(10, options("15-20")).billablePages)
        assertEquals(3, cost(10, options("8-20"), inputPages).billablePages)
    }

    @Test
    fun `two and four per sheet round up on odd page counts`() {
        assertEquals(3, cost(5, options(up = PagesPerSheet.TWO)).impressions)
        assertEquals(3, cost(9, options(up = PagesPerSheet.FOUR)).impressions)
        assertEquals(2, cost(8, options(up = PagesPerSheet.FOUR)).impressions)
        assertEquals(3, cost(5, options(up = PagesPerSheet.TWO), inputPages).impressions)
        assertEquals(3, cost(9, options(up = PagesPerSheet.FOUR), inputPages).impressions)
    }

    @Test
    fun `ranges mean output pages or input pages depending on the semantics`() {
        // 8 pages, 2 per sheet = 4 output pages. Ranges "1-2" are 2 sheets sides, or 2 input pages = 1 side.
        val o = options("1-2", up = PagesPerSheet.TWO)
        assertEquals(2, cost(8, o, outputPages).impressions)
        assertEquals(1, cost(8, o, inputPages).impressions)
        // Output semantics clamp to the 4 output pages.
        assertEquals(4, cost(8, options("1-9", up = PagesPerSheet.TWO), outputPages).impressions)
        // Input semantics: 7 selected input pages, 2 per sheet.
        assertEquals(4, cost(8, options("2-8", up = PagesPerSheet.TWO), inputPages).impressions)
    }

    @Test
    fun `duplex halves the sheets and rounds up on odd impressions`() {
        val estimate = cost(5, options(duplex = Duplex.LONG_EDGE))
        assertEquals(5, estimate.impressions)
        assertEquals(3, estimate.sheets)
        assertEquals(6, cost(12, options(duplex = Duplex.SHORT_EDGE)).sheets)
        assertEquals(5, cost(5, options()).sheets)
    }

    @Test
    fun `billing per side ignores duplex and billing per sheet does not`() {
        val duplex = options(duplex = Duplex.LONG_EDGE)
        assertEquals(25, cost(5, duplex).costCents)
        assertEquals(15, cost(5, duplex, perSheet).costCents)
        assertEquals(25, cost(5, options(), perSheet).costCents)
    }

    @Test
    fun `copies multiply the bill`() {
        val estimate = cost(3, options(copies = 4))
        assertEquals(12, estimate.billablePages)
        assertEquals(60, estimate.costCents)
        assertEquals(3 * 2 * 10, cost(3, options(copies = 2, printer = colour)).costCents)
    }

    @Test
    fun `two up duplex with copies on a colour printer`() {
        // 10 pages, 2 up = 5 sides; duplex = 3 sheets; 2 copies; per sheet = 6 billed at 10 ct.
        val o = options(copies = 2, duplex = Duplex.LONG_EDGE, up = PagesPerSheet.TWO, printer = colour)
        val estimate = cost(10, o, perSheet)
        assertEquals(5, estimate.impressions)
        assertEquals(3, estimate.sheets)
        assertEquals(6, estimate.billablePages)
        assertEquals(60, estimate.costCents)
    }

    @Test
    fun `an empty document costs nothing`() {
        assertEquals(0, cost(0, options()).costCents)
    }

    @Test
    fun `the warning appears above two euros and not at exactly two euros`() {
        assertFalse(cost(40, options()).exceedsWarningThreshold)
        assertTrue(cost(41, options()).exceedsWarningThreshold)
    }

    @Test
    fun `the last selectable page follows the semantics`() {
        assertEquals(4, outputPages.lastSelectablePage(8, 2))
        assertEquals(5, outputPages.lastSelectablePage(9, 2))
        assertEquals(8, inputPages.lastSelectablePage(8, 2))
        assertEquals(8, outputPages.lastSelectablePage(8, 1))
    }
}
