package app.netguard.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CredentialVault — hardware-backed encrypted credential storage.
 *
 * Uses androidx.security.crypto.EncryptedSharedPreferences (AES-256)
 * with Android Keystore backing for additional AES-256-GCM encryption.
 */
@Singleton
class CredentialVault @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun store(key: String, value: String) {
        prefs.edit().putString(sanitizeKey(key), value).apply()
    }

    fun retrieve(key: String): String? = prefs.getString(sanitizeKey(key), null)

    fun delete(key: String) {
        prefs.edit().remove(sanitizeKey(key)).apply()
    }

    fun exists(key: String): Boolean = prefs.contains(sanitizeKey(key))

    fun wipeAll() {
        prefs.edit().clear().apply()
        Timber.w("All credentials wiped")
    }

    /**
     * Encrypt arbitrary bytes using AES-256-GCM with Android Keystore key.
     * Returns Base64(IV + ciphertext).
     */
    fun encrypt(plaintext: ByteArray): String {
        val key = getOrCreateAesKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): ByteArray {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val key = getOrCreateAesKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateAesKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) createAesKey()
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun createAesKey() {
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        keyGen.generateKey()
    }

    private fun sanitizeKey(key: String): String = key.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "netguard_credential_key_v1"
        private const val PREFS_FILE = "netguard_credentials"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
    }
}
