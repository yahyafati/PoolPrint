package com.yahyafati.poolprint.keys

/** [privateKeyPem] is an OpenSSH-format private key. Treat it as a secret: never log it, zero it after use. */
class GeneratedKey(val privateKeyPem: ByteArray, val publicKeyLine: String)

interface KeyGenerator {
    /** Generates an Ed25519 key. The public line is `ssh-ed25519 AAAA… <comment>`. */
    fun generate(comment: String): GeneratedKey
}

/** The platform secure store: Android Keystore-wrapped file, macOS Keychain, Windows Credential Manager. Loading never prompts (A-11). */
interface PrivateKeyStore {
    suspend fun load(): ByteArray?
    suspend fun save(pem: ByteArray)
    suspend fun delete()
}

/**
 * The comment ends up in a line the user pastes into `echo '…' >> authorized_keys`, so it may only contain
 * characters that are harmless inside single quotes.
 */
internal fun safeComment(raw: String): String =
    raw.map { if (it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' || it == '@') it else '-' }
        .joinToString("").take(64).ifEmpty { "pool-print" }

/** Creates, stores and regenerates the device key. */
class KeyManager(
    private val generator: KeyGenerator,
    private val store: PrivateKeyStore,
    private val settings: com.yahyafati.poolprint.settings.AppSettings,
    private val comment: String,
) {
    /** The public key line, generating and storing a key first if there is none. */
    suspend fun ensureKey(): String {
        val existing = settings.getPublicKey()
        if (existing != null && store.load()?.also { it.fill(0) } != null) return existing
        return regenerate()
    }

    /** Replaces the key. The old public key line must be removed from `authorized_keys` by the user. */
    suspend fun regenerate(): String {
        val key = generator.generate(safeComment(comment))
        try {
            store.save(key.privateKeyPem)
        } finally {
            key.privateKeyPem.fill(0)
        }
        settings.setPublicKey(key.publicKeyLine)
        return key.publicKeyLine
    }
}
