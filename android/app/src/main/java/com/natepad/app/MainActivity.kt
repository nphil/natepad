package com.natepad.app

import android.content.Context
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

    private val settingsPrefs by lazy {
        getSharedPreferences("natepad_settings", Context.MODE_PRIVATE)
    }

    /**
     * Lock is active only when BOTH are true:
     *  1. the user enabled the Biometric Lock toggle in Settings
     *  2. the device actually has BIOMETRIC_STRONG enrolled
     * Reading the pref fresh on every call means toggling Settings
     * takes effect immediately, without restarting the app.
     */
    private fun lockEnabled(): Boolean {
        if (!settingsPrefs.getBoolean("biometric_lock", false)) return false
        return BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private val biometricPrompt by lazy {
        val executor = ContextCompat.getMainExecutor(this)
        BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                isLocked = false
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Keep locked — the lock screen's Unlock button retries
            }

            override fun onAuthenticationFailed() {
                // Biometric not recognized — prompt stays up for retry
            }
        })
    }

    private val promptInfo by lazy {
        // DEVICE_CREDENTIAL and setNegativeButtonText are mutually exclusive — BIOMETRIC_STRONG only
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(getString(R.string.biometric_prompt_cancel))
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
        if (lockEnabled()) {
            isLocked = true
        }
    }

    override fun onStart() {
        super.onStart()
        if (isLocked) {
            if (lockEnabled()) {
                // Prompt immediately — the lock screen sits behind as fallback
                authenticateBiometric()
            } else {
                // Lock was disabled (or biometrics removed) while backgrounded
                isLocked = false
            }
        }
    }

    private fun authenticateBiometric() {
        if (lockEnabled()) {
            biometricPrompt.authenticate(promptInfo)
        } else {
            isLocked = false
        }
    }
}
