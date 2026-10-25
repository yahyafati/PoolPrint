package com.yahyafati.poolprint.core.command

import com.yahyafati.poolprint.core.Payloads
import com.yahyafati.poolprint.core.model.PrintJob
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.core.model.PrinterType
import com.yahyafati.poolprint.core.model.Printers
import com.yahyafati.poolprint.core.validation.Patterns
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class LprCommandBuilderTest {
    private fun job(copies: Int = 1, printer: Printer = Printers.default, name: String = "doc.pdf") =
        PrintJob(PrintOptions(printer, copies), name)

    @Test
    fun `builds the single-copy command`() {
        assertEquals("'lpr' '-P' 'tfppr1'", LprCommandBuilder.build(job()).value)
    }

    @Test
    fun `adds the copies flag only when copies is above one`() {
        assertEquals("'lpr' '-P' 'tfppr3' '-#' '2'", LprCommandBuilder.build(job(2, Printers.all[2])).value)
    }

    @Test
    fun `never adds a file argument or a job title`() {
        val value = LprCommandBuilder.build(job(3)).value
        assertFalse("'-T'" in value)
        assertFalse(".pdf" in value)
    }

    @Test
    fun `filename never appears in the command`() {
        for (payload in Payloads.all + "lecture-05.pdf" + "secret thesis.pdf") {
            val value = LprCommandBuilder.build(job(name = payload)).value
            assertEquals("'lpr' '-P' 'tfppr1'", value, "payload: $payload")
        }
    }

    @Test
    fun `a printer with a hostile name can't be constructed`() {
        for (payload in Payloads.all) {
            assertFailsWith<IllegalArgumentException>("payload: $payload") { Printer(payload, PrinterType.BW) }
        }
    }

    @Test
    fun `copies outside the allowed range can't be constructed`() {
        for (copies in listOf(0, -1, 21, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertFailsWith<IllegalArgumentException>("copies: $copies") { PrintOptions(copies = copies) }
        }
    }

    @Test
    fun `the final token check rejects anything outside the safe pattern`() {
        // "#", "--help" and "-tfppr1" are inside the pattern (the builder itself needs `-P` and `-#`);
        // layer 2 keeps them out of printer names, which is covered above.
        for (payload in Payloads.all.filterNot { Patterns.SafeToken.matches(it) }) {
            assertFailsWith<IllegalArgumentException>("payload: $payload") { RemoteCommand.fromTokens(listOf("lpr", payload)) }
        }
    }

    @Test
    fun `test connection command is echo ok`() {
        assertEquals("'echo' 'ok'", TestConnectionCommand.build().value)
    }
}
