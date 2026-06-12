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
import com.natepad.app.ui.theme.AppTheme

class MainActivity : AppCompatActivity() {

    private var isLocked by mutableStateOf(false)
    private var showUnlockButton by mutableStateOf(false)
    private var biometricInProgress = false

    private val settingsPrefs by lazy {
        getSharedPreferences("natepad_settings", Context.MODE_PRIVATE)
    }

    private var selectedTheme by mutableStateOf(AppTheme.MATERIAL_YOU)

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
                biometricInProgress = false
                isLocked = false
                showUnlockButton = false
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                biometricInProgress = false
                // Show retry button — user cancelled or system error
                showUnlockButton = true
            }

            override fun onAuthenticationFailed() {
                // Prompt handles retry internally; show button so user can cancel+retry
                showUnlockButton = true
            }
        })
    }

    private val promptInfo by lazy {
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

        // Restore saved theme
        val savedThemeName = settingsPrefs.getString("app_theme", AppTheme.MATERIAL_YOU.name)
        selectedTheme = runCatching {
            AppTheme.valueOf(savedThemeName ?: AppTheme.MATERIAL_YOU.name)
        }.getOrDefault(AppTheme.MATERIAL_YOU)

        setContent {
            NatepadApp(
                isLocked = isLocked,
                showUnlockButton = showUnlockButton,
                onUnlock = {
                    showUnlockButton = false
                    authenticateBiometric()
                },
                selectedTheme = selectedTheme,
                onThemeChange = { theme ->
                    selectedTheme = theme
                    settingsPrefs.edit().putString("app_theme", theme.name).apply()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Trigger biometric prompt automatically when Activity regains focus.
        // onResume is safe: the window is visible and focused, so BiometricPrompt will show.
        if (isLocked && !biometricInProgress && lockEnabled()) {
            authenticateBiometric()
        }
    }

    override fun onStop() {
        super.onStop()
        if (lockEnabled()) {
            isLocked = true
            showUnlockButton = false
            biometricInProgress = false
        }
    }

    override fun onStart() {
        super.onStart()
        // Unlock immediately if lock was enabled while this session was running but is now disabled
        if (isLocked && !lockEnabled()) {
            isLocked = false
        }
    }

    private fun authenticateBiometric() {
        if (!lockEnabled()) { isLocked = false; return }
        biometricInProgress = true
        showUnlockButton = false
        biometricPrompt.authenticate(promptInfo)
    }
}
