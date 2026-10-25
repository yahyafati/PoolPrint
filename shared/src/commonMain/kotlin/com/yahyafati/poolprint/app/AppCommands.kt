package com.yahyafati.poolprint.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lets the host window (the desktop menu bar and its shortcuts) trigger what the current screen can do.
 * The screen registers an action while it can run it and removes it when it can't.
 */
class AppCommands {
    private val printAction = MutableStateFlow<(() -> Unit)?>(null)

    /** Non-null while the print sheet is open and Print would work. */
    val print: StateFlow<(() -> Unit)?> = printAction.asStateFlow()

    /** Registers [action] (or clears it with null) and returns a function that clears it again. */
    fun registerPrint(action: (() -> Unit)?): () -> Unit {
        printAction.value = action
        return { if (printAction.value === action) printAction.value = null }
    }
}
