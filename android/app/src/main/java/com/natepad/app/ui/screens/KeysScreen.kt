package com.natepad.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
import com.natepad.app.ui.components.AnimatedStatusCard
import com.natepad.app.ui.components.KeyBadge
import com.natepad.app.ui.components.PgpTextField
import com.natepad.app.ui.components.SectionLabel
import com.natepad.app.ui.components.StatusType
import com.natepad.app.util.PgpContentDetector
import com.natepad.app.util.PgpContentKind
import com.natepad.app.util.QrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun KeysScreen(
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
    var qrTarget by remember { mutableStateOf<KeyRecord?>(null) }
    var statusMsg by remember { mutableStateOf<Pair<String, StatusType>?>(null) }

    // List-detail scaffold: single pane on compact windows (detail slides over the
    // list, system back pops it), two panes side by side on expanded windows.
    // Keyed by fingerprint so selection survives list updates and process death.
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    BackHandler(navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    fun openDetail(rec: KeyRecord) {
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, rec.id) }
    }

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

    // ── Dialogs ───────────────────────────────────────────────────────────────

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
                        if (navigator.currentDestination?.contentKey == rec.id) {
                            scope.launch { navigator.navigateBack() }
                        }
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    // ── Panes ─────────────────────────────────────────────────────────────────

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        listPane = {
            // Canonical list-detail proportion: fixed-ish 360dp list, flexible detail.
            AnimatedPane(modifier = Modifier.preferredWidth(360.dp)) {
                KeyListPane(
                    keys = keys,
                    selectedId = navigator.currentDestination?.contentKey,
                    statusMsg = statusMsg,
                    onSelect = ::openDetail,
                    onGenerate = { showGenerateDialog = true },
                    onImport = { importPrefill = ""; showImportDialog = true },
                    onScan = ::launchScanner
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val record = navigator.currentDestination?.contentKey
                    ?.let { id -> keys.firstOrNull { it.id == id } }
                val listHidden =
                    navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden
                if (record != null) {
                    KeyDetailPane(
                        record = record,
                        showBack = listHidden,
                        onBack = { scope.launch { navigator.navigateBack() } },
                        onShare = { armored, name -> shareText(context, armored, name) },
                        onShowQr = { qrTarget = record },
                        onDelete = { deleteTarget = record }
                    )
                } else {
                    DetailPlaceholder()
                }
            }
        }
    )
}

// ── List pane ─────────────────────────────────────────────────────────────────

@Composable
private fun KeyListPane(
    keys: List<KeyRecord>,
    selectedId: String?,
    statusMsg: Pair<String, StatusType>?,
    onSelect: (KeyRecord) -> Unit,
    onGenerate: () -> Unit,
    onImport: () -> Unit,
    onScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Keys",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onGenerate, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generate")
            }
            FilledTonalButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import")
            }
            OutlinedButton(onClick = onScan) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan a key QR code", modifier = Modifier.size(18.dp))
            }
        }
        AnimatedStatusCard(
            status = statusMsg,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(keys, key = { it.id }) { rec ->
                    KeyCard(
                        record = rec,
                        isSelected = selectedId == rec.id,
                        onClick = { onSelect(rec) },
                        modifier = Modifier.animateItem()
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyCard(
    record: KeyRecord,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = record.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (record.displayEmail.isNotEmpty()) {
                    Text(
                        text = record.displayEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KeyBadge(hasPublic = record.hasPublic, hasPrivate = record.hasPrivate)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = record.shortId,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Detail pane ───────────────────────────────────────────────────────────────

@Composable
private fun KeyDetailPane(
    record: KeyRecord,
    showBack: Boolean,
    onBack: () -> Unit,
    onShare: (String, String) -> Unit,
    onShowQr: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to key list")
                }
                Spacer(Modifier.width(4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (record.displayEmail.isNotEmpty()) {
                    Text(
                        text = record.displayEmail,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        KeyBadge(hasPublic = record.hasPublic, hasPrivate = record.hasPrivate)

        Spacer(Modifier.height(16.dp))
        SectionLabel("Fingerprint")
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Text(
                text = record.prettyFingerprint,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(14.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Created " + DateFormat.getDateInstance().format(Date(record.createdAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (record.hasPublic) {
                FilledTonalButton(onClick = { onShare(record.armoredPublic, "${record.displayName}_pub.asc") }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Public")
                }
            }
            if (record.hasPrivate) {
                OutlinedButton(onClick = { onShare(record.armoredPrivate, "${record.displayName}_priv.asc") }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Private")
                }
            }
            if (record.hasPublic) {
                OutlinedButton(onClick = onShowQr) {
                    Icon(Icons.Default.QrCode2, contentDescription = "Show QR code", modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete key",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        if (record.hasPublic) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Public Key")
            PgpTextField(
                value = record.armoredPublic,
                onValueChange = {},
                label = "",
                readOnly = true,
                minLines = 6
            )
        }
    }
}

@Composable
private fun DetailPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.shapes.extraLarge
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Select a key to see its details",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                            .background(Color.White, MaterialTheme.shapes.medium)
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
