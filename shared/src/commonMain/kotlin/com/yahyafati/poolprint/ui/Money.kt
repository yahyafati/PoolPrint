package com.yahyafati.poolprint.ui

/** `60` → `0.60 €`. Money is integer cents everywhere else; this is the only place it is formatted. */
internal fun formatCents(cents: Int): String {
    val abs = if (cents < 0) -cents else cents
    return "${if (cents < 0) "-" else ""}${abs / 100}.${(abs % 100).toString().padStart(2, '0')} €"
}

/** `2026-10-25 14:03` in UTC. Enough to tell how old the cached printers are, without a date-time library. */
internal fun formatTimestamp(epochMillis: Long): String {
    val days = epochMillis.floorDiv(86_400_000L)
    val secondOfDay = epochMillis.mod(86_400_000L) / 1000
    // Civil-from-days (Howard Hinnant), valid for the whole Long range we care about.
    val z = days + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0
    fun two(n: Long) = n.toString().padStart(2, '0')
    return "$year-${two(month)}-${two(day)} ${two(secondOfDay / 3600)}:${two(secondOfDay % 3600 / 60)} UTC"
}
