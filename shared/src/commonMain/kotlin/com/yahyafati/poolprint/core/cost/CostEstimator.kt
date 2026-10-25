package com.yahyafati.poolprint.core.cost

import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.PrinterType

/** Which pages `page-ranges` selects when `number-up` is on (docs/decisions.md, D-07). */
enum class PageRangeSemantics { OUTPUT_PAGES, INPUT_PAGES }

/** Whether the pool bills a duplex sheet as one page or as two (D-06). */
enum class BillingUnit { PER_SIDE, PER_SHEET }

/** The rates and thresholds, in integer cents. Constants on purpose: they change rarely. */
object CostRates {
    const val BW_CENTS = 5
    const val COLOR_CENTS = 10

    /** A third of the monthly budget (6 €): worth a warning on the Print button. */
    const val WARNING_THRESHOLD_CENTS = 200

    fun centsPerPage(type: PrinterType): Int = if (type == PrinterType.BW) BW_CENTS else COLOR_CENTS // UNKNOWN is billed as colour
}

/** The two open questions, as settings with the documented defaults. Flip them once the manual checks are done. */
data class CostPolicy(
    val billingUnit: BillingUnit = BillingUnit.PER_SIDE,
    val rangeSemantics: PageRangeSemantics = PageRangeSemantics.OUTPUT_PAGES,
) {
    /**
     * The highest page number a range may name for a [pageCount]-page document with [pagesPerSheet] pages
     * per sheet: with output-page semantics the numbering is after the layout.
     */
    fun lastSelectablePage(pageCount: Int, pagesPerSheet: Int): Int = when (rangeSemantics) {
        PageRangeSemantics.INPUT_PAGES -> pageCount
        PageRangeSemantics.OUTPUT_PAGES -> ceilDiv(pageCount, pagesPerSheet)
    }
}

/**
 * @property impressions printed sides per copy
 * @property sheets sheets of paper per copy
 * @property billablePages what the Print button calls "pages": per copy sides or sheets, times copies
 */
data class CostEstimate(val impressions: Int, val sheets: Int, val billablePages: Int, val costCents: Int) {
    val exceedsWarningThreshold: Boolean get() = costCents > CostRates.WARNING_THRESHOLD_CENTS
}

object CostEstimator {
    /** [pageCount] is the document's page count, counted locally. Orientation, fit-to-page and paper size don't matter. */
    fun estimate(options: PrintOptions, pageCount: Int, policy: CostPolicy = CostPolicy()): CostEstimate {
        require(pageCount >= 0)
        val up = options.pagesPerSheet.count
        val ranges = options.pageRanges
        val impressions = when (policy.rangeSemantics) {
            PageRangeSemantics.INPUT_PAGES -> ceilDiv(ranges?.countWithin(pageCount) ?: pageCount, up)
            PageRangeSemantics.OUTPUT_PAGES -> {
                val outputPages = ceilDiv(pageCount, up)
                ranges?.countWithin(outputPages) ?: outputPages
            }
        }
        val sheets = if (options.duplex == Duplex.OFF) impressions else ceilDiv(impressions, 2)
        val perCopy = if (policy.billingUnit == BillingUnit.PER_SIDE) impressions else sheets
        val billable = options.copies * perCopy
        return CostEstimate(impressions, sheets, billable, billable * CostRates.centsPerPage(options.printer.type))
    }
}

private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
