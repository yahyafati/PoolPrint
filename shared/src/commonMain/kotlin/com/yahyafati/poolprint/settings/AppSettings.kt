package com.yahyafati.poolprint.settings

import com.yahyafati.poolprint.core.model.PoolUsername
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
}
