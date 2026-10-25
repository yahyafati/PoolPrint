package com.yahyafati.poolprint.core.model

import com.yahyafati.poolprint.core.validation.Patterns
import com.yahyafati.poolprint.core.validation.Validated
import com.yahyafati.poolprint.core.validation.ValidationError

/** The pool login. It goes into the SSH connection config only, never into a remote command. */
class PoolUsername private constructor(val value: String) {
    override fun equals(other: Any?) = other is PoolUsername && other.value == value
    override fun hashCode() = value.hashCode()
    override fun toString() = value

    companion object {
        fun parse(raw: String): Validated<PoolUsername> {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty() -> Validated.Invalid(ValidationError.UsernameEmpty)
                !Patterns.PoolUsername.matches(trimmed) -> Validated.Invalid(ValidationError.UsernameInvalid)
                else -> Validated.Valid(PoolUsername(trimmed))
            }
        }

        /** For values read back from our own storage, which were validated before they were saved. */
        fun fromStored(value: String): PoolUsername? = (parse(value) as? Validated.Valid)?.value
    }
}
