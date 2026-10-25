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

class PageRangesValidatorTest {
    private fun canonical(raw: String, lastPage: Int? = null) = Validators.pageRanges(raw, lastPage).valueOrNull()?.canonical

    @Test
    fun `accepts single pages, ranges and lists`() {
        assertEquals("3", canonical("3"))
        assertEquals("1-4", canonical("1-4"))
        assertEquals("1-4,7", canonical("1-4,7"))
        assertEquals("5", canonical("5-5"))
    }

    @Test
    fun `strips spaces`() {
        assertEquals("1-4,7", canonical(" 1 - 4 , 7 "))
    }

    @Test
    fun `blank input means all pages`() {
        assertEquals(Validated.Valid(null), Validators.pageRanges(""))
        assertEquals(Validated.Valid(null), Validators.pageRanges("   "))
    }

    @Test
    fun `sorts and merges overlapping, adjacent and unordered ranges`() {
        assertEquals("1-5,7", canonical("7,1-4,3-5"))
        assertEquals("1-4", canonical("1-2,3-4"))
        assertEquals("1-9", canonical("1-9,2,4-5"))
        assertEquals("2,4", canonical("4,2,4"))
    }

    @Test
    fun `rejects bad syntax`() {
        for (raw in listOf("0", "01", "1-", "-4", "1--4", "1,,2", "1,", ",1", "a", "1-4;id", "1e3", "1.5", "+1", "１", "123456", "1-4$(id)", "\t1", "1\n")) {
            assertEquals(ValidationError.PageRangesInvalid, Validators.pageRanges(raw).errorOrNull(), "raw: $raw")
        }
    }

    @Test
    fun `rejects a reversed range`() {
        assertEquals(ValidationError.PageRangeReversed, Validators.pageRanges("5-3").errorOrNull())
    }

    @Test
    fun `rejects input over 200 characters`() {
        assertEquals(ValidationError.PageRangesTooLong, Validators.pageRanges("1,".repeat(101)).errorOrNull())
        assertEquals("1-2,4,6,8,10,12,14,16,18,20", canonical("1,2,4,6,8,10,12,14,16,18,20"))
    }

    @Test
    fun `rejects a page past the document end when the count is known`() {
        assertEquals(ValidationError.PageRangeBeyondDocument(10), Validators.pageRanges("8-11", lastPage = 10).errorOrNull())
        assertEquals("8-10", canonical("8-10", lastPage = 10))
        assertEquals("99999", canonical("99999"))
    }

    @Test
    fun `counts the selected pages within a document`() {
        val ranges = Validators.pageRanges("1-3,8-20").valueOrNull()!!
        assertEquals(6, ranges.countWithin(10))
        assertEquals(3, ranges.countWithin(5))
        assertEquals(0, ranges.countWithin(0))
    }
}
