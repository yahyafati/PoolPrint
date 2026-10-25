package com.yahyafati.poolprint.core.validation

/** Allow-list patterns shared by the validators and the command builders (see docs/security.md). */
internal object Patterns {
    /** Printer names and other identifiers. The first character can't be `-`, which prevents option injection. */
    val Identifier = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}$")

    /** Final check on every token that reaches the shell, before quoting. */
    val SafeToken = Regex("^[A-Za-z0-9_.,=#:-]+$")

    /** One page range: `7` or `2-9`. Pages start at 1 and have at most five digits. */
    val PageRange = Regex("^([1-9][0-9]{0,4})(?:-([1-9][0-9]{0,4}))?$")

    /** A CUPS job id from `lpq`: digits only. */
    val JobId = Regex("^[0-9]{1,9}$")

    val PoolUsername = Regex("^[a-z0-9_][a-z0-9_.-]{0,31}$")

    /**
     * One `authorized_keys` line for an Ed25519 key: the type, the 68-character base64 blob (51 bytes, no padding,
     * starting with the encoded type name) and an optional short comment. Nothing else is accepted.
     */
    val Ed25519KeyLine = Regex("^ssh-ed25519 (AAAAC3NzaC1lZDI1NTE5AAAA[A-Za-z0-9+/]{44})(?: ([A-Za-z0-9._@-]{1,64}))?$")
}
