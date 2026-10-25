package com.yahyafati.poolprint.core.command

import kotlin.test.Test
import kotlin.test.assertEquals

class ShellQuoteTest {
    @Test
    fun `wraps a plain token in single quotes`() {
        assertEquals("'lpr'", ShellQuote.quote("lpr"))
    }

    @Test
    fun `turns an embedded single quote into quote-backslash-quote-quote`() {
        assertEquals("'a'\\''b'", ShellQuote.quote("a'b"))
    }

    @Test
    fun `keeps shell metacharacters inside the quotes`() {
        assertEquals("'; id'", ShellQuote.quote("; id"))
        assertEquals("'\$(id)'", ShellQuote.quote("\$(id)"))
    }

    @Test
    fun `quotes the empty string`() {
        assertEquals("''", ShellQuote.quote(""))
    }
}
