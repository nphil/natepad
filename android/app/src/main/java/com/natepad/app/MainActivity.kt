package com.natepad.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.natepad.app.ui.NatepadApp
import com.natepad.app.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private var isLocked by mutableStateOf(false)
    private var showUnlockButton by mutableStateOf(false)
    private var biometricInProgress = false

    private val settingsPrefs by lazy {
        getSharedPreferences("natepad_settings", Context.MODE_PRIVATE)
    }

    private var selectedTheme by mutableStateOf(AppTheme.MATERIAL_YOU)

    /** Text handed in from outside (share sheet or Open-with), pending routing. */
    private var externalText by mutableStateOf<String?>(null)

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

        // Blank this task's snapshot in the app switcher so decrypted plaintext
        // never lingers in recents. Deliberately NOT FLAG_SECURE: in-app
        // screenshots (e.g. of a key's QR code) stay possible.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }

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
                },
                externalText = externalText,
                onExternalTextConsumed = { externalText = null }
            )
        }

        // Only handle the launching intent on a fresh start — after a config
        // change the original SEND/VIEW intent is redelivered and must not
        // re-route over whatever the user was doing.
        if (savedInstanceState == null) handleExternalIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
    }

    // ── Share-target / Open-with ──────────────────────────────────────────────

    private fun handleExternalIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    externalText = text
                } else {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                        ?.let { readExternalUri(it) }
                }
            }
            Intent.ACTION_VIEW -> intent.data?.let { readExternalUri(it) }
        }
    }

    private fun readExternalUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val text = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_EXTERNAL_BYTES) { "File too large" }
                        buf.write(chunk, 0, n)
                    }
                    buf.toByteArray().toString(Charsets.UTF_8)
                }
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (text.isNullOrBlank()) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.external_open_failed),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    externalText = text
                }
            }
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

    companion object {
        /** Sanity cap for shared/opened files — armored PGP text is never this big. */
        private const val MAX_EXTERNAL_BYTES = 2 * 1024 * 1024
    }
}
