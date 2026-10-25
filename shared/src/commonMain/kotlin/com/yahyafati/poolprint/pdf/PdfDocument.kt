package com.yahyafati.poolprint.pdf

import com.yahyafati.poolprint.transport.ByteSource

/**
 * A PDF the user picked or shared. [displayName] is shown in the UI only; it never reaches a remote command.
 * Platform glue builds these from a content URI (Android) or a file (desktop).
 */
interface PdfDocumentSource {
    val displayName: String
    val sizeBytes: Long?

    /** Opens a fresh stream each time, so a retry (for example after trusting the host key) starts at byte 0. */
    fun open(): ByteSource
}
