package com.yahyafati.poolprint.service

import com.yahyafati.poolprint.core.discovery.DiscoverySnapshot
import com.yahyafati.poolprint.core.discovery.Fixtures
import com.yahyafati.poolprint.core.discovery.PrinterState
import com.yahyafati.poolprint.core.model.PrinterType
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.fakes.FakeSshClient.Script
import com.yahyafati.poolprint.fakes.TestDeps
import com.yahyafati.poolprint.fakes.user
import com.yahyafati.poolprint.transport.SshError
import com.yahyafati.poolprint.transport.SshResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoveryServiceTest {
    private val t = TestDeps(user())
    private val discovery get() = t.deps.discovery

    @Test
    fun `fetches the printers and keeps them in the cache`() = runTest {
        t.client.respond(Script.Result(SshResult(0, Fixtures.lpstat, "")))
        val outcome = assertIs<DiscoveryOutcome.Updated>(discovery.refreshPrinters())
        assertEquals("'lpstat' '-p'", t.client.calls.single().command)
        assertEquals(5, outcome.snapshot.printers.size)
        assertEquals(1_000_000_000L, outcome.snapshot.printersFetchedAtMillis)
        assertEquals(outcome.snapshot, t.discoveryStore.snapshot)
        // The hardcoded printers keep their type; an unknown one is billed as colour.
        val known = outcome.snapshot.knownPrinters
        assertEquals(listOf("tfppr1", "tfppr2", "tfppr3", "tfppr4", "extra-lab"), known.map { it.name })
        assertEquals(PrinterType.UNKNOWN, known.last().type)
        assertEquals(PrinterState.DISABLED, outcome.snapshot.stateOf(Printers.all[2]))
    }

    @Test
    fun `fetches the options of one printer`() = runTest {
        t.client.respond(Script.Result(SshResult(0, Fixtures.lpoptions, "")))
        val snapshot = assertIs<DiscoveryOutcome.Updated>(discovery.refreshOptions(Printers.all[0])).snapshot
        assertEquals("'lpoptions' '-p' 'tfppr1' '-l'", t.client.calls.single().command)
        assertEquals(listOf("ColorModel", "HPEconoMode", "Resolution"), snapshot.capabilitiesFor(Printers.all[0])?.options?.map { it.key })
        assertNull(snapshot.capabilitiesFor(Printers.all[1]))
    }

    @Test
    fun `errors leave the cache alone and report unavailable`() = runTest {
        val before = DiscoverySnapshot(printersFetchedAtMillis = 5)
        t.discoveryStore.snapshot = before
        for (script in listOf(
            Script.Error(SshError.HostUnreachable),
            Script.Error(SshError.Timeout),
            Script.Error(SshError.AuthRejected),
            Script.Error(SshError.Protocol("boom")),
            Script.Result(SshResult(1, "", "lpstat: Bad file descriptor")),
            Script.Result(SshResult(0, "", "")),
            Script.Result(SshResult(0, "Drucker tfppr1 ist im Leerlauf.", "")),
            Script.Result(SshResult(0, "\u0000\u0001garbage", "")),
        )) {
            t.client.respond(script)
            assertIs<DiscoveryOutcome.Unavailable>(discovery.refreshPrinters(), "script: $script")
            assertIs<DiscoveryOutcome.Unavailable>(discovery.refreshOptions(Printers.all[0]), "script: $script")
        }
        assertEquals(before, t.discoveryStore.snapshot)
    }

    @Test
    fun `an unknown host key is reported but never trusted`() = runTest {
        t.client.respond(Script.Error(SshError.HostKeyUnknown("ssh-ed25519", "SHA256:abc")))
        assertIs<DiscoveryOutcome.NeedsTrust>(discovery.refreshPrinters())
        assertTrue(t.hostKeys.pins.isEmpty())
    }

    @Test
    fun `not set up means unavailable`() = runTest {
        val unconfigured = TestDeps(null)
        assertIs<DiscoveryOutcome.Unavailable>(unconfigured.deps.discovery.refreshPrinters())
        assertTrue(unconfigured.client.calls.isEmpty())
    }

    @Test
    fun `a tampered cache is cleaned on the way in`() = runTest {
        t.discoveryStore.snapshot = DiscoverySnapshot(
            printers = listOf(
                com.yahyafati.poolprint.core.discovery.DiscoveredPrinter("tfppr1;id", PrinterState.IDLE),
                com.yahyafati.poolprint.core.discovery.DiscoveredPrinter("ok-printer", PrinterState.IDLE),
            ),
        )
        // The raw store keeps whatever it was given; DataStoreDiscoveryStore applies sanitized() on load.
        val cleaned = t.discoveryStore.snapshot.sanitized()
        assertEquals(listOf("ok-printer"), cleaned.printers.map { it.name })
    }

    @Test
    fun `staleness follows the policy`() {
        val day = com.yahyafati.poolprint.core.discovery.DiscoveryPolicy.STALE_AFTER_MILLIS
        assertTrue(com.yahyafati.poolprint.core.discovery.DiscoveryPolicy.isStale(null, 100))
        assertTrue(!com.yahyafati.poolprint.core.discovery.DiscoveryPolicy.isStale(100, 100 + day))
        assertTrue(com.yahyafati.poolprint.core.discovery.DiscoveryPolicy.isStale(100, 101 + day))
        assertTrue(com.yahyafati.poolprint.core.discovery.DiscoveryPolicy.isStale(1000, 5)) // clock went backwards
    }
}
