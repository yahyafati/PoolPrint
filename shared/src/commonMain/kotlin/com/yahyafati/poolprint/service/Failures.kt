package com.yahyafati.poolprint.service

import com.yahyafati.poolprint.transport.SshError

/** What the user sees for a failure. Each kind maps to a string in Compose resources (docs/ux.md catalogue). */
enum class FailureKind {
    NotConfigured,
    HostUnreachable,
    AuthRejected,

    /** The one-time key install (D-20): the server refused the pool password. */
    PasswordRejected,
    HostKeyChanged,
    Timeout,
    UnknownPrinter,
    QuotaExceeded,
    LprMissing,
    PrintFailed,
    CommandFailed,
    UnexpectedOutput,
    Protocol,
}

/**
 * [details] is the raw stderr or protocol message, shown behind a "Details" toggle.
 * [expectedFingerprint]/[actualFingerprint] are set for [FailureKind.HostKeyChanged].
 */
data class UserFailure(
    val kind: FailureKind,
    val exitCode: Int? = null,
    val printerName: String? = null,
    val details: String? = null,
    val expectedFingerprint: String? = null,
    val actualFingerprint: String? = null,
)

object ErrorCatalogue {
    /** [SshError.HostKeyUnknown] is not an error screen: callers handle it as a trust prompt first. */
    fun fromTransport(error: SshError): UserFailure = when (error) {
        SshError.HostUnreachable -> UserFailure(FailureKind.HostUnreachable)
        SshError.AuthRejected -> UserFailure(FailureKind.AuthRejected)
        SshError.Timeout -> UserFailure(FailureKind.Timeout)
        is SshError.HostKeyChanged -> UserFailure(
            FailureKind.HostKeyChanged,
            expectedFingerprint = error.expected,
            actualFingerprint = error.actual,
        )
        is SshError.HostKeyUnknown -> UserFailure(FailureKind.Protocol, details = "unexpected unknown host key")
        is SshError.Protocol -> UserFailure(FailureKind.Protocol, details = error.message)
    }

    /** Best-effort stderr matching; unknown output falls through to [FailureKind.PrintFailed]. */
    fun fromPrintExit(exitCode: Int, stderr: String, printerName: String): UserFailure {
        val text = stderr.lowercase()
        val kind = when {
            exitCode == 127 || "command not found" in text || "lpr: not found" in text -> FailureKind.LprMissing
            "does not exist" in text || "unknown printer" in text || "no such printer" in text ||
                "unknown destination" in text -> FailureKind.UnknownPrinter
            "quota" in text || "budget" in text -> FailureKind.QuotaExceeded
            else -> FailureKind.PrintFailed
        }
        return UserFailure(kind, exitCode = exitCode, printerName = printerName, details = stderr.trim().ifEmpty { null })
    }
}
