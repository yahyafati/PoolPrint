package com.yahyafati.poolprint.desktop

/** The desktop operating systems the app supports (D-25). Anything else is refused at startup. */
enum class DesktopOs {
    MacOs,
    Windows;

    companion object {
        /** The OS this JVM runs on; throws [UnsupportedOsException] on anything but macOS and Windows. */
        fun current(): DesktopOs = detect(System.getProperty("os.name"))

        /** Maps an `os.name` value (`Mac OS X`, `Windows 11`, …) to a [DesktopOs]. [osName] is injectable for tests. */
        fun detect(osName: String?): DesktopOs {
            val name = osName.orEmpty().trim().lowercase()
            return when {
                name.startsWith("mac") -> MacOs
                name.startsWith("windows") -> Windows
                else -> throw UnsupportedOsException(osName)
            }
        }
    }
}

/** Raised at startup on an OS other than macOS and Windows; the message is meant to be shown to the user. */
class UnsupportedOsException(val osName: String?) :
    IllegalStateException("Unsupported OS: \"${osName?.ifBlank { null } ?: "unknown"}\". Pool Print runs on macOS and Windows only.")
