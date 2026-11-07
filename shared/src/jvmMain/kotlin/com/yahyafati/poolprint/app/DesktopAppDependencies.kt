package com.yahyafati.poolprint.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.yahyafati.poolprint.desktop.DesktopOs
import com.yahyafati.poolprint.keys.MacKeychainPrivateKeyStore
import com.yahyafati.poolprint.keys.PrivateKeyStore
import com.yahyafati.poolprint.keys.WindowsCredentialPrivateKeyStore
import com.yahyafati.poolprint.pdf.PdfBoxPageCounter
import java.io.File
import okio.Path.Companion.toPath

private const val APP_FOLDER = "Pool Print"

/**
 * Where the app keeps its non-secret state: `~/Library/Application Support/Pool Print` on macOS (D-03),
 * `%LOCALAPPDATA%\Pool Print` on Windows (D-27; not `%APPDATA%`, which roams). Does not create the folder.
 * [userHome] and [env] are injectable for tests.
 */
internal fun desktopDataDir(os: DesktopOs, userHome: String, env: (String) -> String?): File =
    when (os) {
        DesktopOs.MacOs -> File(userHome, "Library/Application Support/$APP_FOLDER")
        DesktopOs.Windows -> {
            val localAppData = env("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: File(userHome, "AppData/Local").path
            File(localAppData, APP_FOLDER)
        }
    }

/** The platform secure store for the private key (Keychain / Credential Manager). Neither touches the OS until used. */
internal fun desktopKeyStore(os: DesktopOs): PrivateKeyStore =
    when (os) {
        DesktopOs.MacOs -> MacKeychainPrivateKeyStore()
        DesktopOs.Windows -> WindowsCredentialPrivateKeyStore()
    }

/** Short host name for the key comment `pool-print@<host>`; [lookup] may return null or throw. */
internal fun desktopHostName(os: DesktopOs, lookup: () -> String?): String {
    val fallback = if (os == DesktopOs.MacOs) "mac" else "pc"
    val name = runCatching(lookup).getOrNull()?.substringBefore('.')
    return if (name.isNullOrBlank()) fallback else name
}

private val dataStore: DataStore<Preferences> by lazy {
    val dir = desktopDataDir(DesktopOs.current(), System.getProperty("user.home"), System::getenv).also { it.mkdirs() }
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { File(dir, "settings.preferences_pb").absolutePath.toPath() },
    )
}

fun createDesktopAppDependencies(os: DesktopOs = DesktopOs.current()): AppDependencies {
    val host = desktopHostName(os) { java.net.InetAddress.getLocalHost().hostName }
    return buildAppDependencies(dataStore, desktopKeyStore(os), host, PdfBoxPageCounter())
}
