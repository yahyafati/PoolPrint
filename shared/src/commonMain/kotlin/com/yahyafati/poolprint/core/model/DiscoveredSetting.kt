package com.yahyafati.poolprint.core.model

import com.yahyafati.poolprint.core.validation.Patterns

/**
 * One discovered printer option the user changed from its default, sent as `-o <key>=<value>`. Only
 * [com.yahyafati.poolprint.core.validation.Validators.discoveredSetting] makes one, after checking the key and
 * value against the cached `lpoptions` output of [printerName]. The builder refuses a setting that belongs
 * to another printer.
 */
data class DiscoveredSetting internal constructor(val printerName: String, val key: String, val value: String) {
    init {
        require(Patterns.Identifier.matches(printerName) && Patterns.Identifier.matches(key) && Patterns.Identifier.matches(value)) {
            "invalid discovered setting"
        }
    }
}
