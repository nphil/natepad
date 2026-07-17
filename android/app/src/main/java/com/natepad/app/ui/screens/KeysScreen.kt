package com.natepad.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.natepad.app.data.KeyRecord
import com.natepad.app.data.KeyRepository
import com.natepad.app.pgp.PgpService
import com.natepad.app.ui.components.KeyBadge
import com.natepad.app.ui.components.PgpTextField
import com.natepad.app.ui.components.SectionLabel
import com.natepad.app.ui.components.StatusCard
import com.natepad.app.ui.components.StatusType
import com.natepad.app.util.PgpContentDetector
import com.natepad.app.util.PgpContentKind
import com.natepad.app.util.QrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(
    isTablet: Boolean,
    modifier: Modifier = Modifier,
    importRequest: String? = null,
    onImportRequestConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val repo = remember { KeyRepository.getInstance(context) }
    val keys by repo.keys.collectAsState()
    val scope = rememberCoroutineScope()

    var showGenerateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importPrefill by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<KeyRecord?>(null) }
    var selectedKey by remember { mutableStateOf<KeyRecord?>(null) }
    var qrTarget by remember { mutableStateOf<KeyRecord?>(null) }
    var statusMsg by remember { mutableStateOf<Pair<String, StatusType>?>(null) }

    // A key block handed over from the clipboard check on the Home screen.
    LaunchedEffect(importRequest) {
        if (importRequest != null) {
            importPrefill = importRequest
            showImportDialog = true
            onImportRequestConsumed()
        }
    }

    fun handleScanResult(contents: String) {
        val trimmed = contents.trim()
        if (trimmed.uppercase().startsWith(QrCode.FINGERPRINT_SCHEME)) {
            val fp = trimmed.substring(QrCode.FINGERPRINT_SCHEME.length)
                .replace(" ", "").uppercase()
            val match = keys.firstOrNull { it.fingerprint.uppercase() == fp }
            statusMsg = if (match != null) {
                "Fingerprint verified — matches ${match.displayName}" to StatusType.SUCCESS
            } else {
                "No key with fingerprint ${fp.take(16)}… in your keyring — ask for the full key" to StatusType.WARNING
            }
            return
        }
        when (PgpContentDetector.detect(trimmed)) {
            PgpContentKind.PUBLIC_KEY, PgpContentKind.PRIVATE_KEY -> scope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        PgpService.parseKeys(trimmed).mapNotNull { PgpService.parsedKeyToRecord(it) }
                    }
                }.onSuccess { recs ->
                    if (recs.isEmpty()) {
                        statusMsg = "The QR contained no valid PGP keys" to StatusType.ERROR
                    } else {
                        recs.forEach { repo.addKey(it) }
                        statusMsg = "Imported ${recs.size} key(s) from QR" to StatusType.SUCCESS
                    }
                }.onFailure {
                    statusMsg = (it.message ?: "Import failed") to StatusType.ERROR
                }
            }
            else -> statusMsg =
                "The QR code doesn't contain a PGP key or fingerprint" to StatusType.WARNING
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { handleScanResult(it) }
    }

    fun launchScanner() {
        scanLauncher.launch(ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan a key QR code")
            setBeepEnabled(false)
            setOrientationLocked(true)
        })
    }

    qrTarget?.let { rec ->
        QrDialog(record = rec, onDismiss = { qrTarget = null })
    }

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
            initialText = importPrefill,
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
        // ── Tablet split view ─────────────────────────────────────────────────
        Row(modifier = modifier.fillMaxSize()) {
            KeyList(
                keys = keys,
                selectedKey = selectedKey,
                statusMsg = statusMsg,
                onSelect = { selectedKey = it },
                onDelete = { deleteTarget = it },
                onGenerate = { showGenerateDialog = true },
                onImport = { importPrefill = ""; showImportDialog = true },
                modifier = Modifier.width(320.dp)
            )
            VerticalDivider()
            KeyDetail(
                record = selectedKey!!,
                onShare = { armored, name -> shareText(context, armored, name) },
                onDelete = { deleteTarget = selectedKey },
                onShowQr = { qrTarget = selectedKey },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        // ── Single column view (phone or tablet with no selection) ────────────
        Column(modifier = modifier.fillMaxSize()) {
            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showGenerateDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generate")
                }
                FilledTonalButton(
                    onClick = { importPrefill = ""; showImportDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import")
                }
                OutlinedButton(onClick = { launchScanner() }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan a key QR code", modifier = Modifier.size(18.dp))
                }
            }

            statusMsg?.let { (msg, type) ->
                StatusCard(
                    message = msg, type = type,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            if (keys.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No keys yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Generate or import a PGP key to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(keys, key = { it.id }) { rec ->
                        KeyCard(
                            record = rec,
                            isSelected = isTablet && selectedKey?.id == rec.id,
                            onSelect = { if (isTablet) selectedKey = rec },
                            onShare = { armored, name -> shareText(context, armored, name) },
                            onDelete = { deleteTarget = rec },
                            onShowQr = { qrTarget = rec }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
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
        statusMsg?.let { (msg, type) -> StatusCard(msg, type); Spacer(Modifier.height(8.dp)) }
        if (keys.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Key, contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No keys yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(keys, key = { it.id }) { rec ->
                    Card(
                        onClick = { onSelect(rec) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedKey?.id == rec.id)
                                MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
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
    onShowQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(20.dp)) {
        Text(record.displayName, style = MaterialTheme.typography.headlineSmall)
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
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (record.hasPublic) {
                OutlinedButton(onClick = { onShare(record.armoredPublic, "${record.displayName}_pub.asc") }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share Public")
                }
            }
            if (record.hasPrivate) {
                OutlinedButton(onClick = { onShare(record.armoredPrivate, "${record.displayName}_priv.asc") }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share Private")
                }
            }
            OutlinedButton(onClick = onShowQr) {
                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("QR")
            }
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete")
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel("Public Key")
        PgpTextField(value = record.armoredPublic, onValueChange = {}, label = "", readOnly = true, minLines = 6)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyCard(
    record: KeyRecord,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onShare: (String, String) -> Unit,
    onDelete: () -> Unit,
    onShowQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
            Spacer(Modifier.height(6.dp))
            KeyBadge(hasPublic = record.hasPublic, hasPrivate = record.hasPrivate)
            Spacer(Modifier.height(2.dp))
            Text(
                text = record.shortId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (record.hasPublic) {
                    OutlinedButton(
                        onClick = { onShare(record.armoredPublic, "${record.displayName}_pub.asc") },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share Public", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (record.hasPrivate) {
                    OutlinedButton(
                        onClick = { onShare(record.armoredPrivate, "${record.displayName}_priv.asc") },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share Private", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (record.hasPublic) {
                    OutlinedButton(
                        onClick = onShowQr,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("QR", style = MaterialTheme.typography.labelMedium)
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
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                OutlinedTextField(value = passphrase, onValueChange = { passphrase = it }, label = { Text("Passphrase *") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                OutlinedTextField(value = confirmPassphrase, onValueChange = { confirmPassphrase = it }, label = { Text("Confirm Passphrase *") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
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
    onDismiss: () -> Unit,
    initialText: String = ""
) {
    var armored by remember { mutableStateOf(initialText) }

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

// ── QR dialog ─────────────────────────────────────────────────────────────────

@Composable
private fun QrDialog(record: KeyRecord, onDismiss: () -> Unit) {
    var showFullKey by remember { mutableStateOf(false) }
    val keyFits = record.hasPublic &&
        record.armoredPublic.toByteArray(Charsets.UTF_8).size <= QrCode.MAX_PAYLOAD_BYTES
    val payload = if (showFullKey && keyFits) record.armoredPublic
    else QrCode.FINGERPRINT_SCHEME + record.fingerprint

    val bitmap by produceState<Bitmap?>(initialValue = null, payload) {
        value = withContext(Dispatchers.Default) { QrCode.generate(payload) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Key QR Code") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showFullKey,
                        onClick = { showFullKey = false },
                        label = { Text("Fingerprint") }
                    )
                    FilterChip(
                        selected = showFullKey,
                        onClick = { showFullKey = true },
                        label = { Text("Full key") },
                        enabled = keyFits
                    )
                }
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Key QR code",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                Text(
                    text = record.prettyFingerprint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = when {
                        showFullKey -> "Scanning imports this public key."
                        keyFits -> "Verifies the fingerprint. Switch to Full key to transfer the key itself."
                        else -> "Verifies the fingerprint. This key is too large for a QR code — share it as text or a file instead."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
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
