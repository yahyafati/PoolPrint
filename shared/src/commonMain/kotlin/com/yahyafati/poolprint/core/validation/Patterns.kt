package com.yahyafati.poolprint.core.validation

/** Allow-list patterns shared by the validators and the command builders (see docs/security.md). */
internal object Patterns {
    /** Printer names and other identifiers. The first character can't be `-`, which prevents option injection. */
    val Identifier = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}$")

    /** Final check on every token that reaches the shell, before quoting. */
    val SafeToken = Regex("^[A-Za-z0-9_.,=#:-]+$")

    val PoolUsername = Regex("^[a-z0-9_][a-z0-9_.-]{0,31}$")
}
