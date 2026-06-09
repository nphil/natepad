package com.natepad.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.natepad.app.data.KeyRecord
import com.natepad.app.data.KeyRepository
import com.natepad.app.pgp.PgpService
import com.natepad.app.ui.components.KeyBadge
import com.natepad.app.ui.components.PgpTextField
import com.natepad.app.ui.components.SectionLabel
import com.natepad.app.ui.components.StatusCard
import com.natepad.app.ui.components.StatusType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(isTablet: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { KeyRepository.getInstance(context) }
    val keys by repo.keys.collectAsState()
    val scope = rememberCoroutineScope()

    var showGenerateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<KeyRecord?>(null) }
    var selectedKey by remember { mutableStateOf<KeyRecord?>(null) }
    var statusMsg by remember { mutableStateOf<Pair<String, StatusType>?>(null) }

    if (showGenerateDialog) {
        GenerateKeyDialog(
            onGenerate = { name, email, pass ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.Default) {
                            val gen = PgpService.generateKeyPair(name, email, pass)
                            KeyRecord(
                                id = gen.fingerprint,
                                fingerprint = gen.fingerprint,
                                userIds = listOf(gen.userId),
                                hasPublic = true,
                                hasPrivate = true,
                                armoredPublic = gen.armoredPublic,
                                armoredPrivate = gen.armoredPrivate
                            )
                        }
                    }.onSuccess { rec ->
                        repo.addKey(rec)
                        statusMsg = "Key generated: ${rec.displayName}" to StatusType.SUCCESS
                    }.onFailure {
                        statusMsg = (it.message ?: "Key generation failed") to StatusType.ERROR
                    }
                    showGenerateDialog = false
                }
            },
            onDismiss = { showGenerateDialog = false }
        )
    }

    if (showImportDialog) {
        ImportKeyDialog(
            onImport = { armored ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.Default) {
                            val parsed = PgpService.parseKeys(armored)
                            parsed.mapNotNull { PgpService.parsedKeyToRecord(it) }
                        }
                    }.onSuccess { recs ->
                        if (recs.isEmpty()) {
                            statusMsg = "No valid PGP keys found" to StatusType.ERROR
                        } else {
                            recs.forEach { repo.addKey(it) }
                            statusMsg = "Imported ${recs.size} key(s)" to StatusType.SUCCESS
                        }
                    }.onFailure {
                        statusMsg = (it.message ?: "Import failed") to StatusType.ERROR
                    }
                    showImportDialog = false
                }
            },
            onDismiss = { showImportDialog = false }
        )
    }

    deleteTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Key?") },
            text = { Text("Delete \"${rec.displayName}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        repo.removeKey(rec.id)
                        if (selectedKey?.id == rec.id) selectedKey = null
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    if (isTablet && selectedKey != null) {
        Row(modifier = modifier.fillMaxSize()) {
            KeyList(
                keys = keys,
                selectedKey = selectedKey,
                statusMsg = statusMsg,
                onSelect = { selectedKey = it },
                onDelete = { deleteTarget = it },
                onGenerate = { showGenerateDialog = true },
                onImport = { showImportDialog = true },
                modifier = Modifier.width(360.dp)
            )
            KeyDetail(
                record = selectedKey!!,
                onShare = { armored, name -> shareText(context, armored, name) },
                onDelete = { deleteTarget = selectedKey },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Scaffold(
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FloatingActionButton(onClick = { showImportDialog = true }, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(Icons.Default.Add, contentDescription = "Import")
                    }
                    FloatingActionButton(onClick = { showGenerateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Generate")
                    }
                }
            }
        ) { innerPadding ->
            Column(modifier = modifier.padding(innerPadding).padding(horizontal = 16.dp)) {
                statusMsg?.let { (msg, type) ->
                    Spacer(Modifier.height(8.dp))
                    StatusCard(msg, type)
                    Spacer(Modifier.height(8.dp))
                }
                if (keys.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No keys yet.\nGenerate or import a PGP key to get started.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(keys, key = { it.id }) { rec ->
                            KeyCard(
                                record = rec,
                                onShare = { armored, name -> shareText(context, armored, name) },
                                onDelete = { deleteTarget = rec }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyList(
    keys: List<KeyRecord>,
    selectedKey: KeyRecord?,
    statusMsg: Pair<String, StatusType>?,
    onSelect: (KeyRecord) -> Unit,
    onDelete: (KeyRecord) -> Unit,
    onGenerate: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onGenerate, modifier = Modifier.weight(1f)) { Text("Generate") }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") }
        }
        Spacer(Modifier.height(8.dp))
        statusMsg?.let { (msg, type) -> StatusCard(msg, type) ; Spacer(Modifier.height(8.dp)) }
        if (keys.isEmpty()) {
            Text("No keys yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(keys, key = { it.id }) { rec ->
                    Card(
                        onClick = { onSelect(rec) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedKey?.id == rec.id)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(rec.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (rec.displayEmail.isNotEmpty()) {
                                Text(rec.displayEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(4.dp))
                            KeyBadge(hasPublic = rec.hasPublic, hasPrivate = rec.hasPrivate)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyDetail(
    record: KeyRecord,
    onShare: (String, String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(record.displayName, style = MaterialTheme.typography.headlineMedium)
        if (record.displayEmail.isNotEmpty()) {
            Text(record.displayEmail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        KeyBadge(hasPublic = record.hasPublic, hasPrivate = record.hasPrivate)
        Spacer(Modifier.height(4.dp))
        Text(
            text = record.prettyFingerprint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (record.hasPublic) {
                OutlinedButton(onClick = { onShare(record.armoredPublic, "${record.displayName}_pub.asc") }) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Share Public")
                }
            }
            if (record.hasPrivate) {
                OutlinedButton(onClick = { onShare(record.armoredPrivate, "${record.displayName}_priv.asc") }) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Share Private")
                }
            }
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Delete")
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("Public Key")
        PgpTextField(value = record.armoredPublic, onValueChange = {}, label = "", readOnly = true, minLines = 6)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyCard(
    record: KeyRecord,
    onShare: (String, String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(record.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (record.displayEmail.isNotEmpty()) {
                        Text(record.displayEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(4.dp))
            KeyBadge(hasPublic = record.hasPublic, hasPrivate = record.hasPrivate)
            Text(
                text = record.shortId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (record.hasPublic) {
                    OutlinedButton(onClick = { onShare(record.armoredPublic, "${record.displayName}_pub.asc") }) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Share Public")
                    }
                }
                if (record.hasPrivate) {
                    OutlinedButton(onClick = { onShare(record.armoredPrivate, "${record.displayName}_priv.asc") }) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Share Private")
                    }
                }
            }
        }
    }
}

// ── Generate dialog ───────────────────────────────────────────────────────────

@Composable
private fun GenerateKeyDialog(
    onGenerate: (name: String, email: String, passphrase: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate PGP Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = passphrase, onValueChange = { passphrase = it }, label = { Text("Passphrase *") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = confirmPassphrase, onValueChange = { confirmPassphrase = it }, label = { Text("Confirm Passphrase *") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("Generating RSA-4096 may take a few seconds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    name.isBlank() -> error = "Name is required"
                    passphrase.isEmpty() -> error = "Passphrase cannot be empty"
                    passphrase != confirmPassphrase -> error = "Passphrases do not match"
                    else -> { error = null; onGenerate(name.trim(), email.trim(), passphrase) }
                }
            }) { Text("Generate") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Import dialog ─────────────────────────────────────────────────────────────

@Composable
private fun ImportKeyDialog(
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var armored by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import PGP Key") },
        text = {
            PgpTextField(
                value = armored,
                onValueChange = { armored = it },
                label = "Paste Armored Key",
                placeholder = "-----BEGIN PGP PUBLIC KEY BLOCK-----\n…",
                minLines = 8
            )
        },
        confirmButton = {
            Button(onClick = { onImport(armored) }, enabled = armored.isNotBlank()) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun shareText(context: android.content.Context, text: String, filename: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, filename)
    }
    context.startActivity(Intent.createChooser(intent, "Share $filename"))
}
