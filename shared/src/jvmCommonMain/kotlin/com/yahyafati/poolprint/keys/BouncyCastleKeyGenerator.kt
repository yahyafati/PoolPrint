package com.yahyafati.poolprint.keys

import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil

/**
 * Ed25519 through BouncyCastle's lightweight API: no JCA provider is involved, so it works the same on
 * Android (API 29+) and desktop (D-01 spike). The private key is OpenSSH format, which sshj loads.
 */
class BouncyCastleKeyGenerator(private val random: SecureRandom = SecureRandom()) : KeyGenerator {
    override fun generate(comment: String): GeneratedKey {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(random))
        val pair = generator.generateKeyPair()
        val privateBlob = OpenSSHPrivateKeyUtil.encodePrivateKey(pair.private)
        val publicBlob = OpenSSHPublicKeyUtil.encodePublicKey(pair.public)
        val mime = Base64.getMimeEncoder(70, "\n".toByteArray())
        val pem = ("-----BEGIN OPENSSH PRIVATE KEY-----\n" + mime.encodeToString(privateBlob) +
            "\n-----END OPENSSH PRIVATE KEY-----\n").toByteArray()
        privateBlob.fill(0)
        // The OpenSSH helpers can't embed a comment; the comment only goes on the public line.
        return GeneratedKey(pem, "ssh-ed25519 ${Base64.getEncoder().encodeToString(publicBlob)} $comment")
    }
}
