package com.yahyafati.poolprint.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FingerprintTest {
    @Test
    fun `groups a fingerprint in fours under its prefix`() {
        assertEquals(
            "SHA256:\np7Xq 4nLe 9bVd Rk2T\nwHs0 J8yC fMa1 Q6uZ\noE3g Ni5r YtKc",
            groupFingerprint("SHA256:p7Xq4nLe9bVdRk2TwHs0J8yCfMa1Q6uZoE3gNi5rYtKc"),
        )
    }

    @Test
    fun `grouping keeps every character, so nothing is hidden from the comparison`() {
        val fingerprint = "SHA256:abcdefghijklmnopqrstuvwxyz0123456789+/ABCDEFG"
        assertEquals(fingerprint.substringAfter(':'), groupFingerprint(fingerprint).substringAfter(':').filterNot { it == ' ' || it == '\n' })
    }

    @Test
    fun `a fingerprint without a prefix is only grouped`() {
        assertEquals("abcd efgh", groupFingerprint("abcdefgh"))
    }
}
