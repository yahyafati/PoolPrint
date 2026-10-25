package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.app.AppDependencies
import com.yahyafati.poolprint.core.model.PrintJob
import com.yahyafati.poolprint.core.model.PrintLimits
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.validation.Validators
import com.yahyafati.poolprint.core.validation.valueOrNull
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.pdf.PdfDocumentSource
import com.yahyafati.poolprint.service.PrintResult
import com.yahyafati.poolprint.service.UserFailure
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PrintPhase {
    data object Idle : PrintPhase
    data class Uploading(val sentBytes: Long, val totalBytes: Long?) : PrintPhase
    data class Sent(val printerName: String, val jobId: String?) : PrintPhase
    data class AskTrust(val key: PinnedHostKey) : PrintPhase
    data class Failed(val failure: UserFailure) : PrintPhase
}

data class PrintState(
    val document: PdfDocumentSource,
    val printer: Printer = Printers.default,
    val copies: Int = 1,
    val phase: PrintPhase = PrintPhase.Idle,
) {
    val canPrint: Boolean get() = phase is PrintPhase.Idle || phase is PrintPhase.Failed
}

class PrintModel(
    private val deps: AppDependencies,
    document: PdfDocumentSource,
    private val scope: CoroutineScope,
) {
    private val document = document
    private val mutableState = MutableStateFlow(PrintState(document))
    val state: StateFlow<PrintState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun selectPrinter(name: String) {
        val printer = Validators.printer(name).valueOrNull() ?: return
        mutableState.update { it.copy(printer = printer) }
    }

    fun setCopies(copies: Int) {
        val valid = Validators.copies(copies).valueOrNull() ?: return
        mutableState.update { it.copy(copies = valid) }
    }

    fun incrementCopies() = setCopies(mutableState.value.copies + 1)
    fun decrementCopies() = setCopies(mutableState.value.copies - 1)
    val maxCopies: Int get() = PrintLimits.MAX_COPIES

    fun print() {
        val current = mutableState.value
        if (!current.canPrint || job?.isActive == true) return
        val printJob = PrintJob(PrintOptions(current.printer, current.copies), document.displayName)
        mutableState.update { it.copy(phase = PrintPhase.Uploading(0, document.sizeBytes)) }
        job = scope.launch {
            try {
                val result = deps.services.print(printJob, document.open()) { sent ->
                    mutableState.update { s ->
                        if (s.phase is PrintPhase.Uploading) s.copy(phase = PrintPhase.Uploading(sent, document.sizeBytes)) else s
                    }
                }
                mutableState.update {
                    it.copy(
                        phase = when (result) {
                            is PrintResult.Sent -> PrintPhase.Sent(result.printerName, result.jobId)
                            is PrintResult.NeedsTrust -> PrintPhase.AskTrust(result.key)
                            is PrintResult.Failed -> PrintPhase.Failed(result.failure)
                        },
                    )
                }
            } catch (e: CancellationException) {
                mutableState.update { it.copy(phase = PrintPhase.Idle) }
                throw e
            } catch (e: Exception) {
                // Reading the document failed (for example the share permission expired).
                mutableState.update { it.copy(phase = PrintPhase.Failed(UserFailure(com.yahyafati.poolprint.service.FailureKind.PrintFailed, details = e.message))) }
            }
        }
    }

    /** Cancel during upload: aborts the connection, so `lpr` never gets a complete job. */
    fun cancel() {
        job?.cancel()
    }

    fun trustAndRetry() {
        val ask = mutableState.value.phase as? PrintPhase.AskTrust ?: return
        scope.launch {
            deps.services.trust(ask.key)
            mutableState.update { it.copy(phase = PrintPhase.Idle) }
            print()
        }
    }

    fun declineHostKey() = mutableState.update { it.copy(phase = PrintPhase.Idle) }
    fun dismissFailure() = mutableState.update { it.copy(phase = PrintPhase.Idle) }
}
