package com.yahyafati.poolprint.transport

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/** A fake `lpr` on PATH for the test server: records its arguments and stdin, prints a CUPS-style reply. */
class FakeLpr(val dir: Path = Files.createTempDirectory("fake-lpr")) {
    val argsFile: Path = dir.resolve("args.txt")
    val inputFile: Path = dir.resolve("input.bin")
    private val modeFile: Path = dir.resolve("mode")

    init {
        val script = dir.resolve("lpr")
        Files.writeString(
            script,
            """
            #!/bin/sh
            printf '%s\n' "${'$'}@" > '$argsFile'
            cat > '$inputFile'
            if [ "${'$'}(cat '$modeFile' 2>/dev/null)" = "unknown-printer" ]; then
              echo "lpr: The printer or class does not exist." >&2
              exit 1
            fi
            echo "request id is tfppr1-7 (1 file(s))"
            """.trimIndent() + "\n",
        )
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    fun failWithUnknownPrinter() = Files.writeString(modeFile, "unknown-printer")
    fun args(): List<String> = Files.readAllLines(argsFile)
    fun input(): ByteArray = Files.readAllBytes(inputFile)
}
