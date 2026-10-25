package com.yahyafati.poolprint.core.discovery

import com.yahyafati.poolprint.core.Payloads
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LpoptionsParserTest {
    private fun parse(text: String) = LpoptionsParser.parse(text)

    @Test
    fun `parses keys, labels, values and defaults, and hides the standard controls`() {
        val result = parse(Fixtures.lpoptions)
        // PageSize and Duplex collide with the standard controls.
        assertEquals(listOf("ColorModel", "HPEconoMode", "Resolution"), result.options.map { it.key })
        assertEquals(5, result.recognizedLines)
        val color = result.options.first()
        assertEquals("Color Mode", color.label)
        assertEquals(listOf("Gray", "RGB"), color.values)
        assertEquals("RGB", color.default)
        assertEquals("600dpi", result.options.last().default)
    }

    @Test
    fun `drops Custom values and keeps the rest`() {
        val option = parse("Foo/Foo: *A Custom.10x10 B").options.single()
        assertEquals(listOf("A", "B"), option.values)
    }

    @Test
    fun `an option whose values are all dropped disappears`() {
        assertTrue(parse("Foo/Foo: *Custom.WIDTHxHEIGHT").options.isEmpty())
    }

    @Test
    fun `a dropped default leaves no default`() {
        assertNull(parse("Foo/Foo: *Custom.X A B").options.single().default)
    }

    @Test
    fun `collisions are hidden regardless of case`() {
        val keys = listOf("PageSize", "PageRegion", "media", "Media", "Duplex", "sides", "number-up", "page-ranges", "copies", "orientation-requested", "landscape", "fit-to-page")
        val text = keys.joinToString("\n") { "$it/Label: *a b" }
        val result = parse(text)
        assertTrue(result.options.isEmpty())
        assertEquals(keys.size, result.recognizedLines)
    }

    @Test
    fun `keys and values that fail the pattern are dropped`() {
        for (payload in Payloads.all) {
            val text = "Key${payload.replace("/", "")}/Label: *a b\nGood/Label: *ok ${payload.replace(" ", "")} fine"
            for (option in parse(text).options) {
                assertTrue(Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}$").matches(option.key), "key ${option.key}")
                assertTrue(option.values.all { Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}$").matches(it) }, "values ${option.values}")
            }
        }
        assertEquals(listOf("ok", "fine"), parse("Good/Label: *ok \$(id) ; fine").options.single().values)
        assertTrue(parse("Bad\$(id)/Label: *a b").options.isEmpty())
    }

    @Test
    fun `unparseable lines are skipped and the parser never throws`() {
        val result = parse("garbage\n\n: : :\n/x: y\nGood/Label: *a b\n\u0000")
        assertEquals(listOf("Good"), result.options.map { it.key })
        assertEquals(0, parse("not lpoptions output").recognizedLines)
        assertEquals(0, parse("").recognizedLines)
    }

    @Test
    fun `labels lose control characters and are capped`() {
        val label = parse("Foo/Fo\u0007o ${"x".repeat(200)}: *a b").options.single().label
        assertTrue('\u0007' !in label && label.length <= 80)
        assertEquals("Foo", parse("Foo/: *a b").options.single().label)
    }

    @Test
    fun `a single value option is parsed but offers no choice`() {
        val option = parse("OnlyOne/Only: *x").options.single()
        assertTrue(!option.isChoosable)
    }

    @Test
    fun `duplicate keys keep the first`() {
        assertEquals(listOf("a", "b"), parse("K/L: *a b\nK/L: *c d").options.single().values)
    }
}
