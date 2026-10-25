package com.yahyafati.poolprint.pdf

import com.yahyafati.poolprint.transport.ByteSource
import java.io.File
import java.io.FileInputStream

class FilePdfSource(val file: File) : PdfDocumentSource {
    override val displayName: String = file.name
    override val sizeBytes: Long? = file.length().takeIf { it >= 0 }

    override fun open(): ByteSource {
        val stream = FileInputStream(file)
        return object : ByteSource {
            override fun read(buffer: ByteArray): Int = stream.read(buffer)
            override fun close() = stream.close()
        }
    }
}
