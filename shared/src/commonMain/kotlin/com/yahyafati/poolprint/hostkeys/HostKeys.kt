package com.yahyafati.poolprint.hostkeys

import kotlinx.serialization.Serializable

/** A pinned server key: the key type plus its `SHA256:…` fingerprint (the `ssh-keygen -lf` format). */
@Serializable
data class PinnedHostKey(val type: String, val fingerprint: String)

interface HostKeyStore {
    suspend fun get(host: String, port: Int): PinnedHostKey?
    suspend fun pin(host: String, port: Int, key: PinnedHostKey)
    suspend fun forget(host: String, port: Int)
}

sealed interface HostKeyVerdict {
    data object Trusted : HostKeyVerdict
    data class Unknown(val offered: PinnedHostKey) : HostKeyVerdict
    data class Changed(val expected: PinnedHostKey, val actual: PinnedHostKey) : HostKeyVerdict
}

/** Trust on first use: accept only the pinned key; anything else blocks before authentication. */
object HostKeyPolicy {
    fun evaluate(pinned: PinnedHostKey?, offered: PinnedHostKey): HostKeyVerdict = when {
        pinned == null -> HostKeyVerdict.Unknown(offered)
        pinned == offered -> HostKeyVerdict.Trusted
        else -> HostKeyVerdict.Changed(pinned, offered)
    }
}
