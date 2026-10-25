package com.yahyafati.poolprint.core.queue

/** One row of `lpq`. [jobId] is digits only. [owner] and [fileLabel] are display text from the server. */
data class QueueEntry(
    val rank: String,
    val owner: String,
    val jobId: String,
    val fileLabel: String,
    val sizeBytes: Long?,
)

/** The latest `lpq -P <printer>` result. [statusLine] is the first line (for example "tfppr1 is ready"). */
data class QueueSnapshot(val printerName: String, val statusLine: String?, val entries: List<QueueEntry>) {
    /** "Own" means the owner is the pool username; only these get a Cancel action. */
    fun ownEntries(username: String): List<QueueEntry> = entries.filter { it.owner == username }
}

/** A job id that passed [com.yahyafati.poolprint.core.validation.Validators.ownedJobId]; the only input `lprm` takes. */
class OwnedJobId internal constructor(val printerName: String, val value: String) {
    init {
        require(com.yahyafati.poolprint.core.validation.Patterns.JobId.matches(value)) { "invalid job id" }
    }
}
