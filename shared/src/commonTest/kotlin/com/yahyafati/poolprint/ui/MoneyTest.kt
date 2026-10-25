package com.yahyafati.poolprint.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyTest {
    @Test
    fun `formats cents with two decimals and a euro suffix`() {
        assertEquals("0.00 €", formatCents(0))
        assertEquals("0.05 €", formatCents(5))
        assertEquals("0.60 €", formatCents(60))
        assertEquals("2.05 €", formatCents(205))
        assertEquals("12.00 €", formatCents(1200))
    }
}
