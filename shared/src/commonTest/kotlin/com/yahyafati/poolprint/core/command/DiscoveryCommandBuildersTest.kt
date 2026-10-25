package com.yahyafati.poolprint.core.command

import com.yahyafati.poolprint.core.Payloads
import com.yahyafati.poolprint.core.discovery.DiscoveredOption
import com.yahyafati.poolprint.core.discovery.PrinterCapabilities
import com.yahyafati.poolprint.core.model.DiscoveredSetting
import com.yahyafati.poolprint.core.model.PrintJob
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.queue.QueueEntry
import com.yahyafati.poolprint.core.queue.QueueSnapshot
import com.yahyafati.poolprint.core.validation.Validated
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.core.validation.Validators
import com.yahyafati.poolprint.core.validation.valueOrNull
import com.yahyafati.poolprint.fakes.user
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DiscoveryCommandBuildersTest {
    private val tfppr1 = Printers.all[0]
    private val caps = PrinterCapabilities(
        "tfppr1",
        listOf(
            DiscoveredOption("ColorModel", "Color Mode", listOf("Gray", "RGB"), "RGB"),
            DiscoveredOption("Resolution", "Resolution", listOf("300dpi", "600dpi"), "600dpi"),
        ),
        fetchedAtMillis = 1,
    )
    private val defaults = "'lpr' '-P' 'tfppr1' '-o' 'sides=one-sided' '-o' 'media=A4'"

    private fun lpr(options: PrintOptions) = LprCommandBuilder.build(PrintJob(options, "doc.pdf")).value
    private fun setting(key: String, value: String) = Validators.discoveredSetting(caps, key, value).valueOrNull()!!

    @Test
    fun `lpstat lpoptions and lpq have a fixed shape`() {
        assertEquals("'lpstat' '-p'", LpstatCommandBuilder.build().value)
        assertEquals("'lpoptions' '-p' 'tfppr1' '-l'", LpoptionsCommandBuilder.build(tfppr1).value)
        assertEquals("'lpq' '-P' 'tfppr4'", LpqCommandBuilder.build(Printers.all[3]).value)
    }

    @Test
    fun `discovered options come after the standard ones, sorted by key`() {
        val options = PrintOptions(discovered = listOf(setting("Resolution", "300dpi"), setting("ColorModel", "Gray")))
        assertEquals("$defaults '-o' 'ColorModel=Gray' '-o' 'Resolution=300dpi'", lpr(options))
    }

    @Test
    fun `no discovered options means the M2 command`() {
        assertEquals(defaults, lpr(PrintOptions()))
    }

    @Test
    fun `a setting that belongs to another printer can't be combined with this printer`() {
        val gray = setting("ColorModel", "Gray")
        assertFailsWith<IllegalArgumentException> { PrintOptions(printer = Printers.all[1], discovered = listOf(gray)) }
    }

    @Test
    fun `hostile keys are rejected even when the cache contains them as plain text`() {
        for (payload in Payloads.all) {
            val result = Validators.discoveredSetting(caps, payload, "Gray")
            assertIs<Validated.Invalid>(result, "key: $payload")
        }
    }

    @Test
    fun `hostile values are rejected`() {
        for (payload in Payloads.all) {
            val result = Validators.discoveredSetting(caps, "ColorModel", payload)
            assertEquals(ValidationError.OptionValueInvalid, (result as Validated.Invalid).error, "value: $payload")
        }
    }

    @Test
    fun `a value that is not in the cache can't reach the command, even when it looks harmless`() {
        for (value in listOf("RGB2", "gray", "Color", "CMYK", "600dpi")) {
            assertIs<Validated.Invalid>(Validators.discoveredSetting(caps, "ColorModel", value), "value: $value")
        }
        assertIs<Validated.Invalid>(Validators.discoveredSetting(caps, "NotCached", "Gray"))
    }

    @Test
    fun `keys that collide with a standard control are rejected even if the cache holds them`() {
        val poisoned = caps.copy(options = caps.options + DiscoveredOption("media", "Media", listOf("A3"), null) + DiscoveredOption("Duplex", "d", listOf("None"), null))
        assertEquals(ValidationError.OptionUnknown, (Validators.discoveredSetting(poisoned, "media", "A3") as Validated.Invalid).error)
        assertEquals(ValidationError.OptionUnknown, (Validators.discoveredSetting(poisoned, "Duplex", "None") as Validated.Invalid).error)
    }

    @Test
    fun `a hostile setting can't even be constructed`() {
        for (payload in Payloads.all) {
            assertFailsWith<IllegalArgumentException>("key: $payload") { DiscoveredSetting("tfppr1", payload, "ok") }
            assertFailsWith<IllegalArgumentException>("value: $payload") { DiscoveredSetting("tfppr1", "Key", payload) }
        }
    }

    @Test
    fun `preset names never appear in a command`() {
        // The name is not part of PrintOptions at all, so a command can't contain it.
        for (payload in Payloads.all) {
            assertEquals(defaults, lpr(PrintOptions()), "payload: $payload")
        }
    }

    // ---- lprm and job ids ----

    private val queue = QueueSnapshot(
        "tfppr1",
        null,
        listOf(
            QueueEntry("active", "jdoe", "1234", "(stdin)", 10),
            QueueEntry("1st", "muster", "1235", "x.pdf", 10),
        ),
    )

    @Test
    fun `lprm takes a validated own job id`() {
        val job = Validators.ownedJobId("1234", queue, user("jdoe")).valueOrNull()!!
        assertEquals("'lprm' '-P' 'tfppr1' '1234'", LprmCommandBuilder.build(tfppr1, job).value)
    }

    @Test
    fun `other users jobs can't be cancelled`() {
        assertEquals(ValidationError.JobNotOwned, (Validators.ownedJobId("1235", queue, user("jdoe")) as Validated.Invalid).error)
    }

    @Test
    fun `a job that is not in the latest queue can't be cancelled`() {
        assertEquals(ValidationError.JobNotFound, (Validators.ownedJobId("999", queue, user("jdoe")) as Validated.Invalid).error)
    }

    @Test
    fun `hostile job ids are rejected`() {
        val extra = listOf("12 34", "*", "1234;id", "-1", "+1", "１２３４", "1234\n", "0x4d2", "1e3", "12345678901")
        for (payload in Payloads.all + extra) {
            assertIs<Validated.Invalid>(Validators.ownedJobId(payload, queue, user("jdoe")), "job id: $payload")
        }
    }

    @Test
    fun `a job id of another printer can't be used with this printer`() {
        val job = Validators.ownedJobId("1234", queue, user("jdoe")).valueOrNull()!!
        assertFailsWith<IllegalArgumentException> { LprmCommandBuilder.build(Printers.all[1], job) }
    }

    @Test
    fun `preset names are validated as text, not as commands`() {
        assertEquals("Lecture slides", Validators.presetName("  Lecture slides ", emptyList()).valueOrNull())
        assertEquals(ValidationError.PresetNameEmpty, (Validators.presetName("   ", emptyList()) as Validated.Invalid).error)
        assertIs<Validated.Invalid>(Validators.presetName("x".repeat(41), emptyList()))
        assertIs<Validated.Invalid>(Validators.presetName("a\nb", emptyList()))
        assertEquals(ValidationError.PresetNameTaken, (Validators.presetName("LECTURE", listOf("lecture")) as Validated.Invalid).error)
        assertTrue(Validators.presetName("tfppr1; rm -rf ~", emptyList()) is Validated.Valid) // fine as a label: it never reaches a shell
    }
}
