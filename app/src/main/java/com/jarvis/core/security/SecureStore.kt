package com.jarvis.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted vault for secrets (Gemini API key, future provider keys).
 *
 * Design:
 *  - A 256-bit AES key is generated inside the Android Keystore and can never
 *    leave the secure element / TEE.
 *  - Every value is sealed with AES/GCM/NoPadding using a fresh 96-bit IV.
 *  - The ciphertext blob (iv||body) is base64 encoded into a private
 *    SharedPreferences file. Plain text is never written anywhere.
 *
 * If the Keystore is unavailable (rare, but possible on broken OEM builds)
 * every call fails soft: put() returns false and get() returns null, so the
 * app can show "could not store key securely" instead of crashing.
 */
class SecureStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val KEY_GEMINI = "gemini_api_key"

        private const val PREFS_NAME = "jarvis_vault"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "jarvis_vault_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }

    fun put(name: String, value: String): Boolean {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + body.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(body, 0, combined, iv.size, body.size)
            prefs.edit()
                .putString(name, Base64.encodeToString(combined, Base64.NO_WRAP))
                .commit()
        } catch (t: Throwable) {
            false
        }
    }

    fun get(name: String): String? {
        return try {
            val raw = prefs.getString(name, null) ?: return null
            val combined = Base64.decode(raw, Base64.NO_WRAP)
            if (combined.size <= IV_BYTES) return null
            val iv = combined.copyOfRange(0, IV_BYTES)
            val body = combined.copyOfRange(IV_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (t: Throwable) {
            // Key was invalidated (e.g. device lock reset) -> treat as absent.
            null
        }
    }

    fun has(name: String): Boolean = !get(name).isNullOrBlank()

    fun remove(name: String) {
        prefs.edit().remove(name).commit()
    }

    /** Short, safe representation for the UI, e.g. "AIza…Q7Xo". */
    fun maskedPreview(name: String): String? {
        val value = get(name) ?: return null
        if (value.length <= 8) return "••••"
        return value.take(4) + "…" + value.takeLast(4)
    }

    fun keystoreReady(): Boolean = try {
        secretKey()
        true
    } catch (_: Throwable) {
        false
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
