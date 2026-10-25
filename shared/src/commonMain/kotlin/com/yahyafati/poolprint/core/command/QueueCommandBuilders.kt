package com.yahyafati.poolprint.core.command

import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.core.queue.OwnedJobId

/** `lpstat -p`: the printers the server knows. */
object LpstatCommandBuilder {
    fun build(): RemoteCommand = RemoteCommand.fromTokens(listOf("lpstat", "-p"))
}

/** `lpoptions -p <printer> -l`: the options of one printer, as a [Printer] from the known list. */
object LpoptionsCommandBuilder {
    fun build(printer: Printer): RemoteCommand = RemoteCommand.fromTokens(listOf("lpoptions", "-p", printer.name, "-l"))
}

/** `lpq -P <printer>`. */
object LpqCommandBuilder {
    fun build(printer: Printer): RemoteCommand = RemoteCommand.fromTokens(listOf("lpq", "-P", printer.name))
}

/** `lprm -P <printer> <jobId>`. The job id is an [OwnedJobId]: validated, present in the latest `lpq`, owned by the user. */
object LprmCommandBuilder {
    fun build(printer: Printer, job: OwnedJobId): RemoteCommand {
        require(job.printerName == printer.name) { "job belongs to another printer" }
        return RemoteCommand.fromTokens(listOf("lprm", "-P", printer.name, job.value))
    }
}
