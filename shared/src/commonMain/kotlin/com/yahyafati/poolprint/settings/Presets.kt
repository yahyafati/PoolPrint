package com.yahyafati.poolprint.settings

import com.yahyafati.poolprint.core.discovery.DiscoverySnapshot
import com.yahyafati.poolprint.core.model.PrintLimits
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.validation.Validated
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.core.validation.Validators
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * A named option set (D-15): the printer and layout options, plus the discovered options changed from their
 * defaults. Copies and page ranges belong to one job and aren't stored. The [name] is display text; it never
 * reaches a remote command. [discovered] is re-validated against the cached `lpoptions` when the preset is applied.
 */
@Serializable
data class Preset(
    val id: Long,
    val name: String,
    val settings: StoredPrintOptions,
    val discovered: Map<String, String> = emptyMap(),
) {
    /** True when [options] (printer and layout, plus discovered choices) are exactly what this preset applies. */
    fun matches(options: PrintOptions): Boolean {
        val stored = StoredPrintOptions.from(options)
        return stored == settings && options.discovered.associate { it.key to it.value } == discovered
    }
}

interface PresetStore {
    val presets: Flow<List<Preset>>
    suspend fun save(presets: List<Preset>)
}

interface DiscoveryStore {
    suspend fun load(): DiscoverySnapshot
    suspend fun save(snapshot: DiscoverySnapshot)
}

/** Create, rename and delete presets on top of a [PresetStore]. Names are validated and unique ignoring case. */
class PresetManager(private val store: PresetStore) {
    private val lock = Mutex()

    val presets: Flow<List<Preset>> get() = store.presets

    suspend fun list(): List<Preset> = store.presets.first()

    suspend fun create(name: String, options: PrintOptions): Validated<Preset> = lock.withLock {
        val all = list()
        if (all.size >= PrintLimits.MAX_PRESETS) return Validated.Invalid(ValidationError.TooManyPresets)
        val valid = when (val result = Validators.presetName(name, all.map { it.name })) {
            is Validated.Valid -> result.value
            is Validated.Invalid -> return result
        }
        val preset = Preset(
            id = (all.maxOfOrNull { it.id } ?: 0L) + 1,
            name = valid,
            settings = StoredPrintOptions.from(options),
            discovered = options.discovered.associate { it.key to it.value },
        )
        store.save(all + preset)
        Validated.Valid(preset)
    }

    suspend fun rename(id: Long, name: String): Validated<Preset> = lock.withLock {
        val all = list()
        val existing = all.firstOrNull { it.id == id } ?: return Validated.Invalid(ValidationError.PresetNotFound)
        val valid = when (val result = Validators.presetName(name, all.filter { it.id != id }.map { it.name })) {
            is Validated.Valid -> result.value
            is Validated.Invalid -> return result
        }
        val renamed = existing.copy(name = valid)
        store.save(all.map { if (it.id == id) renamed else it })
        Validated.Valid(renamed)
    }

    suspend fun delete(id: Long) = lock.withLock {
        store.save(list().filter { it.id != id })
    }
}
