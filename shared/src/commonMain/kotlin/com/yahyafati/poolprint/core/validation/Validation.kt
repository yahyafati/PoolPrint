package com.yahyafati.poolprint.core.validation

sealed interface ValidationError {
    data object PrinterUnknown : ValidationError
    data object CopiesNotANumber : ValidationError
    data class CopiesOutOfRange(val min: Int, val max: Int) : ValidationError
    data object PageRangesInvalid : ValidationError
    data object PageRangesTooLong : ValidationError
    data object PageRangeReversed : ValidationError
    data class PageRangeBeyondDocument(val lastPage: Int) : ValidationError
    data object UsernameEmpty : ValidationError
    data object UsernameInvalid : ValidationError
}

sealed interface Validated<out T> {
    data class Valid<T>(val value: T) : Validated<T>
    data class Invalid(val error: ValidationError) : Validated<Nothing>
}

fun <T> Validated<T>.valueOrNull(): T? = (this as? Validated.Valid)?.value
fun <T> Validated<T>.errorOrNull(): ValidationError? = (this as? Validated.Invalid)?.error
