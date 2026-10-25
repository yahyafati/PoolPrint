package com.yahyafati.poolprint.core.command

import com.yahyafati.poolprint.core.model.Orientation
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.model.PrintJob

/**
 * `lpr -P <printer> [-# <copies>] -o sides=<…> [-o number-up=<n>] [-o page-ranges=<r>] [-o landscape]
 * [-o fit-to-page] -o media=<size>`, in this fixed order (docs/print-options.md). The document comes in on
 * stdin: no filename, no job title, no file argument. Takes typed values only; every string comes from an
 * enum's mapping table or from [com.yahyafati.poolprint.core.model.PageRanges.canonical];
 * [PrintJob.documentName] is deliberately never read.
 */
object LprCommandBuilder {
    fun build(job: PrintJob): RemoteCommand {
        val options = job.options
        val tokens = buildList {
            add("lpr")
            add("-P")
            add(options.printer.name)
            if (options.copies > 1) {
                add("-#")
                add(options.copies.toString())
            }
            option("sides=${options.duplex.cupsValue}")
            if (options.pagesPerSheet != PagesPerSheet.ONE) option("number-up=${options.pagesPerSheet.count}")
            options.pageRanges?.let { option("page-ranges=${it.canonical}") }
            if (options.orientation == Orientation.LANDSCAPE) option("landscape")
            if (options.fitToPage) option("fit-to-page")
            option("media=${options.paperSize.cupsValue}")
        }
        return RemoteCommand.fromTokens(tokens)
    }

    private fun MutableList<String>.option(value: String) {
        add("-o")
        add(value)
    }
}
