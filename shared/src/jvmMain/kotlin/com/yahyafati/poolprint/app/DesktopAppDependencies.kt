package com.yahyafati.poolprint.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.yahyafati.poolprint.keys.MacKeychainPrivateKeyStore
import com.yahyafati.poolprint.pdf.PdfBoxPageCounter
import java.io.File
import okio.Path.Companion.toPath

private fun appSupportDir(): File {
    val home = System.getProperty("user.home")
    val dir = File(home, "Library/Application Support/Pool Print")
    dir.mkdirs()
    return dir
}

private val dataStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { File(appSupportDir(), "settings.preferences_pb").absolutePath.toPath() },
    )
}

fun createDesktopAppDependencies(): AppDependencies {
    val host = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("mac").substringBefore('.')
    return buildAppDependencies(dataStore, MacKeychainPrivateKeyStore(), host, PdfBoxPageCounter())
}
