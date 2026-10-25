package com.yahyafati.poolprint.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yahyafati.poolprint.keys.BouncyCastleKeyGenerator
import com.yahyafati.poolprint.keys.KeyManager
import com.yahyafati.poolprint.keys.PrivateKeyStore
import com.yahyafati.poolprint.pdf.PdfPageCounter
import com.yahyafati.poolprint.service.PoolServices
import com.yahyafati.poolprint.settings.DataStoreAppSettings
import com.yahyafati.poolprint.settings.DataStoreHostKeyStore
import com.yahyafati.poolprint.transport.SshjClientFactory

/** Wires the real implementations; shared by the Android and desktop entry points. */
fun buildAppDependencies(
    dataStore: DataStore<Preferences>,
    privateKeys: PrivateKeyStore,
    deviceLabel: String,
    pageCounter: PdfPageCounter,
): AppDependencies {
    val settings = DataStoreAppSettings(dataStore)
    val hostKeys = DataStoreHostKeyStore(dataStore)
    return AppDependencies(
        settings = settings,
        hostKeys = hostKeys,
        keys = KeyManager(BouncyCastleKeyGenerator(), privateKeys, settings, "pool-print@$deviceLabel"),
        services = PoolServices(settings, hostKeys, SshjClientFactory(privateKeys, hostKeys)),
        pageCounter = pageCounter,
    )
}
