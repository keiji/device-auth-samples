package dev.keiji.sample.biometricsample

import androidx.biometric.BiometricPrompt.CryptoObject
import kotlin.coroutines.Continuation

interface IAuthorizable {
    fun authorize(
        cryptoObject: CryptoObject?,
        allowedAuthenticators: Int,
        continuation: Continuation<ICryptoAuthManager.Result>,
    )
}

interface ICryptoAuthManager {
    fun setCurrent(authorizable: IAuthorizable)
    fun removeCurrent(authorizable: IAuthorizable)

    sealed class Result {
        data class Success(val cryptoObject: CryptoObject?) : Result()
        data class Error(val errorCode: Int, val errString: String) : Result()
    }

    fun authorize(
        cryptoObject: CryptoObject?,
        allowedAuthenticators: Int,
        continuation: Continuation<Result>,
    )
}

class CryptoAuthManager : ICryptoAuthManager {
    private var current: IAuthorizable? = null

    override fun setCurrent(authorizable: IAuthorizable) {
        current = authorizable
    }

    override fun removeCurrent(authorizable: IAuthorizable) {
        if (current != authorizable) {
            return
        }
        current = null
    }

    override fun authorize(
        cryptoObject: CryptoObject?,
        allowedAuthenticators: Int,
        continuation: Continuation<ICryptoAuthManager.Result>,
    ) {
        current?.authorize(cryptoObject, allowedAuthenticators, continuation)
    }
}
