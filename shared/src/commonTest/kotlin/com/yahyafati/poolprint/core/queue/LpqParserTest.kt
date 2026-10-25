package com.yahyafati.poolprint.core.queue

import com.yahyafati.poolprint.core.discovery.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LpqParserTest {
    @Test
    fun `parses rows after the header`() {
        val queue = assertNotNull(LpqParser.parse("tfppr1", Fixtures.lpqJobs))
        assertEquals("tfppr1 is ready and printing", queue.statusLine)
        assertEquals(listOf("1234", "1235", "1236"), queue.entries.map { it.jobId })
        assertEquals(listOf("active", "1st", "2nd"), queue.entries.map { it.rank })
        assertEquals("slides final.pdf", queue.entries[1].fileLabel)
        assertEquals("(stdin)", queue.entries[0].fileLabel)
        assertEquals(482304L, queue.entries[0].sizeBytes)
    }

    @Test
    fun `only the users own entries count as own`() {
        val queue = assertNotNull(LpqParser.parse("tfppr1", Fixtures.lpqJobs))
        assertEquals(listOf("1234", "1236"), queue.ownEntries("jdoe").map { it.jobId })
        assertEquals(emptyList(), queue.ownEntries("nobody"))
    }

    @Test
    fun `no entries gives an empty queue`() {
        val queue = assertNotNull(LpqParser.parse("tfppr1", Fixtures.lpqEmpty))
        assertEquals("tfppr1 is ready", queue.statusLine)
        assertEquals(emptyList(), queue.entries)
    }

    @Test
    fun `text that is not lpq output is rejected`() {
        assertNull(LpqParser.parse("tfppr1", ""))
        assertNull(LpqParser.parse("tfppr1", "lpq: Unknown destination \"tfppr1\"."))
        assertNull(LpqParser.parse("tfppr1", "\u0000\u0001"))
    }

    @Test
    fun `rows that do not fit the shape are skipped, including hostile job ids`() {
        val text = """
            tfppr1 is ready
            Rank Owner Job File(s) Total Size
            1st jdoe 12;id x 10 bytes
            2nd jdoe 99 ok.pdf 10 bytes
            3rd jdoe ${'$'}(id) y 10 bytes
            4th jdoe 1234567890123 z 10 bytes
        """.trimIndent()
        assertEquals(listOf("99"), assertNotNull(LpqParser.parse("tfppr1", text)).entries.map { it.jobId })
    }
}
