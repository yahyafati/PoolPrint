package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.fakes.TestDeps
import com.yahyafati.poolprint.fakes.user
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.transport.PoolServer
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsModelTest {
    private val t = TestDeps(user())
    private fun TestScope.model() = SettingsModel(t.deps, this).also { advanceUntilIdle() }

    @Test
    fun `forgetting the pinned key needs the second confirmation`() = runTest {
        val pin = PinnedHostKey("ssh-ed25519", "SHA256:abc")
        t.hostKeys.pin(PoolServer.HOST, PoolServer.PORT, pin)
        val m = model()
        assertEquals(pin, m.state.value.hostKey)

        m.ask(SettingsConfirm.ForgetHostKey); advanceUntilIdle()
        assertEquals(pin, t.hostKeys.get(PoolServer.HOST, PoolServer.PORT))
        m.cancelConfirm()
        assertEquals(pin, t.hostKeys.get(PoolServer.HOST, PoolServer.PORT))

        m.ask(SettingsConfirm.ForgetHostKey); m.confirmForgetHostKey(); advanceUntilIdle()
        assertNull(t.hostKeys.get(PoolServer.HOST, PoolServer.PORT))
        assertNull(m.state.value.hostKey)
    }

    @Test
    fun `regenerating the key replaces it and sends the user through onboarding again`() = runTest {
        t.keys().ensureKey()
        t.settings.setOnboardingComplete(true)
        val m = model()
        m.ask(SettingsConfirm.RegenerateKey); m.confirmRegenerateKey(); advanceUntilIdle()
        assertEquals("ssh-ed25519 AAAAKEY2 pool-print@test", t.settings.getPublicKey())
        assertEquals(false, t.settings.onboardingComplete.value)
    }

    private fun TestDeps.keys() = deps.keys
}
