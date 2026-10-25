package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.app.AppDependencies
import com.yahyafati.poolprint.core.cost.CostEstimate
import com.yahyafati.poolprint.core.cost.CostEstimator
import com.yahyafati.poolprint.core.cost.CostPolicy
import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.Orientation
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.model.PaperSize
import com.yahyafati.poolprint.core.model.PrintJob
import com.yahyafati.poolprint.core.model.PrintLimits
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.core.validation.Validated
import com.yahyafati.poolprint.core.validation.ValidationError
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

/** The document's page count, counted on the device. */
sealed interface PageCount {
    data object Counting : PageCount
    data class Known(val pages: Int) : PageCount

    /** The file couldn't be read (corrupt, encrypted): printing is still possible, the cost is unknown. */
    data object Unknown : PageCount
}

data class PrintState(
    val document: PdfDocumentSource,
    val options: PrintOptions = PrintOptions(),
    /** What the user typed; [options] holds the validated result. */
    val pageRangesText: String = "",
    val pageRangesError: ValidationError? = null,
    val pageCount: PageCount = PageCount.Counting,
    val policy: CostPolicy = CostPolicy(),
    val phase: PrintPhase = PrintPhase.Idle,
) {
    val printer: Printer get() = options.printer
    val copies: Int get() = options.copies

    /** Null while counting, when the count is unknown, or when the page ranges are invalid. */
    val estimate: CostEstimate? get() {
        val known = pageCount as? PageCount.Known ?: return null
        if (pageRangesError != null) return null
        return CostEstimator.estimate(options, known.pages, policy)
    }

    val canPrint: Boolean get() = (phase is PrintPhase.Idle || phase is PrintPhase.Failed) && pageRangesError == null
}

class PrintModel(
    private val deps: AppDependencies,
    document: PdfDocumentSource,
    private val scope: CoroutineScope,
    initial: PrintOptions = PrintOptions(),
    policy: CostPolicy = CostPolicy(),
) {
    private val document = document
    private val mutableState = MutableStateFlow(PrintState(document, initial, policy = policy))
    val state: StateFlow<PrintState> = mutableState.asStateFlow()
    private var job: Job? = null

    init {
        scope.launch {
            val pages = deps.pageCounter.count(document)
            mutableState.update { revalidated(it.copy(pageCount = if (pages != null) PageCount.Known(pages) else PageCount.Unknown)) }
        }
    }

    fun selectPrinter(name: String) {
        val printer = Validators.printer(name).valueOrNull() ?: return
        change { it.copy(printer = printer) }
    }

    fun setCopies(copies: Int) {
        val valid = Validators.copies(copies).valueOrNull() ?: return
        change { it.copy(copies = valid) }
    }

    fun incrementCopies() = setCopies(mutableState.value.copies + 1)
    fun decrementCopies() = setCopies(mutableState.value.copies - 1)
    val maxCopies: Int get() = PrintLimits.MAX_COPIES

    fun setPageRanges(text: String) =
        mutableState.update { revalidated(it.copy(pageRangesText = text)) }

    fun setDuplex(duplex: Duplex) = change { it.copy(duplex = duplex) }
    fun setPagesPerSheet(pagesPerSheet: PagesPerSheet) = change { it.copy(pagesPerSheet = pagesPerSheet) }
    fun setOrientation(orientation: Orientation) = change { it.copy(orientation = orientation) }
    fun setFitToPage(fitToPage: Boolean) = change { it.copy(fitToPage = fitToPage) }
    fun setPaperSize(paperSize: PaperSize) = change { it.copy(paperSize = paperSize) }

    private fun change(transform: (PrintOptions) -> PrintOptions) =
        mutableState.update { revalidated(it.copy(options = transform(it.options))) }

    /**
     * Re-checks the typed page ranges against the current layout and page count (ranges can become invalid
     * when pages per sheet or the document changes). Invalid ranges are not sent: [PrintState.canPrint] is false.
     */
    private fun revalidated(state: PrintState): PrintState {
        val lastPage = (state.pageCount as? PageCount.Known)?.let {
            state.policy.lastSelectablePage(it.pages, state.options.pagesPerSheet.count)
        }
        return when (val result = Validators.pageRanges(state.pageRangesText, lastPage)) {
            is Validated.Valid -> state.copy(options = state.options.copy(pageRanges = result.value), pageRangesError = null)
            is Validated.Invalid -> state.copy(options = state.options.copy(pageRanges = null), pageRangesError = result.error)
        }
    }

    fun print() {
        val current = mutableState.value
        if (!current.canPrint || job?.isActive == true) return
        val printJob = PrintJob(current.options, document.displayName)
        scope.launch { runCatching { deps.settings.setPrintDefaults(current.options) } }
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
