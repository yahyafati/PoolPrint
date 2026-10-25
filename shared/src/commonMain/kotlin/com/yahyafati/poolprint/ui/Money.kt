package com.yahyafati.poolprint.ui

/** `60` → `0.60 €`. Money is integer cents everywhere else; this is the only place it is formatted. */
internal fun formatCents(cents: Int): String {
    val abs = if (cents < 0) -cents else cents
    return "${if (cents < 0) "-" else ""}${abs / 100}.${(abs % 100).toString().padStart(2, '0')} €"
}
