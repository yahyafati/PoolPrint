package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.app.AppDependencies
import com.yahyafati.poolprint.core.model.PoolUsername
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.transport.PoolServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsConfirm { None, ForgetHostKey, RegenerateKey }

data class SettingsState(
    val username: PoolUsername? = null,
    val publicKey: String? = null,
    val hostKey: PinnedHostKey? = null,
    val confirm: SettingsConfirm = SettingsConfirm.None,
)

class SettingsModel(private val deps: AppDependencies, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        scope.launch {
            mutableState.update {
                it.copy(
                    username = deps.settings.getUsername(),
                    publicKey = deps.settings.getPublicKey(),
                    hostKey = deps.hostKeys.get(PoolServer.HOST, PoolServer.PORT),
                )
            }
        }
    }

    fun ask(confirm: SettingsConfirm) = mutableState.update { it.copy(confirm = confirm) }
    fun cancelConfirm() = ask(SettingsConfirm.None)

    /** Second step of "Forget pinned key": the next connection asks to trust the key again. */
    fun confirmForgetHostKey() {
        scope.launch {
            deps.hostKeys.forget(PoolServer.HOST, PoolServer.PORT)
            mutableState.update { it.copy(confirm = SettingsConfirm.None) }
            refresh()
        }
    }

    /** New key: the user has to add it to `authorized_keys` and test again, so onboarding runs again. */
    fun confirmRegenerateKey() {
        scope.launch {
            deps.keys.regenerate()
            deps.settings.setOnboardingComplete(false)
            mutableState.update { it.copy(confirm = SettingsConfirm.None) }
        }
    }
}
