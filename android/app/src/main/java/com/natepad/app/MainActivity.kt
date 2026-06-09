package com.natepad.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.natepad.app.ui.NatepadApp
import com.natepad.app.ui.theme.NatepadTheme

class MainActivity : AppCompatActivity() {

    private var isLocked by mutableStateOf(false)
    private var requireBiometric by mutableStateOf(false)

    private val biometricPrompt by lazy {
        val executor = ContextCompat.getMainExecutor(this)
        BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                isLocked = false
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Keep locked on error — user can retry
            }

            override fun onAuthenticationFailed() {
                // Fingerprint not recognized, keep locked
            }
        })
    }

    private val promptInfo by lazy {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_prompt_cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .setNegativeButtonText(getString(R.string.biometric_prompt_cancel))
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if biometric is available and configured
        val biometricManager = BiometricManager.from(this)
        requireBiometric = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS

        setContent {
            NatepadTheme {
                NatepadApp(
                    isLocked = isLocked,
                    onUnlock = { authenticateBiometric() }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (requireBiometric) {
            isLocked = true
        }
    }

    override fun onStart() {
        super.onStart()
        if (isLocked && requireBiometric) {
            authenticateBiometric()
        }
    }

    private fun authenticateBiometric() {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricPrompt.authenticate(promptInfo)
        } else {
            // No biometric enrolled — just unlock
            isLocked = false
        }
    }
}
