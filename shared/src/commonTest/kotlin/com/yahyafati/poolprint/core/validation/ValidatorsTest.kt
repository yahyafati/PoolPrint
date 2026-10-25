package com.yahyafati.poolprint.core.validation

import com.yahyafati.poolprint.core.Payloads
import com.yahyafati.poolprint.core.model.PoolUsername
import com.yahyafati.poolprint.core.model.Printers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ValidatorsTest {
    @Test
    fun `accepts every known printer`() {
        for (printer in Printers.all) {
            assertEquals(printer, Validators.printer(printer.name).valueOrNull())
        }
    }

    @Test
    fun `rejects an unknown but well-formed printer`() {
        assertEquals(ValidationError.PrinterUnknown, Validators.printer("tfppr9").errorOrNull())
    }

    @Test
    fun `rejects every injection payload as a printer`() {
        for (payload in Payloads.all) {
            assertNull(Validators.printer(payload).valueOrNull(), "payload: $payload")
        }
    }

    @Test
    fun `rejects a printer that matches the pattern but is not in the known list`() {
        assertNull(Validators.printer("evil", known = Printers.all).valueOrNull())
    }

    @Test
    fun `accepts copies from one to twenty`() {
        assertEquals(1, Validators.copies("1").valueOrNull())
        assertEquals(20, Validators.copies("20").valueOrNull())
    }

    @Test
    fun `rejects copies out of range`() {
        for (raw in listOf("0", "21", "99", "100")) {
            assertIs<ValidationError.CopiesOutOfRange>(Validators.copies(raw).errorOrNull(), raw)
        }
    }

    @Test
    fun `rejects copies that are not plain digits`() {
        val rejected = listOf("", " ", "2 -o x", "1e3", "+2", "-1", "1.5", "0x10", "２", "1\n", "12345", "٣") + Payloads.all
        for (raw in rejected) {
            assertNull(Validators.copies(raw).valueOrNull(), "raw: $raw")
        }
    }

    @Test
    fun `accepts a normal pool username and trims it`() {
        assertEquals("jdoe", PoolUsername.parse("  jdoe ").valueOrNull()?.value)
    }

    @Test
    fun `rejects usernames that are empty or contain unexpected characters`() {
        assertEquals(ValidationError.UsernameEmpty, PoolUsername.parse("  ").errorOrNull())
        for (payload in Payloads.shell.filter { it.isNotBlank() } + listOf("JDoe", "-jdoe", "a".repeat(33), "j doe")) {
            assertNull(PoolUsername.parse(payload).valueOrNull(), "payload: $payload")
        }
    }
}
