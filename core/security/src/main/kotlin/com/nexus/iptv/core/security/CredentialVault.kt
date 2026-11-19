package com.nexus.iptv.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nexus.iptv.core.common.result.NexusException
import com.nexus.iptv.core.common.result.NexusResult
import com.nexus.iptv.core.common.result.runNexusCatching
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface CredentialVault {
    fun encryptSecret(plainText: String): NexusResult<String>
    fun decryptSecret(cipherPayloadBase64: String): NexusResult<String>
}

@Singleton
class AndroidKeyStoreCredentialVault @Inject constructor() : CredentialVault {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nexus_iptv_master_vault_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) return existingKey.secretKey
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun encryptSecret(plainText: String): NexusResult<String> = runNexusCatching {
        if (plainText.isEmpty()) return@runNexusCatching ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val buffer = ByteBuffer.allocate(1 + iv.size + encryptedBytes.size)
        buffer.put(iv.size.toByte())
        buffer.put(iv)
        buffer.put(encryptedBytes)
        Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    override fun decryptSecret(cipherPayloadBase64: String): NexusResult<String> = runNexusCatching {
        if (cipherPayloadBase64.isEmpty()) return@runNexusCatching ""
        val rawBytes = Base64.decode(cipherPayloadBase64, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(rawBytes)
        val ivLength = buffer.get().toInt()
        if (ivLength != IV_SIZE_BYTES) throw NexusException.SecurityVaultException("Некорректная длина IV ($ivLength)")
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val cipherBytes = ByteArray(buffer.remaining())
        buffer.get(cipherBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    abstract fun bindCredentialVault(impl: AndroidKeyStoreCredentialVault): CredentialVault
}
