package com.yahyafati.poolprint.app

import com.yahyafati.poolprint.hostkeys.HostKeyStore
import com.yahyafati.poolprint.keys.KeyManager
import com.yahyafati.poolprint.pdf.PdfPageCounter
import com.yahyafati.poolprint.service.PoolServices
import com.yahyafati.poolprint.settings.AppSettings

/** Everything the UI needs, assembled by the platform entry point. */
class AppDependencies(
    val settings: AppSettings,
    val hostKeys: HostKeyStore,
    val keys: KeyManager,
    val services: PoolServices,
    val pageCounter: PdfPageCounter,
)
