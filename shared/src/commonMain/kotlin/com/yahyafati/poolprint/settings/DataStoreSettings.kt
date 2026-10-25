package com.yahyafati.poolprint.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yahyafati.poolprint.core.model.PoolUsername
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.hostkeys.HostKeyStore
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yahyafati.poolprint.core.discovery.DiscoverySnapshot
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class DataStoreAppSettings(private val store: DataStore<Preferences>) : AppSettings {
    override val username: Flow<PoolUsername?> = store.data.map { it[USERNAME]?.let(PoolUsername::fromStored) }
    override val onboardingComplete: Flow<Boolean> = store.data.map { it[ONBOARDED] ?: false }
    override val publicKey: Flow<String?> = store.data.map { it[PUBLIC_KEY] }

    override suspend fun getUsername() = username.first()
    override suspend fun getPublicKey() = publicKey.first()

    override suspend fun setUsername(username: PoolUsername) {
        store.edit { it[USERNAME] = username.value }
    }

    override suspend fun setPublicKey(line: String) {
        store.edit { it[PUBLIC_KEY] = line }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        store.edit { it[ONBOARDED] = complete }
    }

    override suspend fun getPrintDefaults(known: List<Printer>): PrintOptions =
        StoredPrintOptions.fromJson(store.data.first()[PRINT_DEFAULTS], known)

    override suspend fun setPrintDefaults(options: PrintOptions) {
        store.edit { it[PRINT_DEFAULTS] = StoredPrintOptions.from(options).toJson() }
    }

    private companion object {
        val PRINT_DEFAULTS = stringPreferencesKey("print_defaults")
        val USERNAME = stringPreferencesKey("pool_username")
        val PUBLIC_KEY = stringPreferencesKey("public_key")
        val ONBOARDED = booleanPreferencesKey("onboarding_complete")
    }
}

/** Pins are not secret but integrity-relevant: they live in app-private storage with the other settings. */
class DataStoreHostKeyStore(private val store: DataStore<Preferences>) : HostKeyStore {
    override suspend fun get(host: String, port: Int): PinnedHostKey? =
        store.data.first()[keyFor(host, port)]?.let { runCatching { Json.decodeFromString<PinnedHostKey>(it) }.getOrNull() }

    override suspend fun pin(host: String, port: Int, key: PinnedHostKey) {
        store.edit { it[keyFor(host, port)] = Json.encodeToString(PinnedHostKey.serializer(), key) }
    }

    override suspend fun forget(host: String, port: Int) {
        store.edit { it.remove(keyFor(host, port)) }
    }

    private fun keyFor(host: String, port: Int) = stringPreferencesKey("hostkey:$host:$port")
}

/** The discovery cache (M3): one JSON value. Unreadable or tampered data gives an empty snapshot. */
class DataStoreDiscoveryStore(private val store: DataStore<Preferences>) : DiscoveryStore {
    override suspend fun load(): DiscoverySnapshot =
        store.data.first()[KEY]?.let { runCatching { Json.decodeFromString<DiscoverySnapshot>(it) }.getOrNull() }
            ?.sanitized() ?: DiscoverySnapshot()

    override suspend fun save(snapshot: DiscoverySnapshot) {
        store.edit { it[KEY] = Json.encodeToString(DiscoverySnapshot.serializer(), snapshot) }
    }

    private companion object {
        val KEY = stringPreferencesKey("discovery_cache")
    }
}

/** Presets (M3): a JSON list. Entries that fail to decode are lost with the list, which then reads as empty. */
class DataStorePresetStore(private val store: DataStore<Preferences>) : PresetStore {
    override val presets: Flow<List<Preset>> = store.data.map { prefs ->
        prefs[KEY]?.let { runCatching { Json.decodeFromString<List<Preset>>(it) }.getOrNull() }.orEmpty()
    }

    override suspend fun save(presets: List<Preset>) {
        store.edit { it[KEY] = Json.encodeToString(ListSerializer(Preset.serializer()), presets) }
    }

    private companion object {
        val KEY = stringPreferencesKey("presets")
    }
}
