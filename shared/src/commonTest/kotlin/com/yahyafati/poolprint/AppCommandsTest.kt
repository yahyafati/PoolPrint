package com.yahyafati.poolprint

import com.yahyafati.poolprint.app.AppCommands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppCommandsTest {
    @Test
    fun `print is unavailable until a screen registers it`() {
        assertNull(AppCommands().print.value)
    }

    @Test
    fun `registered print action runs`() {
        val commands = AppCommands()
        var printed = 0
        commands.registerPrint { printed++ }
        assertNotNull(commands.print.value).invoke()
        assertEquals(1, printed)
    }

    @Test
    fun `clearing removes the action`() {
        val commands = AppCommands()
        val clear = commands.registerPrint { }
        clear()
        assertNull(commands.print.value)
    }

    @Test
    fun `an old screen clearing does not remove a newer screen's action`() {
        val commands = AppCommands()
        val clearOld = commands.registerPrint { }
        commands.registerPrint { }
        clearOld()
        assertNotNull(commands.print.value)
    }
}
