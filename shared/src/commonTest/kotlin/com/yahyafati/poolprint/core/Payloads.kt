package com.yahyafati.poolprint.core

/** Hostile inputs from docs/security.md ("Required injection tests"). */
object Payloads {
    val shell = listOf(
        ";", "&&", "||", "|", "&", "`id`", "$(id)", "\${IFS}", "'", "\"", "\\", "\n", "\r", "\t", "\u0000",
        "#", ">", "<", ">>", "*", "?", "~", "!", "{a,b}", "-o x", "--help", "-P evil", "' ; id ; '",
        "1-4;id", "1-4$(id)", "＄(id)", "é", "😀", " ", "",
    )

    /** Payloads embedded in otherwise plausible values. */
    val embedded = listOf(
        "tfppr1;id", "tfppr1 -o x", "tfppr1\nid", "tfppr1'", "tfppr1\$(id)", "-Pevil", "-tfppr1", "tfppr1`id`",
        "tfppr1|id", "tfppr1&id", "tfppr1>x", "tfppr１",
    )

    val all = shell + embedded
}
