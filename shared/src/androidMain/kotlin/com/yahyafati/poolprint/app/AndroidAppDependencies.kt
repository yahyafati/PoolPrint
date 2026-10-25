package com.yahyafati.poolprint.app

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.yahyafati.poolprint.keys.AndroidKeystorePrivateKeyStore
import java.io.File
import okio.Path.Companion.toPath

private var dataStore: DataStore<Preferences>? = null

@Synchronized
private fun settingsDataStore(context: Context): DataStore<Preferences> =
    dataStore ?: PreferenceDataStoreFactory.createWithPath(
        // Not backed up: after a restore the app starts clean, because the Keystore key doesn't survive one.
        produceFile = { File(context.noBackupFilesDir, "settings.preferences_pb").absolutePath.toPath() },
    ).also { dataStore = it }

fun createAndroidAppDependencies(context: Context): AppDependencies {
    val app = context.applicationContext
    return buildAppDependencies(settingsDataStore(app), AndroidKeystorePrivateKeyStore(app), Build.MODEL.orEmpty())
}
