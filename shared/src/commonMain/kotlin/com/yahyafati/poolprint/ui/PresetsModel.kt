package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.app.AppDependencies
import com.yahyafati.poolprint.core.validation.Validated
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.settings.Preset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PresetsState(val presets: List<Preset> = emptyList(), val loaded: Boolean = false)

/** The presets screen: list, rename and delete. Presets are created from the print sheet and applied there. */
class PresetsModel(private val deps: AppDependencies, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(PresetsState())
    val state: StateFlow<PresetsState> = mutableState.asStateFlow()

    init {
        reload()
    }

    private fun reload() {
        scope.launch {
            val presets = runCatching { deps.presets.list() }.getOrDefault(emptyList())
            mutableState.update { PresetsState(presets, loaded = true) }
        }
    }

    /** Returns the validation error (name empty, too long or taken), or null when renamed. */
    suspend fun rename(id: Long, name: String): ValidationError? {
        val result = deps.presets.rename(id, name)
        if (result is Validated.Invalid) return result.error
        reload()
        return null
    }

    fun delete(id: Long) {
        scope.launch {
            deps.presets.delete(id)
            reload()
        }
    }
}
