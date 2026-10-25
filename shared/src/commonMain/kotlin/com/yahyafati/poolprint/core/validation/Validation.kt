package com.yahyafati.poolprint.core.validation

sealed interface ValidationError {
    data object PrinterUnknown : ValidationError
    data object CopiesNotANumber : ValidationError
    data class CopiesOutOfRange(val min: Int, val max: Int) : ValidationError
    data object PageRangesInvalid : ValidationError
    data object PageRangesTooLong : ValidationError
    data object PageRangeReversed : ValidationError
    data class PageRangeBeyondDocument(val lastPage: Int) : ValidationError
    data object OptionUnknown : ValidationError
    data object OptionValueInvalid : ValidationError
    data object JobIdInvalid : ValidationError
    data object JobNotFound : ValidationError
    data object JobNotOwned : ValidationError
    data object PresetNotFound : ValidationError
    data object PresetNameEmpty : ValidationError
    data class PresetNameTooLong(val max: Int) : ValidationError
    data object PresetNameTaken : ValidationError
    data object TooManyPresets : ValidationError
    data object UsernameEmpty : ValidationError
    data object UsernameInvalid : ValidationError
}

sealed interface Validated<out T> {
    data class Valid<T>(val value: T) : Validated<T>
    data class Invalid(val error: ValidationError) : Validated<Nothing>
}

fun <T> Validated<T>.valueOrNull(): T? = (this as? Validated.Valid)?.value
fun <T> Validated<T>.errorOrNull(): ValidationError? = (this as? Validated.Invalid)?.error
