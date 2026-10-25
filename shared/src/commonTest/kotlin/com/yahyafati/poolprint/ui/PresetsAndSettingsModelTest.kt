package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.core.discovery.Fixtures
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.fakes.FakeSshClient.Script
import com.yahyafati.poolprint.fakes.TestDeps
import com.yahyafati.poolprint.fakes.user
import com.yahyafati.poolprint.transport.SshError
import com.yahyafati.poolprint.transport.SshResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PresetsAndSettingsModelTest {
    private val t = TestDeps(user())

    @Test
    fun `presets can be renamed and deleted`() = runTest {
        t.deps.presets.create("One", PrintOptions())
        t.deps.presets.create("Two", PrintOptions())
        val m = PresetsModel(t.deps, this); advanceUntilIdle()
        assertEquals(listOf("One", "Two"), m.state.value.presets.map { it.name })
        assertEquals(ValidationError.PresetNameTaken, m.rename(m.state.value.presets[0].id, "two"))
        assertNull(m.rename(m.state.value.presets[0].id, "Uno")); advanceUntilIdle()
        assertEquals(listOf("Uno", "Two"), m.state.value.presets.map { it.name })
        m.delete(m.state.value.presets[0].id); advanceUntilIdle()
        assertEquals(listOf("Two"), m.state.value.presets.map { it.name })
    }

    @Test
    fun `manual refresh in settings updates the cache and reports the result`() = runTest {
        t.client.respond(Script.Result(SshResult(0, Fixtures.lpstat, "")), Script.Result(SshResult(0, Fixtures.lpoptions, "")))
        val m = SettingsModel(t.deps, this); advanceUntilIdle()
        assertNull(m.state.value.discoveryUpdatedAt)
        m.refreshDiscovery(); advanceUntilIdle()
        assertEquals(DiscoveryRefresh.Updated, m.state.value.discoveryRefresh)
        assertEquals(5, m.state.value.discoveryPrinterCount)
        assertEquals(t.nowMillis, m.state.value.discoveryUpdatedAt)
    }

    @Test
    fun `a failing refresh in settings keeps the old data and says so`() = runTest {
        t.client.respond(Script.Error(SshError.HostUnreachable))
        val m = SettingsModel(t.deps, this); advanceUntilIdle()
        m.refreshDiscovery(); advanceUntilIdle()
        assertEquals(DiscoveryRefresh.Unavailable, m.state.value.discoveryRefresh)
        assertNull(m.state.value.discoveryUpdatedAt)
    }

    @Test
    fun `timestamps are formatted in UTC`() {
        assertEquals("1970-01-01 00:00 UTC", formatTimestamp(0))
        assertEquals("2026-10-25 14:03 UTC", formatTimestamp(1_792_936_980_000L))
        assertEquals("2024-02-29 23:59 UTC", formatTimestamp(1_709_251_199_000L))
    }
}
