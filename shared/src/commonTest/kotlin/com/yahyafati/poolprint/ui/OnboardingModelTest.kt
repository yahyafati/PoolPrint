package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.core.Payloads
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.fakes.FakeSshClient.Script
import com.yahyafati.poolprint.fakes.TestDeps
import com.yahyafati.poolprint.fakes.user
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.service.FailureKind
import com.yahyafati.poolprint.transport.PoolServer
import com.yahyafati.poolprint.transport.SshError
import com.yahyafati.poolprint.transport.SshResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingModelTest {
    private fun TestScope.model(t: TestDeps) = OnboardingModel(t.deps, this).also { advanceUntilIdle() }

    private val key = PinnedHostKey("ssh-ed25519", "SHA256:server")

    private suspend fun TestScope.toConnectStep(m: OnboardingModel) {
        m.start(); m.onUsernameChange("jdoe"); m.submitUsername(); advanceUntilIdle(); m.continueToConnect()
    }

    @Test
    fun `starts at the welcome step and moves to the username step`() = runTest {
        val m = model(TestDeps())
        assertEquals(OnboardingStep.Welcome, m.state.value.step)
        m.start()
        assertEquals(OnboardingStep.Username, m.state.value.step)
    }

    @Test
    fun `an invalid username is rejected and nothing is saved or generated`() = runTest {
        val t = TestDeps()
        val m = model(t)
        m.start()
        for (bad in Payloads.shell.filter { it.isNotBlank() } + listOf("JDoe", "j doe", "a".repeat(40))) {
            m.onUsernameChange(bad); m.submitUsername(); advanceUntilIdle()
            assertEquals(OnboardingStep.Username, m.state.value.step, bad)
            assertEquals(ValidationError.UsernameInvalid, m.state.value.usernameError, bad)
        }
        m.onUsernameChange("  "); m.submitUsername(); advanceUntilIdle()
        assertEquals(ValidationError.UsernameEmpty, m.state.value.usernameError)
        assertNull(t.settings.getUsername())
        assertEquals(0, t.generator.counter)
    }

    @Test
    fun `a valid username saves it, generates the key and shows the public key`() = runTest {
        val t = TestDeps()
        val m = model(t)
        m.start(); m.onUsernameChange(" jdoe "); m.submitUsername(); advanceUntilIdle()
        assertEquals(OnboardingStep.Key, m.state.value.step)
        assertEquals(user(), t.settings.getUsername())
        assertEquals("ssh-ed25519 AAAAKEY1 pool-print@test", m.state.value.publicKey)
    }

    @Test
    fun `with a saved username it starts at the key step`() = runTest {
        val m = model(TestDeps(user()))
        assertEquals(OnboardingStep.Key, m.state.value.step)
        assertEquals("jdoe", m.state.value.usernameInput)
    }

    @Test
    fun `first connection asks to trust, then pins and tests again`() = runTest {
        val t = TestDeps()
        t.client.respond(
            Script.Error(SshError.HostKeyUnknown(key.type, key.fingerprint)),
            Script.Result(SshResult(0, "ok\n", "")),
        )
        val m = model(t)
        toConnectStep(m)
        m.testConnection(); advanceUntilIdle()
        assertEquals(ConnectState.AskTrust(key), m.state.value.connect)
        assertNull(t.hostKeys.get(PoolServer.HOST, PoolServer.PORT)) // nothing pinned before the tap

        m.trustHostKey(); advanceUntilIdle()
        assertEquals(key, t.hostKeys.get(PoolServer.HOST, PoolServer.PORT))
        assertEquals(ConnectState.Success, m.state.value.connect)
    }

    @Test
    fun `declining the host key pins nothing`() = runTest {
        val t = TestDeps()
        t.client.respond(Script.Error(SshError.HostKeyUnknown(key.type, key.fingerprint)))
        val m = model(t)
        toConnectStep(m)
        m.testConnection(); advanceUntilIdle()
        m.declineHostKey()
        assertEquals(ConnectState.Idle, m.state.value.connect)
        assertTrue(t.hostKeys.pins.isEmpty())
    }

    @Test
    fun `finish is only possible after a successful test and then completes onboarding`() = runTest {
        val t = TestDeps()
        t.client.respond(Script.Result(SshResult(0, "ok\n", "")))
        val m = model(t)
        toConnectStep(m)
        m.finish(); advanceUntilIdle()
        assertEquals(false, t.settings.onboardingComplete.value)
        m.testConnection(); advanceUntilIdle()
        m.finish(); advanceUntilIdle()
        assertEquals(true, t.settings.onboardingComplete.value)
        assertEquals(OnboardingStep.Done, m.state.value.step)
    }

    @Test
    fun `auth rejected is shown and a retry can succeed`() = runTest {
        val t = TestDeps()
        t.client.respond(Script.Error(SshError.AuthRejected), Script.Result(SshResult(0, "ok", "")))
        val m = model(t)
        toConnectStep(m)
        m.testConnection(); advanceUntilIdle()
        assertEquals(FailureKind.AuthRejected, assertIs<ConnectState.Failed>(m.state.value.connect).failure.kind)
        m.testConnection(); advanceUntilIdle()
        assertEquals(ConnectState.Success, m.state.value.connect)
    }

    @Test
    fun `a changed host key is a failure with both fingerprints and no way to continue`() = runTest {
        val t = TestDeps()
        t.client.respond(Script.Error(SshError.HostKeyChanged("SHA256:old", "SHA256:new")))
        val m = model(t)
        toConnectStep(m)
        m.testConnection(); advanceUntilIdle()
        val failure = assertIs<ConnectState.Failed>(m.state.value.connect).failure
        assertEquals(FailureKind.HostKeyChanged, failure.kind)
        m.trustHostKey(); advanceUntilIdle() // no-op: nothing to trust
        assertTrue(t.hostKeys.pins.isEmpty())
        m.finish(); advanceUntilIdle()
        assertEquals(false, t.settings.onboardingComplete.value)
    }
}
