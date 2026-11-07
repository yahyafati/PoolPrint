package com.yahyafati.poolprint.keys

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Store behaviour against an in-memory [CredentialBackend], so it runs on every OS. The real Credential Manager
 * round trip is in [WindowsCredentialRoundTripTest].
 */
class WindowsCredentialPrivateKeyStoreTest {
    private class FakeBackend : CredentialBackend {
        val items = mutableMapOf<String, Pair<String, ByteArray>>()
        override fun read(target: String): ByteArray? = items[target]?.second?.copyOf()
        override fun write(target: String, userName: String, blob: ByteArray) {
            items[target] = userName to blob.copyOf()
        }
        override fun delete(target: String) {
            items.remove(target)
        }
    }

    private val backend = FakeBackend()
    private val store = WindowsCredentialPrivateKeyStore("Pool Print", "unit-test", lazyOf(backend))

    @Test
    fun `CREDENTIALW layout matches wincred h on 64-bit`() {
        // 4+4, 2 pointers, FILETIME (8), size + padding, blob pointer, persist, attribute count, 3 pointers.
        if (com.sun.jna.Native.POINTER_SIZE != 8) return
        assertEquals(80, CredentialW().size())
    }

    @Test
    fun `load returns null when nothing is stored`() = runBlocking {
        assertNull(store.load())
    }

    @Test
    fun `save then load returns the same bytes and save overwrites`() = runBlocking {
        val first = "first".encodeToByteArray()
        store.save(first)
        assertContentEquals(first, store.load())
        val second = ByteArray(WindowsCredentialPrivateKeyStore.MAX_BLOB_BYTES) { it.toByte() }
        store.save(second)
        assertContentEquals(second, store.load())
        assertEquals(1, backend.items.size)
        assertEquals("unit-test", backend.items["Pool Print"]?.first)
    }

    @Test
    fun `delete is idempotent`() = runBlocking {
        store.delete()
        store.save("x".encodeToByteArray())
        store.delete()
        assertNull(store.load())
        store.delete()
    }

    @Test
    fun `refuses a key over the Credential Manager blob limit and keeps the old one`() = runBlocking {
        val old = "old".encodeToByteArray()
        store.save(old)
        val e = assertFailsWith<WindowsCredentialPrivateKeyStore.CredentialTooLargeException> {
            store.save(ByteArray(WindowsCredentialPrivateKeyStore.MAX_BLOB_BYTES + 1))
        }
        assertTrue("2560" in e.message!!)
        assertContentEquals(old, store.load())
    }
}

/**
 * Touches the real Windows Credential Manager, so it only runs when `POOLPRINT_WINCRED_TEST=1` is set on Windows.
 * It uses its own target name and deletes its credential afterwards.
 */
class WindowsCredentialRoundTripTest {
    private val enabled = System.getenv("POOLPRINT_WINCRED_TEST") == "1" && System.getProperty("os.name").startsWith("Windows")

    @Test
    fun `save, load, overwrite and delete round-trip`() = runBlocking {
        if (!enabled) return@runBlocking
        val store = WindowsCredentialPrivateKeyStore(service = "Pool Print (test)", account = "unit-test-item")
        try {
            store.delete()
            assertNull(store.load())
            val first = "-----BEGIN TEST-----\nfirst\n-----END TEST-----\n".encodeToByteArray()
            store.save(first)
            assertContentEquals(first, store.load())
            val second = ByteArray(WindowsCredentialPrivateKeyStore.MAX_BLOB_BYTES) { it.toByte() }
            store.save(second)
            assertContentEquals(second, store.load())
            assertFailsWith<WindowsCredentialPrivateKeyStore.CredentialTooLargeException> {
                store.save(ByteArray(WindowsCredentialPrivateKeyStore.MAX_BLOB_BYTES + 1))
            }
            assertContentEquals(second, store.load())
            store.delete()
            assertNull(store.load())
            store.delete() // idempotent
        } finally {
            store.delete()
        }
    }
}
