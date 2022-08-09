package dev.keiji.sample.biometricsample

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_BIOMETRIC_ENROLL
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG: String = "MainActivity"

        private const val REQUEST_CODE_BIOMETRIC_ENROLL = 0x011

        private const val AUTHENTICATE_VALUE = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var biometricManager: BiometricManager

    private lateinit var state: TextView
    private lateinit var setupAuth: Button
    private lateinit var startAuth: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        state = findViewById(R.id.state)
        setupAuth = findViewById(R.id.setup_auth)
        startAuth = findViewById(R.id.start_auth)

        setupAuth.setOnClickListener {
            startSettingActivity()
        }
        startAuth.setOnClickListener {
            startAuth(AUTHENTICATE_VALUE)
        }

        initBiometricPrompt()

        biometricManager = BiometricManager.from(this)
    }

    override fun onStart() {
        super.onStart()

        updateAuthenticateState()
    }

    private fun updateAuthenticateState() {
        setupAuth.isEnabled = false

        val canAuthenticate = biometricManager.canAuthenticate(AUTHENTICATE_VALUE)
        Log.d(TAG, "canAuthenticate: $canAuthenticate")

        val text = when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                "BIOMETRIC_SUCCESS"
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                "BIOMETRIC_ERROR_NO_HARDWARE"
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                "BIOMETRIC_ERROR_HW_UNAVAILABLE"
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                setupAuth.isEnabled = true
                "BIOMETRIC_ERROR_NONE_ENROLLED"
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                "BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED"
            }
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                "BIOMETRIC_ERROR_UNSUPPORTED"
            }
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                "BIOMETRIC_STATUS_UNKNOWN"
            }
            else -> "else"
        }

        Log.d(TAG, text)
        state.text = text
    }

    private fun initBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(
                        applicationContext,
                        "Authentication error: $errString", Toast.LENGTH_SHORT
                    )
                        .show()
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(
                        applicationContext,
                        "Authentication succeeded!", Toast.LENGTH_SHORT
                    )
                        .show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(
                        applicationContext, "Authentication failed",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            })
    }

    private fun startSettingActivity() {
        val enrollIntent = Intent(ACTION_BIOMETRIC_ENROLL).apply {
            putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                AUTHENTICATE_VALUE
            )
        }
        startActivityForResult(enrollIntent, REQUEST_CODE_BIOMETRIC_ENROLL)
    }

    private fun startAuth(authenticateValue: Int) {
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for my app")
            .setSubtitle("Log in using your biometric credential")
            .setAllowedAuthenticators(authenticateValue)

        if (authenticateValue and DEVICE_CREDENTIAL == 0) {
            promptInfoBuilder.setNegativeButtonText("Negative Button Text")
        }

        biometricPrompt.authenticate(promptInfoBuilder.build())
    }
}
