package com.yahyafati.poolprint.settings

import com.yahyafati.poolprint.core.model.PoolUsername
import com.yahyafati.poolprint.core.model.PrintOptions
import com.yahyafati.poolprint.core.model.Printer
import com.yahyafati.poolprint.core.model.Printers
import kotlinx.coroutines.flow.Flow

/** Non-secret app state (D-03). The private key never goes here. */
interface AppSettings {
    val username: Flow<PoolUsername?>
    val onboardingComplete: Flow<Boolean>
    val publicKey: Flow<String?>

    suspend fun getUsername(): PoolUsername?
    suspend fun getPublicKey(): String?
    suspend fun setUsername(username: PoolUsername)
    suspend fun setPublicKey(line: String)
    suspend fun setOnboardingComplete(complete: Boolean)

    /** The last-used options (printer and layout, not copies or page ranges): defaults when nothing is saved. */
    suspend fun getPrintDefaults(known: List<Printer> = Printers.all): PrintOptions
    suspend fun setPrintDefaults(options: PrintOptions)
}
