package com.yahyafati.poolprint.keys

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/**
 * Touches the real login keychain, so it only runs when `POOLPRINT_KEYCHAIN_TEST=1` is set on a Mac.
 * It uses its own service name and deletes its item afterwards.
 */
class MacKeychainPrivateKeyStoreTest {
    private val enabled = System.getenv("POOLPRINT_KEYCHAIN_TEST") == "1" && System.getProperty("os.name").startsWith("Mac")

    @Test
    fun `save, load, overwrite and delete round-trip`() = runBlocking {
        if (!enabled) return@runBlocking
        val store = MacKeychainPrivateKeyStore(service = "Pool Print (test)", account = "unit-test-item")
        try {
            store.delete()
            assertNull(store.load())
            val first = "-----BEGIN TEST-----\nfirst\n-----END TEST-----\n".encodeToByteArray()
            store.save(first)
            assertContentEquals(first, store.load())
            val second = ByteArray(3000) { it.toByte() }
            store.save(second)
            assertContentEquals(second, store.load())
            store.delete()
            assertNull(store.load())
        } finally {
            store.delete()
        }
    }
}
