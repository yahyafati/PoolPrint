package com.yahyafati.poolprint.core.command

import com.yahyafati.poolprint.core.Payloads
import java.io.File
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** docs/security.md: for arbitrary strings, `sh -c "printf %s <quoted>"` must print the original string. */
class ShellQuoteRoundTripTest {
    private val sh = File("/bin/sh")

    private fun roundTrip(text: String): String {
        val process = ProcessBuilder("/bin/sh", "-c", "printf %s ${ShellQuote.quote(text)}").redirectErrorStream(true).start()
        val out = process.inputStream.readBytes().toString(Charsets.UTF_8)
        process.waitFor()
        return out
    }

    @Test
    fun `hostile payloads survive a real shell unchanged`() {
        if (!sh.exists()) return
        // NUL can't be part of a process argument at all, so it is covered by the token check instead.
        for (payload in Payloads.all.filter { '\u0000' !in it }) {
            assertEquals(payload, roundTrip(payload), "payload: ${payload.replace("\n", "\\n")}")
        }
    }

    @Test
    fun `random strings survive a real shell unchanged`() {
        if (!sh.exists()) return
        val alphabet = "ab '\"\\\$`;&|<>(){}[]*?~!#\n\t é".toCharArray()
        val random = Random(42)
        repeat(200) {
            val text = String(CharArray(random.nextInt(24)) { alphabet[random.nextInt(alphabet.size)] })
            assertEquals(text, roundTrip(text), "text: ${text.replace("\n", "\\n")}")
        }
    }
}
