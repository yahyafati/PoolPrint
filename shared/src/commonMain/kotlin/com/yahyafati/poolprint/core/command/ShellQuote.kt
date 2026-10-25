package com.yahyafati.poolprint.core.command

object ShellQuote {
    /** POSIX single-quoting: wrap in `'…'`, and turn every embedded `'` into `'\''`. */
    fun quote(token: String): String = "'" + token.replace("'", "'\\''") + "'"
}
