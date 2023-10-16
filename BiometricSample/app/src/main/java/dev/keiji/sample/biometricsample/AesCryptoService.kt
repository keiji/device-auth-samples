package dev.keiji.sample.biometricsample

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.IllegalStateException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.suspendCoroutine

private const val KEYSTORE_NAME = "AndroidKeyStore"

private const val TRANSFORMATION =
    KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE

private const val GCM_TAG_LENGTH = 128
private const val AES_INITIALIZATION_VECTOR_LENGTH_IN_BYTES = 12

interface IAesCryptoService {
    fun setCryptoAuthManager(cryptoAuthManager: ICryptoAuthManager)

    suspend fun encrypt(plainBytes: ByteArray): ByteArray

    suspend fun decrypt(ivAndEncrypted: ByteArray): ByteArray

    fun pack(iv: ByteArray, encrypted: ByteArray): ByteArray {
        return ByteArrayOutputStream().let {
            it.write(iv)
            it.write(encrypted)
            it.toByteArray()
        }
    }

    fun unpack(ivAndEncrypted: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(AES_INITIALIZATION_VECTOR_LENGTH_IN_BYTES)
        val encrypted = ByteArray(ivAndEncrypted.size - AES_INITIALIZATION_VECTOR_LENGTH_IN_BYTES)
        ByteArrayInputStream(ivAndEncrypted).use {
            it.read(iv)
            it.read(encrypted)
        }

        return Pair(iv, encrypted)
    }
}

class AesCryptoService(
    private val authenticationTimeout: Int,
    private val authenticationTypes: Int,
) : IAesCryptoService {

    private val keyalias = UUID.randomUUID().toString()

    private var cryptoAuthManager: ICryptoAuthManager? = null

    override fun setCryptoAuthManager(cryptoAuthManager: ICryptoAuthManager) {
        this.cryptoAuthManager = cryptoAuthManager
    }

    private val parameterSpec: KeyGenParameterSpec
        get() {
            val builder = KeyGenParameterSpec.Builder(
                keyalias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUnlockedDeviceRequired(true)
                // Do not set true
                .setUserConfirmationRequired(false)

                .setUserAuthenticationRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    authenticationTimeout,
                    authenticationTypes,
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(0)
            }

            return builder.build()
        }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_NAME).also {
            it.load(null)
        }

        if (keyStore.containsAlias(keyalias)) {
            return keyStore.getKey(keyalias, null) as SecretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME)
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    private fun getKeyInfo(secretKey: SecretKey): KeyInfo {
        val factory = SecretKeyFactory.getInstance(secretKey.algorithm, KEYSTORE_NAME)
        return factory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo
    }

    private suspend fun getEncryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = getSecretKey()
        val keyInfo = getKeyInfo(secretKey)

        try {
            // UserNotAuthenticatedException
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            if (!keyInfo.isUserAuthenticationRequired) {
                return cipher
            }

            val result = suspendCoroutine { continuation ->
                cryptoAuthManager?.authorize(
                    cryptoObject = BiometricPrompt.CryptoObject(cipher),
                    keyInfo.userAuthenticationTypeAsBiometricsAuthenticator,
                    continuation,
                )
            }
            when (result) {
                is ICryptoAuthManager.Result.Success -> {
                    val authorizedCipher = result.cryptoObject?.cipher
                        ?: throw IllegalStateException("Authentication succeeded but cipher null")
                    return authorizedCipher
                }

                is ICryptoAuthManager.Result.Error -> {
                    throw IllegalStateException("Authentication failed.")
                }
            }
        } catch (exception: UserNotAuthenticatedException) {
            val result = suspendCoroutine { continuation ->
                cryptoAuthManager?.authorize(
                    cryptoObject = null,
                    keyInfo.userAuthenticationTypeAsBiometricsAuthenticator,
                    continuation,
                )
            }
            when (result) {
                is ICryptoAuthManager.Result.Success -> {
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    return cipher
                }

                is ICryptoAuthManager.Result.Error -> {
                    throw IllegalStateException("Authentication failed.")
                }
            }
        }

    }

    override suspend fun encrypt(plainBytes: ByteArray): ByteArray {
        val cipher = getEncryptCipher()
        val encrypted = cipher
            .doFinal(plainBytes)
        return pack(cipher.iv, encrypted)
    }

    private suspend fun getDecryptCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = getSecretKey()
        val keyInfo = getKeyInfo(secretKey)

        try {
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )

            if (!keyInfo.isUserAuthenticationRequired) {
                return cipher
            }

            val result = suspendCoroutine { continuation ->
                cryptoAuthManager?.authorize(
                    cryptoObject = BiometricPrompt.CryptoObject(cipher),
                    keyInfo.userAuthenticationTypeAsBiometricsAuthenticator,
                    continuation,
                )
            }

            when (result) {
                is ICryptoAuthManager.Result.Success -> {
                    val authorizedCipher = result.cryptoObject?.cipher
                        ?: throw IllegalStateException("Authentication succeeded but cipher null")
                    return authorizedCipher
                }

                is ICryptoAuthManager.Result.Error -> {
                    throw IllegalStateException("Authentication failed.")
                }
            }
        } catch (exception: UserNotAuthenticatedException) {
            val result = suspendCoroutine { continuation ->
                cryptoAuthManager?.authorize(
                    cryptoObject = null,
                    keyInfo.userAuthenticationTypeAsBiometricsAuthenticator,
                    continuation,
                )
            }
            when (result) {
                is ICryptoAuthManager.Result.Success -> {
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        secretKey,
                        GCMParameterSpec(GCM_TAG_LENGTH, iv)
                    )
                    return cipher
                }

                is ICryptoAuthManager.Result.Error -> {
                    throw IllegalStateException("Authorization failed.")
                }
            }
        }
    }

    override suspend fun decrypt(ivAndEncrypted: ByteArray): ByteArray {
        val (iv, encrypted) = unpack(ivAndEncrypted)
        return getDecryptCipher(iv).doFinal(encrypted)
    }
}

private val KeyInfo.userAuthenticationTypeAsBiometricsAuthenticator: Int
    get() {
        var result = 0x0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (userAuthenticationType.and(KeyProperties.AUTH_DEVICE_CREDENTIAL) != 0) {
                result += BiometricManager.Authenticators.DEVICE_CREDENTIAL
            }
            if (userAuthenticationType.and(KeyProperties.AUTH_BIOMETRIC_STRONG) != 0) {
                result += BiometricManager.Authenticators.BIOMETRIC_STRONG
            }
        } else {
            result += BiometricManager.Authenticators.BIOMETRIC_STRONG
        }

        return result
    }

