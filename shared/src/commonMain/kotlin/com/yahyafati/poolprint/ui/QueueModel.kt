package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.app.AppDependencies
import com.yahyafati.poolprint.core.discovery.DiscoverySnapshot
import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.queue.QueueEntry
import com.yahyafati.poolprint.core.queue.QueueSnapshot
import com.yahyafati.poolprint.core.validation.Validators
import com.yahyafati.poolprint.core.validation.valueOrNull
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.service.CancelResult
import com.yahyafati.poolprint.service.QueueResult
import com.yahyafati.poolprint.service.UserFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the last Cancel did, for a one-line message under the list. */
enum class CancelNotice { Cancelled, NotAllowed }

data class QueueState(
    val printers: List<Printer> = Printers.all,
    val printer: Printer = Printers.default,
    val loading: Boolean = false,
    val queue: QueueSnapshot? = null,
    /** Whose entries are "own". */
    val username: String? = null,
    val failure: UserFailure? = null,
    val askTrust: PinnedHostKey? = null,
    /** The entry the user is about to cancel (a confirmation dialog is open). */
    val confirming: QueueEntry? = null,
    val cancelling: Boolean = false,
    val notice: CancelNotice? = null,
) {
    fun isOwn(entry: QueueEntry): Boolean = username != null && entry.owner == username
}

/** The queue screen: `lpq` for a printer, and Cancel (after a confirmation) for the user's own jobs only. */
class QueueModel(private val deps: AppDependencies, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = mutableState.asStateFlow()
    private var job: Job? = null

    init {
        scope.launch {
            val discovery: DiscoverySnapshot = deps.discovery.cached()
            val known = discovery.knownPrinters
            val last = deps.settings.getPrintDefaults(known).printer
            mutableState.update { it.copy(printers = known, printer = last) }
        }
    }

    fun selectPrinter(name: String) {
        val printer = Validators.printer(name, mutableState.value.printers).valueOrNull() ?: return
        mutableState.update { it.copy(printer = printer, queue = null, failure = null, notice = null, confirming = null) }
        refresh()
    }

    /** Runs `lpq` for the selected printer. Called when the screen opens and by the refresh button. */
    fun refresh() {
        job?.cancel()
        mutableState.update { it.copy(loading = true, failure = null, askTrust = null) }
        val printer = mutableState.value.printer
        job = scope.launch {
            val result = deps.queue.load(printer)
            mutableState.update { s ->
                if (s.printer != printer) return@update s
                when (result) {
                    is QueueResult.Loaded -> s.copy(loading = false, queue = result.snapshot, username = result.username)
                    is QueueResult.NeedsTrust -> s.copy(loading = false, askTrust = result.key)
                    is QueueResult.Failed -> s.copy(loading = false, queue = null, failure = result.failure)
                }
            }
        }
    }

    fun askCancel(entry: QueueEntry) {
        if (mutableState.value.isOwn(entry)) mutableState.update { it.copy(confirming = entry) }
    }

    fun dismissCancel() = mutableState.update { it.copy(confirming = null) }

    /** Sends `lprm` for the confirmed entry (the service checks again that it is in the latest queue and yours), then refreshes. */
    fun confirmCancel() {
        val entry = mutableState.value.confirming ?: return
        val printer = mutableState.value.printer
        mutableState.update { it.copy(confirming = null, cancelling = true, notice = null) }
        scope.launch {
            val result = deps.queue.cancel(printer, entry.jobId)
            mutableState.update {
                when (result) {
                    CancelResult.Cancelled -> it.copy(cancelling = false, notice = CancelNotice.Cancelled)
                    is CancelResult.Rejected -> it.copy(cancelling = false, notice = CancelNotice.NotAllowed)
                    is CancelResult.NeedsTrust -> it.copy(cancelling = false, askTrust = result.key)
                    is CancelResult.Failed -> it.copy(cancelling = false, failure = result.failure)
                }
            }
            if (result == CancelResult.Cancelled || result is CancelResult.Rejected) refresh()
        }
    }

    fun trustAndRetry() {
        val key = mutableState.value.askTrust ?: return
        scope.launch {
            deps.services.trust(key)
            mutableState.update { it.copy(askTrust = null) }
            refresh()
        }
    }

    fun declineTrust() = mutableState.update { it.copy(askTrust = null) }

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    fun dismissNotice() = mutableState.update { it.copy(notice = null) }
}
