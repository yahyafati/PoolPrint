package com.yahyafati.poolprint.ui

import com.yahyafati.poolprint.app.AppDependencies
import com.yahyafati.poolprint.core.model.PoolUsername
import com.yahyafati.poolprint.core.model.PublicKeyLine
import com.yahyafati.poolprint.core.validation.valueOrNull
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.service.AuthorizeResult
import com.yahyafati.poolprint.service.DiscoveryOutcome
import com.yahyafati.poolprint.service.UserFailure
import com.yahyafati.poolprint.transport.PoolServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsConfirm { None, ForgetHostKey, RegenerateKey }

/** What the last manual "Refresh printers and options" did. */
enum class DiscoveryRefresh { None, Running, Updated, Unavailable, NeedsTrust }

/** The "Add another device" form: paste the new device's public key, then authorize it. */
sealed interface AddDevice {
    data object Closed : AddDevice
    data class Form(val input: String = "", val invalid: Boolean = false) : AddDevice
    data object Running : AddDevice
    data class Added(val comment: String) : AddDevice
    data class Failed(val failure: UserFailure) : AddDevice
}

data class SettingsState(
    val addDevice: AddDevice = AddDevice.Closed,
    val username: PoolUsername? = null,
    val publicKey: String? = null,
    val hostKey: PinnedHostKey? = null,
    val confirm: SettingsConfirm = SettingsConfirm.None,
    /** When the printer list was last fetched (epoch millis), or null when it never was. */
    val discoveryUpdatedAt: Long? = null,
    val discoveryPrinterCount: Int = 0,
    val discoveryRefresh: DiscoveryRefresh = DiscoveryRefresh.None,
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
            val cached = deps.discovery.cached()
            mutableState.update { it.copy(discoveryUpdatedAt = cached.printersFetchedAtMillis, discoveryPrinterCount = cached.printers.size) }
        }
    }

    /** Fetches the printer list and the options of the last-used printer now, whatever the cache age. */
    fun refreshDiscovery() {
        if (mutableState.value.discoveryRefresh == DiscoveryRefresh.Running) return
        mutableState.update { it.copy(discoveryRefresh = DiscoveryRefresh.Running) }
        scope.launch {
            val printer = deps.settings.getPrintDefaults(deps.discovery.cached().knownPrinters).printer
            val outcome = deps.discovery.refreshAll(printer)
            val result = when (outcome) {
                is DiscoveryOutcome.Updated -> DiscoveryRefresh.Updated
                is DiscoveryOutcome.Unavailable -> DiscoveryRefresh.Unavailable
                is DiscoveryOutcome.NeedsTrust -> DiscoveryRefresh.NeedsTrust
            }
            refresh()
            mutableState.update { it.copy(discoveryRefresh = result) }
        }
    }

    fun openAddDevice() = mutableState.update { it.copy(addDevice = AddDevice.Form()) }
    fun closeAddDevice() = mutableState.update { it.copy(addDevice = AddDevice.Closed) }

    fun onAddDeviceInput(text: String) = mutableState.update { it.copy(addDevice = AddDevice.Form(text)) }

    /** Validates the pasted key and appends it to `authorized_keys` over this device's own login. */
    fun authorizeDevice() {
        val form = mutableState.value.addDevice as? AddDevice.Form ?: return
        val key = PublicKeyLine.parse(form.input).valueOrNull()
        if (key == null) {
            mutableState.update { it.copy(addDevice = form.copy(invalid = true)) }
            return
        }
        mutableState.update { it.copy(addDevice = AddDevice.Running) }
        scope.launch {
            val next = when (val result = deps.services.authorizeDevice(key)) {
                AuthorizeResult.Added -> AddDevice.Added(key.comment)
                is AuthorizeResult.Failed -> AddDevice.Failed(result.failure)
            }
            mutableState.update { it.copy(addDevice = next) }
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
