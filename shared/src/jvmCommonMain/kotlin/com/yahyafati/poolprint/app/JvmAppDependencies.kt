package com.yahyafati.poolprint.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yahyafati.poolprint.keys.BouncyCastleKeyGenerator
import com.yahyafati.poolprint.keys.KeyManager
import com.yahyafati.poolprint.keys.PrivateKeyStore
import com.yahyafati.poolprint.pdf.PdfPageCounter
import com.yahyafati.poolprint.service.DiscoveryService
import com.yahyafati.poolprint.service.PoolServices
import com.yahyafati.poolprint.service.QueueService
import com.yahyafati.poolprint.settings.DataStoreAppSettings
import com.yahyafati.poolprint.settings.DataStoreDiscoveryStore
import com.yahyafati.poolprint.settings.DataStoreHostKeyStore
import com.yahyafati.poolprint.settings.DataStorePresetStore
import com.yahyafati.poolprint.settings.PresetManager
import com.yahyafati.poolprint.transport.SshjClientFactory
import kotlin.time.Clock

/** Wires the real implementations; shared by the Android and desktop entry points. */
fun buildAppDependencies(
    dataStore: DataStore<Preferences>,
    privateKeys: PrivateKeyStore,
    deviceLabel: String,
    pageCounter: PdfPageCounter,
): AppDependencies {
    val settings = DataStoreAppSettings(dataStore)
    val hostKeys = DataStoreHostKeyStore(dataStore)
    val services = PoolServices(settings, hostKeys, SshjClientFactory(privateKeys, hostKeys))
    return AppDependencies(
        settings = settings,
        hostKeys = hostKeys,
        keys = KeyManager(BouncyCastleKeyGenerator(), privateKeys, settings, "pool-print@$deviceLabel"),
        services = services,
        pageCounter = pageCounter,
        discovery = DiscoveryService(services, DataStoreDiscoveryStore(dataStore), clock = { Clock.System.now().toEpochMilliseconds() }),
        presets = PresetManager(DataStorePresetStore(dataStore)),
        queue = QueueService(services, settings),
    )
}
