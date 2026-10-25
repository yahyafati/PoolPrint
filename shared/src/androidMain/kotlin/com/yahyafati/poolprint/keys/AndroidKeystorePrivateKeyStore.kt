package com.yahyafati.poolprint.keys

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encrypts the private key with an AES-256-GCM key that lives in AndroidKeyStore (non-exportable) and keeps
 * `iv + ciphertext` in app-private storage that is excluded from backups. The Keystore key does **not**
 * require user authentication, so using it never prompts (A-11). See docs/security.md.
 */
class AndroidKeystorePrivateKeyStore(
    context: Context,
    private val alias: String = "pool_print_ssh_key_wrap",
    fileName: String = "ssh_key.enc",
) : PrivateKeyStore {
    // noBackupFilesDir is never part of Auto Backup or device-to-device transfer.
    private val file = File(context.noBackupFilesDir, fileName)

    override suspend fun load(): ByteArray? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            val blob = file.readBytes()
            if (blob.size <= IV_BYTES) return@withContext null
            val key = existingKey() ?: return@withContext null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
            cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
        } catch (_: GeneralSecurityException) {
            // The Keystore key is gone (for example after a restore or a lock-screen reset): the key is unusable.
            null
        }
    }

    override suspend fun save(pem: ByteArray) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
        val encrypted = cipher.doFinal(pem)
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(cipher.iv + encrypted)
        check(temp.renameTo(file)) { "could not store the key file" }
    }

    override suspend fun delete() = withContext(Dispatchers.IO) {
        file.delete()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(alias, null) as? SecretKey
    }

    private fun createKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
