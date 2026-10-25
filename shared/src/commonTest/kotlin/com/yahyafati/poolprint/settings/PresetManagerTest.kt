package com.yahyafati.poolprint.settings

import com.yahyafati.poolprint.core.discovery.DiscoveredOption
import com.yahyafati.poolprint.core.discovery.PrinterCapabilities
import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.model.PrintLimits
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.validation.Validated
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.core.validation.Validators
import com.yahyafati.poolprint.core.validation.valueOrNull
import com.yahyafati.poolprint.fakes.FakePresetStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresetManagerTest {
    private val store = FakePresetStore()
    private val manager = PresetManager(store)
    private val caps = PrinterCapabilities("tfppr1", listOf(DiscoveredOption("ColorModel", "Color", listOf("Gray", "RGB"), "RGB")), 1)
    private val slides = PrintOptions(duplex = Duplex.LONG_EDGE, pagesPerSheet = PagesPerSheet.FOUR)

    @Test
    fun `creates, lists, renames and deletes`() = runTest {
        val created = manager.create("Lecture slides", slides).valueOrNull()!!
        assertEquals(listOf(created), manager.list())
        val second = manager.create("Exam", PrintOptions(printer = Printers.all[2])).valueOrNull()!!
        assertEquals(created.id + 1, second.id)

        assertEquals("Slides", manager.rename(created.id, "Slides").valueOrNull()?.name)
        assertEquals(listOf("Slides", "Exam"), manager.list().map { it.name })

        manager.delete(created.id)
        assertEquals(listOf("Exam"), manager.list().map { it.name })
        // Ids are not reused for an existing preset.
        assertEquals(second.id + 1, manager.create("Third", slides).valueOrNull()!!.id)
    }

    @Test
    fun `a preset stores printer and layout plus discovered choices, but no copies or page ranges`() = runTest {
        val options = PrintOptions(
            copies = 5,
            pageRanges = Validators.pageRanges("1-3").valueOrNull(),
            duplex = Duplex.SHORT_EDGE,
            discovered = listOf(Validators.discoveredSetting(caps, "ColorModel", "Gray").valueOrNull()!!),
        )
        val preset = manager.create("Gray duplex", options).valueOrNull()!!
        val applied = preset.settings.toOptions()
        assertEquals(1, applied.copies)
        assertNull(applied.pageRanges)
        assertEquals(Duplex.SHORT_EDGE, applied.duplex)
        assertEquals(mapOf("ColorModel" to "Gray"), preset.discovered)
        assertTrue(preset.matches(options.copy(copies = 2, pageRanges = null)))
        assertFalse(preset.matches(options.copy(duplex = Duplex.OFF)))
        assertFalse(preset.matches(options.copy(discovered = emptyList())))
    }

    @Test
    fun `names are unique ignoring case, trimmed and bounded`() = runTest {
        manager.create(" Lecture ", slides)
        assertEquals("Lecture", manager.list().single().name)
        assertEquals(ValidationError.PresetNameTaken, (manager.create("LECTURE", slides) as Validated.Invalid).error)
        assertEquals(ValidationError.PresetNameEmpty, (manager.create("  ", slides) as Validated.Invalid).error)
        assertIs<Validated.Invalid>(manager.create("a".repeat(PrintLimits.MAX_PRESET_NAME + 1), slides))
        assertEquals(1, manager.list().size)
    }

    @Test
    fun `renaming to the own name or a different case is allowed, to another presets name is not`() = runTest {
        val a = manager.create("A", slides).valueOrNull()!!
        manager.create("B", slides)
        assertIs<Validated.Valid<*>>(manager.rename(a.id, "A"))
        assertIs<Validated.Valid<*>>(manager.rename(a.id, "a"))
        assertEquals(ValidationError.PresetNameTaken, (manager.rename(a.id, "b") as Validated.Invalid).error)
        assertEquals(ValidationError.PresetNotFound, (manager.rename(999, "Z") as Validated.Invalid).error)
    }

    @Test
    fun `the number of presets is capped`() = runTest {
        repeat(PrintLimits.MAX_PRESETS) { manager.create("P$it", slides) }
        assertEquals(ValidationError.TooManyPresets, (manager.create("one more", slides) as Validated.Invalid).error)
    }

    @Test
    fun `presets survive serialization`() {
        val preset = Preset(3, "Slides", StoredPrintOptions.from(slides), mapOf("ColorModel" to "Gray"))
        val json = Json.encodeToString(Preset.serializer(), preset)
        assertEquals(preset, Json.decodeFromString(Preset.serializer(), json))
    }

    @Test
    fun `stored printers that are no longer known fall back to the default printer`() {
        val stored = StoredPrintOptions(printer = "lab-printer")
        assertEquals("tfppr1", stored.toOptions().printer.name)
        val known = Printers.all + Printers.resolve("lab-printer")
        assertEquals("lab-printer", stored.toOptions(known).printer.name)
    }
}
