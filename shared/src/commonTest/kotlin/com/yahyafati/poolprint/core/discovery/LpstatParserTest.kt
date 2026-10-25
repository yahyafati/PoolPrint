package com.yahyafati.poolprint.core.discovery

import com.yahyafati.poolprint.core.Payloads
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LpstatParserTest {
    @Test
    fun `parses printers, states and the reason line`() {
        val printers = LpstatParser.parse(Fixtures.lpstat)
        assertEquals(listOf("tfppr1", "tfppr2", "tfppr3", "tfppr4", "extra-lab"), printers.map { it.name })
        assertEquals(
            listOf(PrinterState.IDLE, PrinterState.PRINTING, PrinterState.DISABLED, PrinterState.IDLE, PrinterState.IDLE),
            printers.map { it.state },
        )
        assertEquals("Paper jam", printers[2].reason)
        assertEquals(null, printers[0].reason)
    }

    @Test
    fun `an unrecognised state is unknown`() {
        assertEquals(PrinterState.UNKNOWN, LpstatParser.parse("printer tfppr1 is processing.").single().state)
    }

    @Test
    fun `drops names that fail the identifier pattern instead of escaping them`() {
        val text = Payloads.all.joinToString("\n") { "printer $it is idle." } + "\nprinter -evil is idle.\nprinter tfppr9 is idle."
        val names = LpstatParser.parse(text).map { it.name }
        // A payload with a space only contributes its first word (a plain, valid name); nothing else survives.
        val pattern = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}$")
        assertTrue(names.all { pattern.matches(it) }, "names: $names")
        assertTrue("-evil" !in names && "tfppr1;id" !in names && "tfppr1`id`" !in names && "tfppr1|id" !in names)
        assertTrue("tfppr9" in names)
    }

    @Test
    fun `localised or garbage output gives an empty list and never throws`() {
        assertEquals(emptyList(), LpstatParser.parse("Drucker tfppr1 ist im Leerlauf."))
        assertEquals(emptyList(), LpstatParser.parse(""))
        assertEquals(emptyList(), LpstatParser.parse("\u0000\u0001 garbage \n\n\t\t"))
    }

    @Test
    fun `lists each printer once`() {
        assertEquals(1, LpstatParser.parse("printer a is idle.\nprinter a is idle.").size)
    }

    @Test
    fun `a huge listing is capped`() {
        val text = (1..500).joinToString("\n") { "printer p$it is idle." }
        assertTrue(LpstatParser.parse(text).size <= 100)
    }
}
