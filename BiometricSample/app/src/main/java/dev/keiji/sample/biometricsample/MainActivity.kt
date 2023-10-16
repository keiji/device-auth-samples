package dev.keiji.sample.biometricsample

import android.os.Bundle
import android.security.keystore.KeyProperties
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.keiji.sample.biometricsample.ui.theme.BiometricSampleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.IllegalStateException
import java.util.concurrent.Executor
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.random.Random

class MainActivity : AppCompatActivity(), IAuthorizable {

    private val aesCryptoService: IAesCryptoService = AesCryptoService(
        0,
        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
    )

    private val cryptoAuthManager: ICryptoAuthManager?
        get() {
            val mainApplication = application
            return if (mainApplication is MainApplication) {
                mainApplication.cryptoAuthManager
            } else {
                null
            }
        }
    private val promptInfoBuilder
        get() = BiometricPrompt.PromptInfo.Builder()
            .setTitle("認証が必要です")
            .setSubtitle("ロックを解除してください")

    private lateinit var executor: Executor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        executor = ContextCompat.getMainExecutor(this)

        val mainApplication = application
        if (mainApplication is MainApplication) {
            aesCryptoService.setCryptoAuthManager(mainApplication.cryptoAuthManager)
        }

        setContent {
            BiometricSampleTheme {
                val coroutineScope = rememberCoroutineScope()

                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    MainScreen(doTest = {
                        coroutineScope.launch {
                            encryptDecryptTest()
                        }
                    })
                }
            }
        }
    }

    private val random = Random.Default

    private suspend fun encryptDecryptTest() {
        val plainBytes = random.nextBytes(256)

        try {
            val ivAndEncrypted = aesCryptoService.encrypt(plainBytes)
            val decrypted = aesCryptoService.decrypt(ivAndEncrypted)

            val result = if (plainBytes.contentEquals(decrypted)) {
                "Success"
            } else {
                "Failed"
            }
            Toast.makeText(
                this@MainActivity,
                "Encrypt/Decrypt $result",
                Toast.LENGTH_LONG
            ).show()

        } catch (exception: IllegalStateException) {
            Toast.makeText(
                this@MainActivity,
                "認証に失敗しました",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onStart() {
        super.onStart()

        cryptoAuthManager?.setCurrent(this)
    }

    override fun onStop() {
        super.onStop()

        cryptoAuthManager?.removeCurrent(this)
    }

    override fun authorize(
        cryptoObject: BiometricPrompt.CryptoObject?,
        allowedAuthenticators: Int,
        continuation: Continuation<ICryptoAuthManager.Result>
    ) {
        lifecycleScope.launch {
            showBiometricsPrompt(cryptoObject, allowedAuthenticators, continuation)
        }
    }

    private suspend fun showBiometricsPrompt(
        cryptoObject: BiometricPrompt.CryptoObject?,
        allowedAuthenticators: Int,
        continuation: Continuation<ICryptoAuthManager.Result>
    ) = withContext(Dispatchers.Main) {
        val prompt = BiometricPrompt(
            this@MainActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)

                    continuation.resume(ICryptoAuthManager.Result.Success(result.cryptoObject))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)

                    continuation.resume(
                        ICryptoAuthManager.Result.Error(
                            errorCode,
                            errString.toString()
                        )
                    )
                }
            })

        // Construct PromptInfo
        val builder = promptInfoBuilder
            .setAllowedAuthenticators(allowedAuthenticators)
        if (allowedAuthenticators.and(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == 0) {
            builder.setNegativeButtonText("Deny")
        }

        val promptInfo = builder.build()
        if (cryptoObject != null) {
            prompt.authenticate(
                promptInfo,
                cryptoObject,
            )
        } else {
            prompt.authenticate(
                promptInfo,
            )
        }
    }
}

@Composable
private fun MainScreen(doTest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = doTest,
        ) {
            Text(text = "Test")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    BiometricSampleTheme {
        MainScreen(doTest = {})
    }
}
