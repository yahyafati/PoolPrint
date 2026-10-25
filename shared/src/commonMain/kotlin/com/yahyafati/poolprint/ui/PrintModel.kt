package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.app.AppDependencies
import com.yahyafati.poolprint.core.cost.CostEstimate
import com.yahyafati.poolprint.core.cost.CostEstimator
import com.yahyafati.poolprint.core.cost.CostPolicy
import com.yahyafati.poolprint.core.discovery.DiscoverySnapshot
import com.yahyafati.poolprint.core.discovery.DiscoveredOption
import com.yahyafati.poolprint.core.discovery.DiscoveryPolicy
import com.yahyafati.poolprint.core.discovery.PrinterCapabilities
import com.yahyafati.poolprint.core.discovery.PrinterState
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
import com.yahyafati.poolprint.service.DiscoveryOutcome
import com.yahyafati.poolprint.service.PrintResult
import com.yahyafati.poolprint.service.UserFailure
import com.yahyafati.poolprint.settings.Preset
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

/** Whether the last look at the server for printers and options is running, done or failed (silently: a hint only). */
enum class DiscoveryStatus { Idle, Refreshing, Unavailable }

data class PrintState(
    val document: PdfDocumentSource,
    val options: PrintOptions = PrintOptions(),
    /** What the user typed; [options] holds the validated result. */
    val pageRangesText: String = "",
    val pageRangesError: ValidationError? = null,
    val pageCount: PageCount = PageCount.Counting,
    val policy: CostPolicy = CostPolicy(),
    val phase: PrintPhase = PrintPhase.Idle,
    val discovery: DiscoverySnapshot = DiscoverySnapshot(),
    val discoveryStatus: DiscoveryStatus = DiscoveryStatus.Idle,
    val presets: List<Preset> = emptyList(),
) {
    val printer: Printer get() = options.printer
    val copies: Int get() = options.copies

    /** The hardcoded printers plus the discovered ones. */
    val knownPrinters: List<Printer> get() = discovery.knownPrinters

    fun printerState(printer: Printer): PrinterState? = discovery.stateOf(printer)

    val capabilities: PrinterCapabilities? get() = discovery.capabilitiesFor(options.printer)

    /** The discovered options that get a generated control; each one's current value is [selectedValue]. */
    val discoveredControls: List<DiscoveredOption> get() = capabilities?.options.orEmpty().filter { it.isChoosable }

    fun selectedValue(option: DiscoveredOption): String? =
        options.discovered.firstOrNull { it.key == option.key }?.value ?: option.default

    /** True when no discovered options are known for the printer: the sheet says it uses the default settings. */
    val usingDefaults: Boolean get() = capabilities == null && discoveryStatus != DiscoveryStatus.Refreshing

    /** The preset whose settings are exactly the current ones (shown as selected), if any. */
    val activePreset: Preset? get() = presets.firstOrNull { it.matches(options) }

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

    private var discoveryJob: Job? = null

    init {
        scope.launch {
            val pages = deps.pageCounter.count(document)
            mutableState.update { revalidated(it.copy(pageCount = if (pages != null) PageCount.Known(pages) else PageCount.Unknown)) }
        }
        // Local reads only: the cached printers and options, and the presets. Nothing touches the network here.
        scope.launch {
            val cached = deps.discovery.cached()
            val presets = runCatching { deps.presets.list() }.getOrDefault(emptyList())
            mutableState.update { it.copy(discovery = cached, presets = presets) }
        }
    }

    fun selectPrinter(name: String) {
        val printer = Validators.printer(name, mutableState.value.knownPrinters).valueOrNull() ?: return
        if (printer == mutableState.value.printer) return
        // Discovered options belong to one printer: the choices don't carry over.
        change { it.copy(printer = printer, discovered = emptyList()) }
    }

    /**
     * Looks at the server for printers and for the options of the selected printer, when the cache is missing
     * or stale (or [force]). Failure is silent: the cache, or the defaults, stay in use. Called by the print
     * screen when it opens and when the printer changes.
     */
    fun refreshDiscovery(force: Boolean = false) {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            mutableState.update { it.copy(discoveryStatus = DiscoveryStatus.Refreshing) }
            var failed = false
            val now = deps.discovery.now()
            if (force || DiscoveryPolicy.isStale(mutableState.value.discovery.printersFetchedAtMillis, now)) {
                failed = !apply(deps.discovery.refreshPrinters())
            }
            val printer = mutableState.value.printer
            val fetched = mutableState.value.discovery.capabilitiesFor(printer)?.fetchedAtMillis
            if (force || DiscoveryPolicy.isStale(fetched, now)) {
                failed = !apply(deps.discovery.refreshOptions(printer)) || failed
            }
            mutableState.update { it.copy(discoveryStatus = if (failed) DiscoveryStatus.Unavailable else DiscoveryStatus.Idle) }
        }
    }

    private fun apply(outcome: DiscoveryOutcome): Boolean {
        if (outcome !is DiscoveryOutcome.Updated) return false
        mutableState.update { s ->
            val withCache = s.copy(discovery = outcome.snapshot)
            // A choice that is no longer in the refreshed options can't stay selected.
            withCache.copy(options = withCache.options.copy(discovered = withCache.stillValid(withCache.options.discovered)))
        }
        return true
    }

    /** Sets a discovered option. Only values from the cached options of this printer are accepted; the default is "not set". */
    fun setDiscovered(key: String, value: String) {
        val capabilities = mutableState.value.capabilities ?: return
        val setting = Validators.discoveredSetting(capabilities, key, value).valueOrNull() ?: return
        val default = capabilities.option(key)?.default
        change { options ->
            val others = options.discovered.filter { it.key != key }
            options.copy(discovered = (if (value == default) others else others + setting).sortedBy { it.key })
        }
    }

    /**
     * Applies a preset: its printer, layout options and discovered choices. Copies and page ranges stay as
     * they are. Discovered choices that the cached options of the preset's printer don't contain are dropped.
     */
    fun applyPreset(id: Long) {
        mutableState.update { s ->
            val preset = s.presets.firstOrNull { it.id == id } ?: return@update s
            val base = preset.settings.toOptions(s.knownPrinters).copy(copies = s.options.copies, pageRanges = s.options.pageRanges)
            val capabilities = s.discovery.capabilitiesFor(base.printer)
            val discovered = capabilities?.let { caps ->
                preset.discovered.mapNotNull { (key, value) -> Validators.discoveredSetting(caps, key, value).valueOrNull() }
                    .filter { it.value != caps.option(it.key)?.default }
            }.orEmpty().sortedBy { it.key }
            revalidated(s.copy(options = base.copy(discovered = discovered)))
        }
    }

    /** Saves the current printer and options as a preset. Returns the validation error, or null when it was saved. */
    suspend fun savePreset(name: String): ValidationError? {
        val result = deps.presets.create(name, mutableState.value.options)
        if (result is Validated.Invalid) return result.error
        mutableState.update { it.copy(presets = runCatching { deps.presets.list() }.getOrDefault(it.presets)) }
        return null
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

    private fun PrintState.stillValid(settings: List<com.yahyafati.poolprint.core.model.DiscoveredSetting>) =
        capabilities?.let { caps -> settings.filter { Validators.discoveredSetting(caps, it.key, it.value) is Validated.Valid } }.orEmpty()

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
