package com.natepad.app.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.natepad.app.ui.components.SectionLabel

private const val PREF_BIOMETRIC_LOCK = "biometric_lock"

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("natepad_settings", Context.MODE_PRIVATE) }

    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    var biometricEnabled by remember {
        mutableStateOf(prefs.getBoolean(PREF_BIOMETRIC_LOCK, false))
    }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        } catch (_: PackageManager.NameNotFoundException) {
            "—"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionLabel("Security")

        if (biometricAvailable) {
            ListItem(
                headlineContent = { Text("Biometric Lock") },
                supportingContent = { Text("Lock app when it goes to background") },
                trailingContent = {
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { enabled ->
                            biometricEnabled = enabled
                            prefs.edit().putBoolean(PREF_BIOMETRIC_LOCK, enabled).apply()
                        }
                    )
                }
            )
        } else {
            ListItem(
                headlineContent = { Text("Biometric Lock") },
                supportingContent = { Text("No biometrics enrolled on this device") },
                trailingContent = {
                    Switch(checked = false, onCheckedChange = null, enabled = false)
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        SectionLabel("About")

        ListItem(
            headlineContent = { Text("NatePad") },
            supportingContent = { Text("PGP notepad for Android") }
        )
        ListItem(
            headlineContent = { Text("Version") },
            trailingContent = {
                Text(
                    text = versionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        ListItem(
            headlineContent = { Text("Encryption") },
            supportingContent = { Text("RSA-4096 / AES-256 via Bouncy Castle") }
        )
        ListItem(
            headlineContent = { Text("Key Storage") },
            supportingContent = { Text("AES-256-GCM via Android Keystore") }
        )
    }
}
