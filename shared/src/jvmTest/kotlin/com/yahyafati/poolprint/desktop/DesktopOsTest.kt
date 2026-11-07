package com.yahyafati.poolprint.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopOsTest {
    @Test
    fun `detects macOS from os name`() {
        assertEquals(DesktopOs.MacOs, DesktopOs.detect("Mac OS X"))
        assertEquals(DesktopOs.MacOs, DesktopOs.detect("macOS"))
    }

    @Test
    fun `detects Windows from os name`() {
        assertEquals(DesktopOs.Windows, DesktopOs.detect("Windows 11"))
        assertEquals(DesktopOs.Windows, DesktopOs.detect("Windows 10"))
        assertEquals(DesktopOs.Windows, DesktopOs.detect("  windows server 2022 "))
    }

    @Test
    fun `fails clearly on any other OS`() {
        for (name in listOf("Linux", "FreeBSD", "SunOS", "", "   ", null)) {
            val e = assertFailsWith<UnsupportedOsException>("os.name=$name") { DesktopOs.detect(name) }
            assertTrue(e.message!!.startsWith("Unsupported OS"), e.message)
            assertTrue("macOS and Windows" in e.message!!)
        }
        assertTrue("Linux" in assertFailsWith<UnsupportedOsException> { DesktopOs.detect("Linux") }.message!!)
    }

    @Test
    fun `menu shortcuts use meta on macOS and ctrl on Windows`() {
        assertEquals(KeyShortcut(Key.O, meta = true), menuShortcut(DesktopOs.MacOs, Key.O))
        assertEquals(KeyShortcut(Key.P, ctrl = true), menuShortcut(DesktopOs.Windows, Key.P))
    }
}
