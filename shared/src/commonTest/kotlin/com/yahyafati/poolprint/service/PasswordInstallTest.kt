package com.yahyafati.poolprint.service

import com.yahyafati.poolprint.core.command.AuthorizeKeyCommand
import com.yahyafati.poolprint.fakes.FakeSshClient.Script
import com.yahyafati.poolprint.fakes.TestDeps
import com.yahyafati.poolprint.fakes.user
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.transport.PoolServer
import com.yahyafati.poolprint.transport.SshError
import com.yahyafati.poolprint.transport.SshResult
import com.yahyafati.poolprint.ui.ConnectState
import com.yahyafati.poolprint.ui.OnboardingModel
import com.yahyafati.poolprint.ui.OnboardingStep
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one-time password key install (D-20) through [PoolServices] and [OnboardingModel]. The key blob is synthetic. */
class PasswordInstallTest {
    private val t = TestDeps(user())
    private val publicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA${"A".repeat(44)} pool-print@test"
    private val hostKey = PinnedHostKey("ssh-ed25519", "SHA256:server")
    private val ok = Script.Result(SshResult(0, "", ""))
    private val echoOk = Script.Result(SshResult(0, "ok\n", ""))

    private fun zeroed(chars: CharArray) = chars.all { it == '\u0000' }

    // --- PoolServices ---

    @Test
    fun `installs the stored public key with the fixed command and zeroes the password`() = runTest {
        t.settings.setPublicKey(publicKey)
        val password = "p4ss; rm -rf ~ 'quoted'".toCharArray()
        assertEquals(InstallKeyResult.Installed, t.services.installKeyWithPassword(password))

        val call = t.client.calls.single()
        assertEquals(AuthorizeKeyCommand.build().value, call.command)
        assertEquals("\n$publicKey\n", call.stdin!!.decodeToString())
        assertEquals("p4ss; rm -rf ~ 'quoted'", call.password)
        assertTrue("p4ss" !in call.command && "p4ss" !in call.stdin.decodeToString())
        assertTrue(zeroed(password))
    }

    @Test
    fun `a rejected password is PasswordRejected, not the key error`() = runTest {
        t.settings.setPublicKey(publicKey)
        t.client.respond(Script.Error(SshError.AuthRejected))
        val result = assertIs<InstallKeyResult.Failed>(t.services.installKeyWithPassword("x".toCharArray()))
        assertEquals(FailureKind.PasswordRejected, result.failure.kind)
    }

    @Test
    fun `an unknown host key asks for trust and a non-zero exit reports stderr`() = runTest {
        t.settings.setPublicKey(publicKey)
        t.client.respond(Script.Error(SshError.HostKeyUnknown(hostKey.type, hostKey.fingerprint)), Script.Result(SshResult(1, "", "Disk quota exceeded\n")))
        assertEquals(InstallKeyResult.NeedsTrust(hostKey), t.services.installKeyWithPassword("x".toCharArray()))
        val failed = assertIs<InstallKeyResult.Failed>(t.services.installKeyWithPassword("x".toCharArray()))
        assertEquals(UserFailure(FailureKind.CommandFailed, exitCode = 1, details = "Disk quota exceeded"), failed.failure)
    }

    @Test
    fun `without a username or a valid public key nothing connects, and the password is still zeroed`() = runTest {
        val none = TestDeps()
        val password = "secret".toCharArray()
        assertEquals(FailureKind.NotConfigured, assertIs<InstallKeyResult.Failed>(none.services.installKeyWithPassword(password)).failure.kind)
        assertTrue(zeroed(password))

        t.settings.setPublicKey("ssh-ed25519 AAAA; id")
        val other = "secret".toCharArray()
        assertEquals(FailureKind.NotConfigured, assertIs<InstallKeyResult.Failed>(t.services.installKeyWithPassword(other)).failure.kind)
        assertTrue(zeroed(other))
        assertTrue(none.client.calls.isEmpty() && t.client.calls.isEmpty())
    }

    // --- OnboardingModel ---

    /** With a saved username the model starts at the key step; the fake generator's key isn't valid, so swap it. */
    private suspend fun TestScope.passwordStep(): OnboardingModel {
        val m = OnboardingModel(t.deps, this)
        advanceUntilIdle()
        t.settings.setPublicKey(publicKey)
        m.usePassword()
        assertEquals(OnboardingStep.PasswordSetup, m.state.value.step)
        return m
    }

    @Test
    fun `a successful install moves on and tests the key login`() = runTest {
        t.client.respond(ok, echoOk)
        val m = passwordStep()
        val password = "secret".toCharArray()
        m.installWithPassword(password); advanceUntilIdle()

        assertEquals(OnboardingStep.Connect, m.state.value.step)
        assertEquals(ConnectState.Success, m.state.value.connect)
        assertEquals(listOf("secret", null), t.client.calls.map { it.password })
        assertTrue(zeroed(password))
    }

    @Test
    fun `first connection asks to trust, pins only after the tap, then installs with the same password`() = runTest {
        t.client.respond(Script.Error(SshError.HostKeyUnknown(hostKey.type, hostKey.fingerprint)), ok, echoOk)
        val m = passwordStep()
        m.installWithPassword("secret".toCharArray()); advanceUntilIdle()
        assertEquals(ConnectState.AskTrust(hostKey), m.state.value.install)
        assertNull(t.hostKeys.get(PoolServer.HOST, PoolServer.PORT))

        m.trustHostKey(); advanceUntilIdle()
        assertEquals(hostKey, t.hostKeys.get(PoolServer.HOST, PoolServer.PORT))
        assertEquals(listOf("secret", "secret", null), t.client.calls.map { it.password })
        assertEquals(ConnectState.Success, m.state.value.connect)
    }

    @Test
    fun `declining the host key forgets the password and pins nothing`() = runTest {
        t.client.respond(Script.Error(SshError.HostKeyUnknown(hostKey.type, hostKey.fingerprint)), ok)
        val m = passwordStep()
        m.installWithPassword("secret".toCharArray()); advanceUntilIdle()
        m.declineHostKey()
        assertEquals(ConnectState.Idle, m.state.value.install)
        m.trustHostKey(); advanceUntilIdle() // nothing left to retry with
        assertTrue(t.hostKeys.pins.isEmpty())
        assertEquals(1, t.client.calls.size)
    }

    @Test
    fun `a wrong password stays on the step with the password error`() = runTest {
        t.client.respond(Script.Error(SshError.AuthRejected))
        val m = passwordStep()
        m.installWithPassword("wrong".toCharArray()); advanceUntilIdle()
        assertEquals(OnboardingStep.PasswordSetup, m.state.value.step)
        assertEquals(FailureKind.PasswordRejected, assertIs<ConnectState.Failed>(m.state.value.install).failure.kind)
    }

    @Test
    fun `an empty password does nothing and back returns to the key step`() = runTest {
        val m = passwordStep()
        m.installWithPassword(CharArray(0)); advanceUntilIdle()
        assertTrue(t.client.calls.isEmpty())
        m.back()
        assertEquals(OnboardingStep.Key, m.state.value.step)
        val late = "secret".toCharArray()
        m.installWithPassword(late); advanceUntilIdle() // not on the password step any more
        assertTrue(t.client.calls.isEmpty())
        assertTrue(zeroed(late))
    }
}
