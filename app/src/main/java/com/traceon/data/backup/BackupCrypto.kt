package com.traceon.data.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Optional passphrase encryption: PBKDF2-HMAC-SHA256 (150k iterations) -> AES-256-GCM. Format: magic | salt | iv | ciphertext. */
object BackupCrypto {
    private val MAGIC = "TRACEON-ENC1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val ITERATIONS = 150_000

    fun isEncrypted(data: ByteArray): Boolean = data.size > MAGIC.size && MAGIC.indices.all { data[it] == MAGIC[it] }

    fun encrypt(plain: ByteArray, passphrase: String, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray, passphrase: String): ByteArray {
        require(isEncrypted(data) && data.size > MAGIC.size + SALT_LEN + IV_LEN) { "Not an encrypted TRACEON backup." }
        val saltStart = MAGIC.size
        val salt = data.copyOfRange(saltStart, saltStart + SALT_LEN)
        val iv = data.copyOfRange(saltStart + SALT_LEN, saltStart + SALT_LEN + IV_LEN)
        val body = data.copyOfRange(saltStart + SALT_LEN + IV_LEN, data.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
            cipher.doFinal(body)
        } catch (e: GeneralSecurityException) {
            throw IllegalArgumentException("Wrong passphrase, or the backup file is corrupted.")
        }
    }

    private fun key(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
