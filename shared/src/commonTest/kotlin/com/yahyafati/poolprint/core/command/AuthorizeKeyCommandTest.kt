package com.yahyafati.poolprint.core.command

import com.yahyafati.poolprint.core.Payloads
import com.yahyafati.poolprint.core.model.PublicKeyLine
import com.yahyafati.poolprint.core.validation.Validated
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.core.validation.valueOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The key blob here is synthetic: the right shape, not a real key. */
class AuthorizeKeyCommandTest {
    private val blob = "AAAAC3NzaC1lZDI1NTE5AAAA" + "A".repeat(44)
    private val line = "ssh-ed25519 $blob pool-print@Test-Mac"
    private val fixedCommand =
        "umask 077; mkdir -p ~/.ssh; chmod 700 ~/.ssh; cat >> ~/.ssh/authorized_keys; chmod 600 ~/.ssh/authorized_keys"

    private fun parse(raw: String) = PublicKeyLine.parse(raw)

    @Test
    fun `the command is a fixed string and does not depend on the key`() {
        assertEquals(fixedCommand, AuthorizeKeyCommand.build().value)
    }

    @Test
    fun `the key goes in on stdin as one canonical line`() {
        val key = parse("  $line \n").valueOrNull()
        assertNotNull(key)
        assertEquals("\n$line\n", AuthorizeKeyCommand.stdin(key).decodeToString())
    }

    @Test
    fun `a key without a comment gets the default one`() {
        assertEquals("ssh-ed25519 $blob pool-print", parse("ssh-ed25519 $blob").valueOrNull()?.line)
    }

    @Test
    fun `rejects other key types and malformed blobs`() {
        val bad = listOf(
            "ssh-rsa $blob c",
            "ecdsa-sha2-nistp256 $blob c",
            "ssh-ed25519 ${blob.dropLast(1)} c",
            "ssh-ed25519 ${blob}A c",
            "ssh-ed25519 ${"B".repeat(68)} c",
            "ssh-ed25519 ${blob.dropLast(1)}= c",
            "ssh-ed25519 ${blob}c",
            "ssh-ed25519  $blob c",
            "ssh-ed25519\t$blob c",
            "",
            "   ",
        )
        for (raw in bad) {
            assertEquals(Validated.Invalid(ValidationError.PublicKeyInvalid), parse(raw), "accepted: $raw")
        }
    }

    @Test
    fun `rejects every injection payload in the comment`() {
        for (payload in Payloads.all) {
            assertInert("$line$payload")
            assertInert("ssh-ed25519 $blob $payload")
            assertInert("ssh-ed25519 $blob c$payload")
        }
    }

    @Test
    fun `rejects every injection payload around and inside the blob`() {
        for (payload in Payloads.all) {
            assertInert("$payload$line")
            assertInert("ssh-ed25519 ${blob.dropLast(1)}$payload c")
            assertInert("ssh-ed25519$payload$blob c")
        }
    }

    @Test
    fun `rejects a second line and options before the key type`() {
        assertInert("$line\n$line")
        assertInert("$line\nssh-ed25519 $blob other")
        assertInert("command=\"id\" $line")
        assertInert("from=\"*\",command=\"id\" $line")
        assertInert("no-pty $line")
        assertEquals(Validated.Invalid(ValidationError.PublicKeyInvalid), parse("$line\n$line"))
        assertEquals(Validated.Invalid(ValidationError.PublicKeyInvalid), parse("command=\"id\" $line"))
    }

    @Test
    fun `an accepted key never changes the command and stays one line`() {
        val accepted = listOf(line, "$line ", " $line", "ssh-ed25519 $blob a.b_c-d@e", "ssh-ed25519 $blob ${"x".repeat(64)}")
        for (raw in accepted) {
            val key = parse(raw).valueOrNull()
            assertNotNull(key, raw)
            assertEquals(fixedCommand, AuthorizeKeyCommand.build().value)
            val stdin = AuthorizeKeyCommand.stdin(key).decodeToString()
            assertEquals(2, stdin.count { it == '\n' })
            assertTrue(stdin.startsWith("\n") && stdin.endsWith("\n"))
            assertFalse('\r' in stdin || '\u0000' in stdin)
        }
        assertEquals(Validated.Invalid(ValidationError.PublicKeyInvalid), parse("ssh-ed25519 $blob ${"x".repeat(65)}"))
    }

    /** Anything accepted must still be a single harmless line; a trailing line break alone is trimmed away. */
    private fun assertInert(raw: String) {
        when (val result = parse(raw)) {
            is Validated.Invalid -> assertIs<ValidationError.PublicKeyInvalid>(result.error)
            is Validated.Valid -> {
                val canonical = result.value.line
                assertTrue(Regex("^ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA[A-Za-z0-9+/]{44} [A-Za-z0-9._@-]{1,64}$").matches(canonical), "accepted: $raw")
                assertNull(raw.trim().lines().drop(1).firstOrNull(), "accepted multi-line: $raw")
            }
        }
    }
}
