package com.yahyafati.poolprint.service

import com.yahyafati.poolprint.fakes.FakeSshClient.Script
import com.yahyafati.poolprint.fakes.TestDeps
import com.yahyafati.poolprint.fakes.user
import com.yahyafati.poolprint.transport.SshError
import com.yahyafati.poolprint.transport.SshResult
import com.yahyafati.poolprint.ui.AddDevice
import com.yahyafati.poolprint.ui.SettingsModel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The key blob is synthetic: the right shape, not a real key. */
class AuthorizeDeviceTest {
    private val t = TestDeps(user())
    private val key = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA${"A".repeat(44)} pool-print@Pixel"
    private fun TestScope.model() = SettingsModel(t.deps, this).also { advanceUntilIdle() }

    @Test
    fun `authorizing sends the fixed command and the key on stdin`() = runTest {
        val m = model()
        m.openAddDevice(); m.onAddDeviceInput("  $key\n"); m.authorizeDevice(); advanceUntilIdle()

        val call = t.client.calls.single()
        assertTrue(call.command.startsWith("umask 077; mkdir -p ~/.ssh;"))
        assertTrue(key !in call.command)
        assertEquals("\n$key\n", call.stdin!!.decodeToString())
        assertEquals(AddDevice.Added("pool-print@Pixel"), m.state.value.addDevice)
    }

    @Test
    fun `an invalid key is rejected without connecting`() = runTest {
        val m = model()
        m.openAddDevice(); m.onAddDeviceInput("ssh-ed25519 AAAA; id"); m.authorizeDevice(); advanceUntilIdle()

        assertTrue(t.client.calls.isEmpty())
        assertEquals(AddDevice.Form("ssh-ed25519 AAAA; id", invalid = true), m.state.value.addDevice)
    }

    @Test
    fun `a non-zero exit is reported with stderr`() = runTest {
        t.client.respond(Script.Result(SshResult(1, "", "Disk quota exceeded\n")))
        val m = model()
        m.openAddDevice(); m.onAddDeviceInput(key); m.authorizeDevice(); advanceUntilIdle()

        val failed = assertIs<AddDevice.Failed>(m.state.value.addDevice)
        assertEquals(FailureKind.CommandFailed, failed.failure.kind)
        assertEquals(1, failed.failure.exitCode)
        assertEquals("Disk quota exceeded", failed.failure.details)
    }

    @Test
    fun `a rejected login is reported as AuthRejected`() = runTest {
        t.client.respond(Script.Error(SshError.AuthRejected))
        val m = model()
        m.openAddDevice(); m.onAddDeviceInput(key); m.authorizeDevice(); advanceUntilIdle()

        assertEquals(FailureKind.AuthRejected, assertIs<AddDevice.Failed>(m.state.value.addDevice).failure.kind)
    }

    @Test
    fun `authorizing does nothing when the form is closed`() = runTest {
        val m = model()
        m.authorizeDevice(); advanceUntilIdle()
        assertTrue(t.client.calls.isEmpty())
        assertEquals(AddDevice.Closed, m.state.value.addDevice)
    }
}
