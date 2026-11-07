package com.yahyafati.poolprint.app

import com.yahyafati.poolprint.desktop.DesktopOs
import com.yahyafati.poolprint.keys.MacKeychainPrivateKeyStore
import com.yahyafati.poolprint.keys.WindowsCredentialPrivateKeyStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DesktopPlatformTest {
    @Test
    fun `macOS data folder is under Application Support`() {
        val dir = desktopDataDir(DesktopOs.MacOs, "/Users/ann", env = { null })
        assertEquals(File("/Users/ann", "Library/Application Support/Pool Print"), dir)
    }

    @Test
    fun `Windows data folder is under LOCALAPPDATA`() {
        val dir = desktopDataDir(DesktopOs.Windows, "C:\\Users\\ann", env = { if (it == "LOCALAPPDATA") "C:\\Users\\ann\\AppData\\Local" else null })
        assertEquals(File("C:\\Users\\ann\\AppData\\Local", "Pool Print"), dir)
    }

    @Test
    fun `Windows data folder falls back to the profile when LOCALAPPDATA is missing or blank`() {
        val expected = File(File("C:\\Users\\ann", "AppData/Local").path, "Pool Print")
        assertEquals(expected, desktopDataDir(DesktopOs.Windows, "C:\\Users\\ann", env = { null }))
        assertEquals(expected, desktopDataDir(DesktopOs.Windows, "C:\\Users\\ann", env = { " " }))
    }

    @Test
    fun `macOS data folder ignores LOCALAPPDATA`() {
        val dir = desktopDataDir(DesktopOs.MacOs, "/Users/ann", env = { "/somewhere/else" })
        assertEquals(File("/Users/ann", "Library/Application Support/Pool Print"), dir)
    }

    @Test
    fun `key store is chosen per OS`() {
        assertIs<MacKeychainPrivateKeyStore>(desktopKeyStore(DesktopOs.MacOs))
        assertIs<WindowsCredentialPrivateKeyStore>(desktopKeyStore(DesktopOs.Windows))
    }

    @Test
    fun `host name is the short name, with a per-OS fallback`() {
        assertEquals("ann-laptop", desktopHostName(DesktopOs.MacOs) { "ann-laptop.local" })
        assertEquals("DESKTOP-1A2B", desktopHostName(DesktopOs.Windows) { "DESKTOP-1A2B" })
        assertEquals("mac", desktopHostName(DesktopOs.MacOs) { throw java.net.UnknownHostException() })
        assertEquals("pc", desktopHostName(DesktopOs.Windows) { throw java.net.UnknownHostException() })
        assertEquals("pc", desktopHostName(DesktopOs.Windows) { null })
        assertEquals("pc", desktopHostName(DesktopOs.Windows) { ".local" })
    }
}
