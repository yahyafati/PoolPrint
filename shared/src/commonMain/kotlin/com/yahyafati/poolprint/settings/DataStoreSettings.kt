package com.yahyafati.poolprint.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yahyafati.poolprint.core.model.PoolUsername
import com.yahyafati.poolprint.hostkeys.HostKeyStore
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    private companion object {
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
