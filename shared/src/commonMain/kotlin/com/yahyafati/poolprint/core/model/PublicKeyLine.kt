package com.yahyafati.poolprint.core.model

import com.yahyafati.poolprint.core.validation.Patterns
import com.yahyafati.poolprint.core.validation.Validated
import com.yahyafati.poolprint.core.validation.ValidationError

/**
 * A public key another device wants authorized: `ssh-ed25519 AAAA… <comment>`. It is rebuilt from the validated
 * parts, so [line] is always a single canonical line. It travels to the server on stdin, never in a command.
 */
class PublicKeyLine private constructor(val blob: String, val comment: String) {
    val line: String get() = "ssh-ed25519 $blob $comment"

    override fun equals(other: Any?) = other is PublicKeyLine && other.line == line
    override fun hashCode() = line.hashCode()
    override fun toString() = line

    companion object {
        const val DEFAULT_COMMENT = "pool-print"

        fun parse(raw: String): Validated<PublicKeyLine> {
            val match = Patterns.Ed25519KeyLine.matchEntire(raw.trim())
                ?: return Validated.Invalid(ValidationError.PublicKeyInvalid)
            val comment = match.groupValues[2].ifEmpty { DEFAULT_COMMENT }
            return Validated.Valid(PublicKeyLine(match.groupValues[1], comment))
        }
    }
}
