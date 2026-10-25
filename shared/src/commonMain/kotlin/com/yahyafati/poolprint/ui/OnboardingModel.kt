package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.app.AppDependencies
import com.yahyafati.poolprint.core.model.PoolUsername
import com.yahyafati.poolprint.core.validation.Validated
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.service.InstallKeyResult
import com.yahyafati.poolprint.service.TestConnectionResult
import com.yahyafati.poolprint.service.UserFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [PasswordSetup] is the optional one-time "add the key for me" step (D-20), between [Key] and [Connect]. */
enum class OnboardingStep { Welcome, Username, Key, PasswordSetup, Connect, Done }

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
    /** The password key install. [ConnectState.Success] is never used: on success the model moves to [OnboardingStep.Connect]. */
    val install: ConnectState = ConnectState.Idle,
)

/**
 * Welcome → username → key → (optionally: install the key with the pool password once) → verify server and test
 * connection → done. The password is only used by the optional step and never stored (A-03, D-20); nothing asks
 * for it after onboarding (A-11). Starts at the key step when a username is already saved (for example after
 * "Regenerate key").
 */
class OnboardingModel(
    private val deps: AppDependencies,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = mutableState.asStateFlow()

    /** Held only while the user is asked to trust the server, so the install can resume. Zeroed on every exit. */
    private var pendingPassword: CharArray? = null

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

    fun back() {
        forgetPassword()
        mutableState.update {
            when (it.step) {
                OnboardingStep.Username -> it.copy(step = OnboardingStep.Welcome)
                OnboardingStep.Key -> it.copy(step = OnboardingStep.Username)
                OnboardingStep.PasswordSetup -> it.copy(step = OnboardingStep.Key, install = ConnectState.Idle)
                OnboardingStep.Connect -> it.copy(step = OnboardingStep.Key, connect = ConnectState.Idle)
                else -> it
            }
        }
    }

    fun usePassword() = mutableState.update { it.copy(step = OnboardingStep.PasswordSetup, install = ConnectState.Idle) }

    /**
     * Installs the key with the pool password, then tests the key login. Takes ownership of [password]: it is
     * zeroed once the attempt is over (or when the user cancels the trust prompt).
     */
    fun installWithPassword(password: CharArray) {
        val current = mutableState.value
        if (current.step != OnboardingStep.PasswordSetup || current.install == ConnectState.Running || password.isEmpty()) {
            password.fill('\u0000')
            return
        }
        forgetPassword()
        mutableState.update { it.copy(install = ConnectState.Running) }
        scope.launch { attemptInstall(password) }
    }

    private suspend fun attemptInstall(password: CharArray) {
        // The service zeroes what it is given; a copy survives only while the user has to trust the server.
        val copy = password.copyOf()
        when (val result = deps.services.installKeyWithPassword(password)) {
            InstallKeyResult.Installed -> {
                copy.fill('\u0000')
                mutableState.update { it.copy(step = OnboardingStep.Connect, install = ConnectState.Idle, connect = ConnectState.Idle) }
                testConnection()
            }
            is InstallKeyResult.NeedsTrust -> {
                pendingPassword = copy
                mutableState.update { it.copy(install = ConnectState.AskTrust(result.key)) }
            }
            is InstallKeyResult.Failed -> {
                copy.fill('\u0000')
                mutableState.update { it.copy(install = ConnectState.Failed(result.failure)) }
            }
        }
    }

    private fun forgetPassword() {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
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

    /** The user compared the fingerprint and tapped Trust: pin it, then retry the test or the password install. */
    fun trustHostKey() {
        val current = mutableState.value
        if (current.step == OnboardingStep.PasswordSetup) {
            val ask = current.install as? ConnectState.AskTrust ?: return
            val password = pendingPassword ?: return
            pendingPassword = null
            mutableState.update { it.copy(install = ConnectState.Running) }
            scope.launch {
                deps.services.trust(ask.key)
                attemptInstall(password)
            }
            return
        }
        val ask = current.connect as? ConnectState.AskTrust ?: return
        scope.launch {
            deps.services.trust(ask.key)
            testConnection()
        }
    }

    fun declineHostKey() {
        forgetPassword()
        mutableState.update { it.copy(connect = ConnectState.Idle, install = ConnectState.Idle) }
    }

    fun finish() {
        if (mutableState.value.connect != ConnectState.Success) return
        scope.launch {
            deps.settings.setOnboardingComplete(true)
            mutableState.update { it.copy(step = OnboardingStep.Done) }
        }
    }
}
