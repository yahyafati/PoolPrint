package com.yahyafati.poolprint.settings

import com.yahyafati.poolprint.core.model.Duplex
import com.yahyafati.poolprint.core.model.Orientation
import com.yahyafati.poolprint.core.model.PagesPerSheet
import com.yahyafati.poolprint.core.model.PaperSize
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.validation.Validators
import com.yahyafati.poolprint.core.validation.valueOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The remembered print options (D-13): printer, duplex, pages per sheet, orientation, fit-to-page and paper.
 * Copies and page ranges belong to one job, so they start fresh every time. Fields are plain strings and
 * numbers, and [toOptions] re-validates each one, so a stale or tampered value falls back to its default.
 */
@Serializable
data class StoredPrintOptions(
    val printer: String? = null,
    val duplex: String? = null,
    val pagesPerSheet: Int? = null,
    val orientation: String? = null,
    val fitToPage: Boolean? = null,
    val paperSize: String? = null,
) {
    fun toOptions(): PrintOptions {
        val base = PrintOptions()
        return base.copy(
            printer = printer?.let { Validators.printer(it).valueOrNull() } ?: base.printer,
            duplex = Duplex.entries.firstOrNull { it.name == duplex } ?: base.duplex,
            pagesPerSheet = PagesPerSheet.entries.firstOrNull { it.count == pagesPerSheet } ?: base.pagesPerSheet,
            orientation = Orientation.entries.firstOrNull { it.name == orientation } ?: base.orientation,
            fitToPage = fitToPage ?: base.fitToPage,
            paperSize = PaperSize.entries.firstOrNull { it.name == paperSize } ?: base.paperSize,
        )
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun from(options: PrintOptions) = StoredPrintOptions(
            printer = options.printer.name,
            duplex = options.duplex.name,
            pagesPerSheet = options.pagesPerSheet.count,
            orientation = options.orientation.name,
            fitToPage = options.fitToPage,
            paperSize = options.paperSize.name,
        )

        /** Unreadable JSON gives the defaults. */
        fun fromJson(text: String?): PrintOptions =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }?.toOptions() ?: PrintOptions()
    }
}
