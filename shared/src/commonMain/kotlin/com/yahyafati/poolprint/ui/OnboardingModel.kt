package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.app.AppDependencies
import com.yahyafati.poolprint.core.model.PoolUsername
import com.yahyafati.poolprint.core.validation.Validated
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.service.TestConnectionResult
import com.yahyafati.poolprint.service.UserFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { Welcome, Username, Key, Connect, Done }

sealed interface ConnectState {
    data object Idle : ConnectState
    data object Running : ConnectState
    data object Success : ConnectState

    /** First connection: show the fingerprint, and only pin it after an explicit tap. */
    data class AskTrust(val key: PinnedHostKey) : ConnectState
    data class Failed(val failure: UserFailure) : ConnectState
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val usernameInput: String = "",
    val usernameError: ValidationError? = null,
    val publicKey: String? = null,
    val connect: ConnectState = ConnectState.Idle,
)

/**
 * Welcome → username → key → verify server and test connection → done. Never asks for a password
 * (A-03, A-11). Starts at the key step when a username is already saved (for example after "Regenerate key").
 */
class OnboardingModel(
    private val deps: AppDependencies,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = mutableState.asStateFlow()

    init {
        scope.launch {
            val saved = deps.settings.getUsername()
            if (saved != null) {
                mutableState.update { it.copy(usernameInput = saved.value) }
                goToKey()
            }
        }
    }

    fun start() = mutableState.update { it.copy(step = OnboardingStep.Username) }

    fun onUsernameChange(text: String) = mutableState.update { it.copy(usernameInput = text, usernameError = null) }

    fun submitUsername() {
        when (val parsed = PoolUsername.parse(mutableState.value.usernameInput)) {
            is Validated.Invalid -> mutableState.update { it.copy(usernameError = parsed.error) }
            is Validated.Valid -> scope.launch {
                deps.settings.setUsername(parsed.value)
                goToKey()
            }
        }
    }

    private suspend fun goToKey() {
        val line = deps.keys.ensureKey()
        mutableState.update { it.copy(step = OnboardingStep.Key, publicKey = line, connect = ConnectState.Idle) }
    }

    fun continueToConnect() = mutableState.update { it.copy(step = OnboardingStep.Connect, connect = ConnectState.Idle) }

    fun back() = mutableState.update {
        when (it.step) {
            OnboardingStep.Username -> it.copy(step = OnboardingStep.Welcome)
            OnboardingStep.Key -> it.copy(step = OnboardingStep.Username)
            OnboardingStep.Connect -> it.copy(step = OnboardingStep.Key, connect = ConnectState.Idle)
            else -> it
        }
    }

    fun testConnection() {
        if (mutableState.value.connect == ConnectState.Running) return
        mutableState.update { it.copy(connect = ConnectState.Running) }
        scope.launch {
            val next = when (val result = deps.services.testConnection()) {
                TestConnectionResult.Success -> ConnectState.Success
                is TestConnectionResult.NeedsTrust -> ConnectState.AskTrust(result.key)
                is TestConnectionResult.Failed -> ConnectState.Failed(result.failure)
            }
            mutableState.update { it.copy(connect = next) }
        }
    }

    /** The user compared the fingerprint and tapped Trust: pin it, then test again. */
    fun trustHostKey() {
        val ask = mutableState.value.connect as? ConnectState.AskTrust ?: return
        scope.launch {
            deps.services.trust(ask.key)
            testConnection()
        }
    }

    fun declineHostKey() = mutableState.update { it.copy(connect = ConnectState.Idle) }

    fun finish() {
        if (mutableState.value.connect != ConnectState.Success) return
        scope.launch {
            deps.settings.setOnboardingComplete(true)
            mutableState.update { it.copy(step = OnboardingStep.Done) }
        }
    }
}
