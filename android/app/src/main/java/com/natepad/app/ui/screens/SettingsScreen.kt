package com.natepad.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.natepad.app.data.BackupService
import com.natepad.app.data.KeyRepository
import com.natepad.app.ui.components.AnimatedStatusCard
import com.natepad.app.ui.components.SectionLabel
import com.natepad.app.ui.components.StatusType
import com.natepad.app.ui.components.contentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import com.natepad.app.ui.theme.AppTheme
import com.natepad.app.ui.theme.resolveColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREF_BIOMETRIC_LOCK = "biometric_lock"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    selectedTheme: AppTheme = AppTheme.MATERIAL_YOU,
    onThemeChange: (AppTheme) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("natepad_settings", Context.MODE_PRIVATE) }
    val repo = remember { KeyRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

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

    // ── Backup state ──────────────────────────────────────────────────────────
    var showExportDialog by remember { mutableStateOf(false) }
    var importedText by remember { mutableStateOf<String?>(null) }
    var backupStatus by remember { mutableStateOf<Pair<String, StatusType>?>(null) }
    var backupWorking by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.onSuccess { text ->
            if (text.isNullOrBlank()) {
                backupStatus = "Could not read the selected file" to StatusType.ERROR
            } else {
                importedText = text
            }
        }.onFailure {
            backupStatus = "Could not read the selected file" to StatusType.ERROR
        }
    }

    if (showExportDialog) {
        BackupPasswordDialog(
            title = "Export Encrypted Backup",
            description = "Set a backup password. You will need it to restore — it cannot be recovered.",
            confirmLabel = "Create & Share",
            requireConfirm = true,
            isWorking = backupWorking,
            onConfirm = { password ->
                scope.launch {
                    backupWorking = true
                    runCatching {
                        withContext(Dispatchers.Default) {
                            BackupService.createBackup(repo.getKeys(), password.toCharArray())
                        }
                    }.onSuccess { json ->
                        showExportDialog = false
                        runCatching { shareBackupFile(context, json) }
                            .onSuccess { backupStatus = "Backup ready — choose where to save it" to StatusType.INFO }
                            .onFailure { backupStatus = "Could not open share sheet" to StatusType.ERROR }
                    }.onFailure {
                        backupStatus = (it.message ?: "Backup failed") to StatusType.ERROR
                        showExportDialog = false
                    }
                    backupWorking = false
                }
            },
            onDismiss = { if (!backupWorking) showExportDialog = false }
        )
    }

    importedText?.let { text ->
        BackupPasswordDialog(
            title = "Restore Backup",
            description = "Enter the password this backup was encrypted with.",
            confirmLabel = "Restore",
            requireConfirm = false,
            isWorking = backupWorking,
            onConfirm = { password ->
                scope.launch {
                    backupWorking = true
                    runCatching {
                        withContext(Dispatchers.Default) {
                            BackupService.restoreBackup(text, password.toCharArray())
                        }
                    }.onSuccess { recs ->
                        recs.forEach { repo.addKey(it) }
                        backupStatus = "Restored ${recs.size} key(s)" to StatusType.SUCCESS
                        importedText = null
                    }.onFailure { e ->
                        backupStatus = when (e) {
                            is IllegalArgumentException -> (e.message ?: "Invalid backup file") to StatusType.ERROR
                            else -> "Wrong password or corrupted backup" to StatusType.ERROR
                        }
                    }
                    backupWorking = false
                }
            },
            onDismiss = { if (!backupWorking) importedText = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Column(
        modifier = Modifier
            .contentWidth(max = 720.dp)
            .padding(horizontal = 16.dp, vertical = 16.dp)
      ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        // ── Security ──────────────────────────────────────────────────────────
        SectionLabel("Security", modifier = Modifier.padding(bottom = 8.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            } else {
                ListItem(
                    headlineContent = { Text("Biometric Lock") },
                    supportingContent = { Text("No biometrics enrolled on this device") },
                    trailingContent = {
                        Switch(checked = false, onCheckedChange = null, enabled = false)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

        // ── Appearance ────────────────────────────────────────────────────────
        SectionLabel("Appearance", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                val dark = isSystemInDarkTheme()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppTheme.values().forEach { theme ->
                        ThemeSwatch(
                            theme = theme,
                            dark = dark,
                            selected = selectedTheme == theme,
                            onClick = { onThemeChange(theme) }
                        )
                    }
                }
            }
        }

        // ── Backup ────────────────────────────────────────────────────────────
        SectionLabel("Backup", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        AnimatedStatusCard(backupStatus, modifier = Modifier.padding(bottom = 8.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Export Encrypted Backup") },
                supportingContent = { Text("All keys in one password-encrypted file, shared via the system share sheet") },
                leadingContent = {
                    Icon(Icons.Outlined.Upload, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    if (repo.getKeys().isEmpty()) {
                        backupStatus = "No keys to back up yet" to StatusType.ERROR
                    } else {
                        showExportDialog = true
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("Import Backup") },
                supportingContent = { Text("Restore keys from a NatePad backup file") },
                leadingContent = {
                    Icon(Icons.Outlined.Download, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { importLauncher.launch(arrayOf("*/*")) }
            )
        }
        Text(
            text = "Backups are encrypted with AES-256-GCM using a key derived from your password " +
                "(PBKDF2, 600k iterations). The same file restores on both Android and iOS.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
        )

        // ── About ─────────────────────────────────────────────────────────────
        SectionLabel("About", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("NatePad") },
                supportingContent = { Text("PGP notepad for Android") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("Version") },
                trailingContent = {
                    Text(
                        text = versionName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("Encryption") },
                supportingContent = { Text("RSA-4096 / AES-256 via Bouncy Castle") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("Key Storage") },
                supportingContent = { Text("AES-256-GCM via Android Keystore") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
      }
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    description: String,
    confirmLabel: String,
    requireConfirm: Boolean,
    isWorking: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !isWorking,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (requireConfirm) ImeAction.Next else ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (requireConfirm) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !isWorking,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (isWorking) {
                    Spacer(Modifier.height(4.dp))
                    CircularProgressIndicator(modifier = Modifier.padding(start = 4.dp))
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isWorking,
                onClick = {
                    when {
                        password.isEmpty() -> error = "Password cannot be empty"
                        requireConfirm && password.length < 8 -> error = "Use at least 8 characters"
                        requireConfirm && password != confirm -> error = "Passwords do not match"
                        else -> { error = null; onConfirm(password) }
                    }
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isWorking) { Text("Cancel") }
        }
    )
}

@Composable
private fun ThemeSwatch(theme: AppTheme, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    // Preview the theme with its own generated palette so light/dark reads true.
    val scheme = theme.resolveColorScheme(dark)
    val swatchBg = scheme.surfaceContainerHighest
    val swatchDot = scheme.primary

    val borderWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 1.5.dp, label = "swatch_border"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        label = "swatch_border_color"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(swatchBg)
                .border(width = borderWidth, color = borderColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = scheme.onPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .background(swatchDot, CircleShape)
                        .padding(4.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(swatchDot)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = theme.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun shareBackupFile(context: Context, json: String) {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    val dir = File(context.cacheDir, "backups").apply { mkdirs() }
    val file = File(dir, "natepad-backup-$stamp.${BackupService.FILE_EXTENSION}")
    file.writeText(json)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share backup"))
}
