package com.yahyafati.poolprint.core.command

import com.yahyafati.poolprint.core.model.PrintJob

/**
 * `lpr -P <printer> [-# <copies>]`. The document comes in on stdin: no filename, no job title, no file
 * argument. Takes typed values only; [PrintJob.documentName] is deliberately never read.
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
        }
        return RemoteCommand.fromTokens(tokens)
    }
}
