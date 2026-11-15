package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Android Keystore-backed AES-256-GCM encryption for playlist credentials, pairing tokens,
 * and custom headers, plus salted PBKDF2 hashing for the 4-digit Parental PIN.
 */
object KeystoreCryptoManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "mehditv_master_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val ENCRYPTED_PREFIX = "enc_v1:"

    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val key = getOrCreateSecretKey()
        if (key != null) {
            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val iv = cipher.iv
                val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
                val combined = ByteArray(iv.size + cipherBytes.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
                ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
            } catch (_: Throwable) {
                plainText
            }
        }
        return plainText
    }

    fun decrypt(storedValue: String): String {
        if (storedValue.isEmpty()) return ""
        if (!storedValue.startsWith(ENCRYPTED_PREFIX)) {
            return storedValue
        }
        val key = getOrCreateSecretKey() ?: return ""
        return try {
            val payload = Base64.decode(storedValue.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
            if (payload.size <= IV_LENGTH_BYTES) return ""
            val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
            val cipherBytes = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }

    fun isKeystoreAvailable(): Boolean = getOrCreateSecretKey() != null

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hashPin(pin: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        return try {
            val spec = PBEKeySpec(pin.toCharArray(), salt, 12_000, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded
            Base64.encodeToString(hash, Base64.NO_WRAP)
        } catch (_: Throwable) {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            val hash = digest.digest(pin.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(hash, Base64.NO_WRAP)
        }
    }

    fun verifyPin(pin: String, saltBase64: String, expectedHash: String): Boolean {
        if (saltBase64.isBlank() || expectedHash.isBlank()) return false
        val actual = hashPin(pin, saltBase64)
        return MessageDigest.isEqual(
            actual.toByteArray(StandardCharsets.UTF_8),
            expectedHash.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
