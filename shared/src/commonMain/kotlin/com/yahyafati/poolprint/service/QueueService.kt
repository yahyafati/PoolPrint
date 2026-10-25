package com.yahyafati.poolprint.service

import com.yahyafati.poolprint.core.command.LpqCommandBuilder
import com.yahyafati.poolprint.core.command.LprmCommandBuilder
import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.core.queue.LpqParser
import com.yahyafati.poolprint.core.queue.QueueSnapshot
import com.yahyafati.poolprint.core.validation.Validated
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.core.validation.Validators
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.settings.AppSettings

sealed interface QueueResult {
    /** [username] is whose entries count as "own". */
    data class Loaded(val snapshot: QueueSnapshot, val username: String) : QueueResult

    data class NeedsTrust(val key: PinnedHostKey) : QueueResult

    data class Failed(val failure: UserFailure) : QueueResult
}

sealed interface CancelResult {
    data object Cancelled : CancelResult

    /** The job id didn't pass validation (not digits, not in the latest queue, or not yours): nothing was sent. */
    data class Rejected(val error: ValidationError) : CancelResult

    data class NeedsTrust(val key: PinnedHostKey) : CancelResult

    data class Failed(val failure: UserFailure) : CancelResult
}

/**
 * `lpq` and `lprm`. A job can only be cancelled when it is in the **latest** `lpq` result this service
 * fetched for the printer and its owner is the pool username (docs/print-options.md, "Validation rules").
 */
class QueueService(private val services: PoolServices, private val settings: AppSettings) {
    private val latest = mutableMapOf<String, QueueSnapshot>()

    suspend fun load(printer: Printer): QueueResult {
        val username = settings.getUsername() ?: return QueueResult.Failed(UserFailure(FailureKind.NotConfigured))
        return when (val outcome = services.run(LpqCommandBuilder.build(printer))) {
            is RemoteOutcome.NeedsTrust -> QueueResult.NeedsTrust(outcome.key)
            is RemoteOutcome.Failed -> QueueResult.Failed(outcome.failure)
            is RemoteOutcome.Done -> {
                val result = outcome.result
                if (result.exitCode != 0) {
                    QueueResult.Failed(
                        UserFailure(FailureKind.CommandFailed, exitCode = result.exitCode, printerName = printer.name, details = result.stderr.trim().ifEmpty { null }),
                    )
                } else {
                    val snapshot = LpqParser.parse(printer.name, result.stdout)
                    if (snapshot == null) {
                        QueueResult.Failed(UserFailure(FailureKind.UnexpectedOutput, printerName = printer.name, details = result.stdout.take(200)))
                    } else {
                        latest[printer.name] = snapshot
                        QueueResult.Loaded(snapshot, username.value)
                    }
                }
            }
        }
    }

    suspend fun cancel(printer: Printer, jobId: String): CancelResult {
        val username = settings.getUsername() ?: return CancelResult.Failed(UserFailure(FailureKind.NotConfigured))
        val queue = latest[printer.name] ?: return CancelResult.Rejected(ValidationError.JobNotFound)
        val job = when (val valid = Validators.ownedJobId(jobId, queue, username)) {
            is Validated.Valid -> valid.value
            is Validated.Invalid -> return CancelResult.Rejected(valid.error)
        }
        return when (val outcome = services.run(LprmCommandBuilder.build(printer, job))) {
            is RemoteOutcome.NeedsTrust -> CancelResult.NeedsTrust(outcome.key)
            is RemoteOutcome.Failed -> CancelResult.Failed(outcome.failure)
            is RemoteOutcome.Done -> {
                val result = outcome.result
                if (result.exitCode == 0) {
                    latest.remove(printer.name)
                    CancelResult.Cancelled
                } else {
                    CancelResult.Failed(
                        UserFailure(FailureKind.CommandFailed, exitCode = result.exitCode, printerName = printer.name, details = result.stderr.trim().ifEmpty { null }),
                    )
                }
            }
        }
    }
}
