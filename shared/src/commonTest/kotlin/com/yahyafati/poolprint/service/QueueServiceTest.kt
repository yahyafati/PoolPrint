package com.yahyafati.poolprint.service

import com.yahyafati.poolprint.core.discovery.Fixtures
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.fakes.FakeSshClient.Script
import com.yahyafati.poolprint.fakes.TestDeps
import com.yahyafati.poolprint.fakes.user
import com.yahyafati.poolprint.transport.SshError
import com.yahyafati.poolprint.transport.SshResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QueueServiceTest {
    private val t = TestDeps(user("jdoe"))
    private val queue get() = t.deps.queue
    private val printer = Printers.all[0]

    @Test
    fun `loads the queue and knows whose entries are own`() = runTest {
        t.client.respond(Script.Result(SshResult(0, Fixtures.lpqJobs, "")))
        val loaded = assertIs<QueueResult.Loaded>(queue.load(printer))
        assertEquals("'lpq' '-P' 'tfppr1'", t.client.calls.single().command)
        assertEquals(3, loaded.snapshot.entries.size)
        assertEquals(listOf("1234", "1236"), loaded.snapshot.ownEntries(loaded.username).map { it.jobId })
    }

    @Test
    fun `cancels an own job with lprm`() = runTest {
        t.client.respond(Script.Result(SshResult(0, Fixtures.lpqJobs, "")), Script.Result(SshResult(0, "", "")))
        queue.load(printer)
        assertEquals(CancelResult.Cancelled, queue.cancel(printer, "1234"))
        assertEquals("'lprm' '-P' 'tfppr1' '1234'", t.client.calls.last().command)
    }

    @Test
    fun `another users job is never sent to lprm`() = runTest {
        t.client.respond(Script.Result(SshResult(0, Fixtures.lpqJobs, "")))
        queue.load(printer)
        val result = assertIs<CancelResult.Rejected>(queue.cancel(printer, "1235"))
        assertEquals(ValidationError.JobNotOwned, result.error)
        assertEquals(1, t.client.calls.size) // only the lpq
    }

    @Test
    fun `nothing is cancelled before a queue was loaded, or for ids that are not in it`() = runTest {
        assertEquals(ValidationError.JobNotFound, assertIs<CancelResult.Rejected>(queue.cancel(printer, "1234")).error)
        t.client.respond(Script.Result(SshResult(0, Fixtures.lpqJobs, "")))
        queue.load(printer)
        assertEquals(ValidationError.JobNotFound, assertIs<CancelResult.Rejected>(queue.cancel(printer, "77")).error)
        assertEquals(ValidationError.JobIdInvalid, assertIs<CancelResult.Rejected>(queue.cancel(printer, "1234; id")).error)
        assertEquals(1, t.client.calls.size)
    }

    @Test
    fun `the latest queue is per printer`() = runTest {
        t.client.respond(Script.Result(SshResult(0, Fixtures.lpqJobs, "")))
        queue.load(printer)
        assertIs<CancelResult.Rejected>(queue.cancel(Printers.all[1], "1234"))
    }

    @Test
    fun `a cancelled job can't be cancelled twice from a stale list`() = runTest {
        t.client.respond(Script.Result(SshResult(0, Fixtures.lpqJobs, "")), Script.Result(SshResult(0, "", "")))
        queue.load(printer)
        queue.cancel(printer, "1234")
        assertIs<CancelResult.Rejected>(queue.cancel(printer, "1234"))
    }

    @Test
    fun `failures come back as typed results`() = runTest {
        t.client.respond(Script.Error(SshError.HostUnreachable))
        assertEquals(FailureKind.HostUnreachable, assertIs<QueueResult.Failed>(queue.load(printer)).failure.kind)
        t.client.respond(Script.Result(SshResult(1, "", "lpq: Unknown destination")))
        assertEquals(FailureKind.CommandFailed, assertIs<QueueResult.Failed>(queue.load(printer)).failure.kind)
        t.client.respond(Script.Result(SshResult(0, "what is this", "")))
        assertEquals(FailureKind.UnexpectedOutput, assertIs<QueueResult.Failed>(queue.load(printer)).failure.kind)
        t.client.respond(Script.Error(SshError.HostKeyUnknown("ssh-ed25519", "SHA256:x")))
        assertIs<QueueResult.NeedsTrust>(queue.load(printer))
        assertTrue(t.hostKeys.pins.isEmpty())
    }

    @Test
    fun `a failing lprm is reported with its stderr`() = runTest {
        t.client.respond(Script.Result(SshResult(0, Fixtures.lpqJobs, "")), Script.Result(SshResult(1, "", "lprm: Forbidden")))
        queue.load(printer)
        val failed = assertIs<CancelResult.Failed>(queue.cancel(printer, "1234"))
        assertEquals("lprm: Forbidden", failed.failure.details)
    }
}
