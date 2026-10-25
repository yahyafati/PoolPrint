package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.core.discovery.DiscoveredOption
import com.yahyafati.poolprint.core.discovery.Fixtures
import com.yahyafati.poolprint.core.discovery.PrinterCapabilities
import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.model.PrinterType
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.fakes.FakeDocument
import com.yahyafati.poolprint.fakes.FakeSshClient.Script
import com.yahyafati.poolprint.fakes.TestDeps
import com.yahyafati.poolprint.fakes.user
import com.yahyafati.poolprint.transport.SshError
import com.yahyafati.poolprint.transport.SshResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrintModelDiscoveryTest {
    private val t = TestDeps(user())
    private val doc = FakeDocument("slides; rm -rf ~.pdf")
    private val base = "'lpr' '-P' 'tfppr1' '-o' 'sides=one-sided' '-o' 'media=A4'"
    private fun TestScope.model() = PrintModel(t.deps, doc, this)

    private val lpstatOk = Script.Result(SshResult(0, Fixtures.lpstat, ""))
    private val lpoptionsOk = Script.Result(SshResult(0, Fixtures.lpoptions, ""))

    @Test
    fun `opening the sheet reads only the cache and never the network`() = runTest {
        val m = model(); advanceUntilIdle()
        assertTrue(t.client.calls.isEmpty())
        assertTrue(m.state.value.usingDefaults)
        assertEquals(listOf("tfppr1", "tfppr2", "tfppr3", "tfppr4"), m.state.value.knownPrinters.map { it.name })
    }

    @Test
    fun `refreshing fetches printers and options and builds controls`() = runTest {
        t.client.respond(lpstatOk, lpoptionsOk)
        val m = model(); advanceUntilIdle()
        m.refreshDiscovery(); advanceUntilIdle()
        assertEquals(listOf("'lpstat' '-p'", "'lpoptions' '-p' 'tfppr1' '-l'"), t.client.calls.map { it.command })
        val state = m.state.value
        assertEquals(DiscoveryStatus.Idle, state.discoveryStatus)
        assertEquals(listOf("ColorModel", "HPEconoMode", "Resolution"), state.discoveredControls.map { it.key })
        assertFalse(state.usingDefaults)
        assertEquals("extra-lab", state.knownPrinters.last().name)
        assertEquals(PrinterType.UNKNOWN, state.knownPrinters.last().type)
    }

    @Test
    fun `a fresh cache is not fetched again`() = runTest {
        t.client.respond(lpstatOk, lpoptionsOk)
        val m = model(); advanceUntilIdle()
        m.refreshDiscovery(); advanceUntilIdle()
        m.refreshDiscovery(); advanceUntilIdle()
        assertEquals(2, t.client.calls.size)
        m.refreshDiscovery(force = true); advanceUntilIdle()
        assertEquals(4, t.client.calls.size)
    }

    @Test
    fun `a stale cache is refreshed`() = runTest {
        t.client.respond(lpstatOk, lpoptionsOk)
        val m = model(); advanceUntilIdle()
        m.refreshDiscovery(); advanceUntilIdle()
        t.nowMillis += 25L * 60 * 60 * 1000
        m.refreshDiscovery(); advanceUntilIdle()
        assertEquals(4, t.client.calls.size)
    }

    @Test
    fun `printing works when discovery fails or returns garbage`() = runTest {
        for (script in listOf(
            Script.Error(SshError.HostUnreachable),
            Script.Error(SshError.Timeout),
            Script.Error(SshError.HostKeyUnknown("ssh-ed25519", "SHA256:x")),
            Script.Result(SshResult(1, "", "boom")),
            Script.Result(SshResult(0, "complete nonsense \u0000", "")),
        )) {
            val deps = TestDeps(user())
            deps.client.respond(script)
            val m = PrintModel(deps.deps, doc, this)
            advanceUntilIdle()
            m.refreshDiscovery(); advanceUntilIdle()
            assertEquals(DiscoveryStatus.Unavailable, m.state.value.discoveryStatus)
            assertTrue(m.state.value.usingDefaults)
            assertTrue(m.state.value.discoveredControls.isEmpty())
            assertEquals(4, m.state.value.knownPrinters.size)

            deps.client.respond(Script.Result(SshResult(0, "request id is tfppr1-1 (1 file(s))", "")))
            m.print(); advanceUntilIdle()
            assertIs<PrintPhase.Sent>(m.state.value.phase)
            assertEquals(base, deps.client.calls.last().command)
        }
    }

    private fun TestScope.modelWithCaps(): PrintModel {
        t.discoveryStore.snapshot = com.yahyafati.poolprint.core.discovery.DiscoverySnapshot(
            printersFetchedAtMillis = t.nowMillis,
            capabilities = mapOf(
                "tfppr1" to PrinterCapabilities(
                    "tfppr1",
                    listOf(DiscoveredOption("ColorModel", "Color Mode", listOf("Gray", "RGB"), "RGB")),
                    t.nowMillis,
                ),
                "tfppr2" to PrinterCapabilities(
                    "tfppr2",
                    listOf(DiscoveredOption("Staple", "Staple", listOf("None", "Left"), "None")),
                    t.nowMillis,
                ),
            ),
        )
        return model()
    }

    @Test
    fun `a chosen discovered value reaches the command and the default does not`() = runTest {
        val m = modelWithCaps(); advanceUntilIdle()
        m.setDiscovered("ColorModel", "Gray")
        m.print(); advanceUntilIdle()
        assertEquals("$base '-o' 'ColorModel=Gray'", t.client.calls.single().command)

        m.dismissFailure(); m.setDiscovered("ColorModel", "RGB")
        assertTrue(m.state.value.options.discovered.isEmpty())
    }

    @Test
    fun `values and keys that are not in the cache are ignored`() = runTest {
        val m = modelWithCaps(); advanceUntilIdle()
        for ((key, value) in listOf(
            "ColorModel" to "CMYK", "ColorModel" to "Gray;id", "ColorModel" to "\$(id)", "Nope" to "Gray",
            "ColorModel;id" to "Gray", "Staple" to "Left", "media" to "A3", "Duplex" to "None", "" to "",
        )) {
            m.setDiscovered(key, value)
        }
        assertTrue(m.state.value.options.discovered.isEmpty())
        m.print(); advanceUntilIdle()
        assertEquals(base, t.client.calls.single().command)
    }

    @Test
    fun `changing the printer drops the discovered choices`() = runTest {
        val m = modelWithCaps(); advanceUntilIdle()
        m.setDiscovered("ColorModel", "Gray")
        m.selectPrinter("tfppr2")
        assertTrue(m.state.value.options.discovered.isEmpty())
        m.setDiscovered("ColorModel", "Gray") // not an option of tfppr2
        assertTrue(m.state.value.options.discovered.isEmpty())
        m.setDiscovered("Staple", "Left")
        m.print(); advanceUntilIdle()
        assertEquals("'lpr' '-P' 'tfppr2' '-o' 'sides=one-sided' '-o' 'media=A4' '-o' 'Staple=Left'", t.client.calls.single().command)
    }

    @Test
    fun `a refresh that removes a value also removes the choice`() = runTest {
        val m = modelWithCaps(); advanceUntilIdle()
        m.setDiscovered("ColorModel", "Gray")
        t.client.respond(lpstatOk, Script.Result(SshResult(0, "ColorModel/Color Mode: *RGB CMYK", "")))
        m.refreshDiscovery(force = true); advanceUntilIdle()
        assertTrue(m.state.value.options.discovered.isEmpty())
        assertEquals(listOf("RGB", "CMYK"), m.state.value.capabilities?.option("ColorModel")?.values)
    }

    @Test
    fun `switching to a discovered printer works and it is billed as colour`() = runTest {
        t.client.respond(lpstatOk, lpoptionsOk)
        val m = model(); advanceUntilIdle()
        m.refreshDiscovery(); advanceUntilIdle()
        m.selectPrinter("extra-lab"); advanceUntilIdle()
        assertEquals("extra-lab", m.state.value.printer.name)
        assertEquals(PrinterType.UNKNOWN, m.state.value.printer.type)
        assertEquals(10 * 24, m.state.value.estimate?.costCents)
        m.selectPrinter("not-discovered")
        assertEquals("extra-lab", m.state.value.printer.name)
    }

    // ---- presets ----

    @Test
    fun `saves a preset, applies it and shows it as active until something changes`() = runTest {
        val m = modelWithCaps(); advanceUntilIdle()
        m.setDuplex(Duplex.LONG_EDGE); m.setPagesPerSheet(PagesPerSheet.TWO); m.setDiscovered("ColorModel", "Gray")
        assertNull(m.savePreset("Lecture slides"))
        assertEquals("Lecture slides", m.state.value.activePreset?.name)

        m.setDuplex(Duplex.OFF); m.setDiscovered("ColorModel", "RGB"); m.selectPrinter("tfppr3"); m.setCopies(4)
        assertNull(m.state.value.activePreset)

        m.applyPreset(m.state.value.presets.single().id)
        val options = m.state.value.options
        assertEquals("tfppr1", options.printer.name)
        assertEquals(Duplex.LONG_EDGE, options.duplex)
        assertEquals(PagesPerSheet.TWO, options.pagesPerSheet)
        assertEquals(4, options.copies) // copies are not part of a preset
        assertEquals("Lecture slides", m.state.value.activePreset?.name)

        m.print(); advanceUntilIdle()
        assertEquals(
            "'lpr' '-P' 'tfppr1' '-#' '4' '-o' 'sides=two-sided-long-edge' '-o' 'number-up=2' '-o' 'media=A4' '-o' 'ColorModel=Gray'",
            t.client.calls.single().command,
        )
    }

    @Test
    fun `a preset's discovered value that isn't in the cache is dropped when applied`() = runTest {
        val m = modelWithCaps(); advanceUntilIdle()
        m.setDiscovered("ColorModel", "Gray")
        m.savePreset("Gray")
        // The server's options change under the preset.
        t.discoveryStore.snapshot = t.discoveryStore.snapshot.copy(
            capabilities = mapOf("tfppr1" to PrinterCapabilities("tfppr1", listOf(DiscoveredOption("ColorModel", "Color", listOf("RGB", "CMYK"), "RGB")), 1)),
        )
        val fresh = model(); advanceUntilIdle()
        fresh.applyPreset(fresh.state.value.presets.single().id)
        assertTrue(fresh.state.value.options.discovered.isEmpty())
    }

    @Test
    fun `an applied preset keeps the typed page ranges`() = runTest {
        val m = model(); advanceUntilIdle()
        m.setDuplex(Duplex.SHORT_EDGE)
        m.savePreset("Short")
        m.setDuplex(Duplex.OFF); m.setPageRanges("2-3")
        m.applyPreset(m.state.value.presets.single().id)
        assertEquals("2-3", m.state.value.options.pageRanges?.canonical)
        assertEquals(Duplex.SHORT_EDGE, m.state.value.duplexForTest())
    }

    private fun PrintState.duplexForTest() = options.duplex

    @Test
    fun `preset names are validated and never reach the command`() = runTest {
        val m = model(); advanceUntilIdle()
        assertEquals(ValidationError.PresetNameEmpty, m.savePreset("   "))
        assertNull(m.savePreset("x; rm -rf ~ \$(id)"))
        assertEquals(ValidationError.PresetNameTaken, m.savePreset("X; RM -rf ~ \$(id)"))
        m.print(); advanceUntilIdle()
        val command = t.client.calls.single().command
        assertEquals(base, command)
        assertTrue("rm" !in command && "x;" !in command)
        assertNotNull(m.state.value.activePreset)
    }
}
