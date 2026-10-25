package com.yahyafati.poolprint.service

import com.yahyafati.poolprint.fakes.FakeKeyGenerator
import com.yahyafati.poolprint.fakes.FakeKeyStore
import com.yahyafati.poolprint.fakes.FakeSettings
import com.yahyafati.poolprint.keys.KeyManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class KeyManagerTest {
    private val generator = FakeKeyGenerator()
    private val store = FakeKeyStore()
    private val settings = FakeSettings()
    private val manager = KeyManager(generator, store, settings, "pool-print@test")

    @Test
    fun `first call generates and stores a key`() = runTest {
        val line = manager.ensureKey()
        assertEquals("ssh-ed25519 AAAAKEY1 pool-print@test", line)
        assertNotNull(store.stored)
        assertEquals(line, settings.getPublicKey())
    }

    @Test
    fun `ensureKey is idempotent while the key exists`() = runTest {
        val first = manager.ensureKey()
        assertEquals(first, manager.ensureKey())
        assertEquals(1, generator.counter)
    }

    @Test
    fun `a missing private key is regenerated even if a public key is recorded`() = runTest {
        manager.ensureKey()
        store.delete()
        assertNotEquals("ssh-ed25519 AAAAKEY1 pool-print@test", manager.ensureKey())
        assertEquals(2, generator.counter)
    }

    @Test
    fun `regenerate replaces both halves`() = runTest {
        manager.ensureKey()
        val second = manager.regenerate()
        assertEquals("ssh-ed25519 AAAAKEY2 pool-print@test", second)
        assertEquals("PRIVATE-2", store.stored?.decodeToString())
    }

    @Test
    fun `the comment can't break out of the single-quoted authorized_keys line`() = runTest {
        val hostile = KeyManager(generator, store, settings, "Pixel 8'; rm -rf ~; echo '")
        val line = hostile.ensureKey()
        assertEquals(1, line.count { it == ' ' } - 1)  // type, blob, and a comment without spaces
        assertEquals(false, "'" in line || ";" in line || "~" in line)
    }
}
