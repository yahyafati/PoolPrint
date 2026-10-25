package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.core.discovery.Fixtures
import com.yahyafati.poolprint.fakes.FakeSshClient.Script
import com.yahyafati.poolprint.fakes.TestDeps
import com.yahyafati.poolprint.fakes.user
import com.yahyafati.poolprint.service.FailureKind
import com.yahyafati.poolprint.transport.SshError
import com.yahyafati.poolprint.transport.SshResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueModelTest {
    private val t = TestDeps(user("jdoe"))
    private fun TestScope.model() = QueueModel(t.deps, this)
    private val jobs = Script.Result(SshResult(0, Fixtures.lpqJobs, ""))
    private val ok = Script.Result(SshResult(0, "", ""))

    @Test
    fun `shows the queue with own jobs marked`() = runTest {
        t.client.respond(jobs)
        val m = model(); m.refresh(); advanceUntilIdle()
        val state = m.state.value
        assertEquals(listOf("1234", "1235", "1236"), state.queue?.entries?.map { it.jobId })
        assertEquals(listOf(true, false, true), state.queue!!.entries.map { state.isOwn(it) })
        assertEquals("'lpq' '-P' 'tfppr1'", t.client.calls.single().command)
    }

    @Test
    fun `cancel asks first and sends lprm only after confirmation`() = runTest {
        t.client.respond(jobs, ok, Script.Result(SshResult(0, Fixtures.lpqEmpty, "")))
        val m = model(); m.refresh(); advanceUntilIdle()
        val entry = m.state.value.queue!!.entries.first()
        m.askCancel(entry)
        assertEquals(entry, m.state.value.confirming)
        assertEquals(1, t.client.calls.size)
        m.confirmCancel(); advanceUntilIdle()
        assertEquals("'lprm' '-P' 'tfppr1' '1234'", t.client.calls[1].command)
        assertEquals(CancelNotice.Cancelled, m.state.value.notice)
        assertEquals(emptyList(), m.state.value.queue?.entries)
        assertEquals(3, t.client.calls.size) // refreshed afterwards
    }

    @Test
    fun `dismissing the confirmation sends nothing`() = runTest {
        t.client.respond(jobs)
        val m = model(); m.refresh(); advanceUntilIdle()
        m.askCancel(m.state.value.queue!!.entries.first())
        m.dismissCancel(); m.confirmCancel(); advanceUntilIdle()
        assertEquals(1, t.client.calls.size)
        assertNull(m.state.value.confirming)
    }

    @Test
    fun `other users jobs can't be selected for cancelling`() = runTest {
        t.client.respond(jobs)
        val m = model(); m.refresh(); advanceUntilIdle()
        m.askCancel(m.state.value.queue!!.entries[1])
        assertNull(m.state.value.confirming)
        m.confirmCancel(); advanceUntilIdle()
        assertEquals(1, t.client.calls.size)
    }

    @Test
    fun `a failure is shown and a retry works`() = runTest {
        t.client.respond(Script.Error(SshError.HostUnreachable), jobs)
        val m = model(); m.refresh(); advanceUntilIdle()
        assertEquals(FailureKind.HostUnreachable, m.state.value.failure?.kind)
        assertNull(m.state.value.queue)
        m.refresh(); advanceUntilIdle()
        assertNull(m.state.value.failure)
        assertNotNull(m.state.value.queue)
    }

    @Test
    fun `switching printer loads that printers queue`() = runTest {
        t.client.respond(jobs)
        val m = model(); advanceUntilIdle()
        m.selectPrinter("tfppr3"); advanceUntilIdle()
        assertEquals("'lpq' '-P' 'tfppr3'", t.client.calls.single().command)
        m.selectPrinter("evil;id")
        assertEquals("tfppr3", m.state.value.printer.name)
    }

    @Test
    fun `an unknown host key asks for trust and retries after`() = runTest {
        t.client.respond(Script.Error(SshError.HostKeyUnknown("ssh-ed25519", "SHA256:abc")), jobs)
        val m = model(); m.refresh(); advanceUntilIdle()
        assertNotNull(m.state.value.askTrust)
        assertTrue(t.hostKeys.pins.isEmpty())
        m.trustAndRetry(); advanceUntilIdle()
        assertEquals(1, t.hostKeys.pins.size)
        assertNotNull(m.state.value.queue)
    }
}
