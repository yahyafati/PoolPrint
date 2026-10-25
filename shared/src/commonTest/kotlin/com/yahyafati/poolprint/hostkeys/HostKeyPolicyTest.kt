package com.yahyafati.poolprint.hostkeys

import kotlin.test.Test
import kotlin.test.assertEquals

class HostKeyPolicyTest {
    private val a = PinnedHostKey("ssh-ed25519", "SHA256:aaa")
    private val b = PinnedHostKey("ssh-ed25519", "SHA256:bbb")

    @Test
    fun `nothing pinned is unknown`() {
        assertEquals(HostKeyVerdict.Unknown(a), HostKeyPolicy.evaluate(null, a))
    }

    @Test
    fun `the pinned key is trusted`() {
        assertEquals(HostKeyVerdict.Trusted, HostKeyPolicy.evaluate(a, a))
    }

    @Test
    fun `a different fingerprint is a change`() {
        assertEquals(HostKeyVerdict.Changed(a, b), HostKeyPolicy.evaluate(a, b))
    }

    @Test
    fun `the same fingerprint under another key type is a change`() {
        val other = a.copy(type = "ssh-rsa")
        assertEquals(HostKeyVerdict.Changed(a, other), HostKeyPolicy.evaluate(a, other))
    }
}
