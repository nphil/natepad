package com.natepad.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.natepad.app.data.KeyRecord
import com.natepad.app.data.KeyRepository
import com.natepad.app.pgp.PgpService
import com.natepad.app.ui.components.ModeFilterChip
import com.natepad.app.ui.components.PgpTextField
import com.natepad.app.ui.components.RecipientChip
import com.natepad.app.ui.components.SectionLabel
import com.natepad.app.ui.components.StatusCard
import com.natepad.app.ui.components.StatusType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CryptoMode(val label: String) {
    ENCRYPT("Encrypt"),
    DECRYPT("Decrypt"),
    SIGN("Sign"),
    VERIFY("Verify")
}

@Composable
fun CryptoScreen(
    initialMode: CryptoMode = CryptoMode.ENCRYPT,
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { KeyRepository.getInstance(context) }
    val keys by repo.keys.collectAsState()

    var selectedMode by remember { mutableStateOf(initialMode) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CryptoMode.entries.forEach { mode ->
                ModeFilterChip(
                    label = mode.label,
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode }
                )
            }
        }

        when (selectedMode) {
            CryptoMode.ENCRYPT -> EncryptWorkflow(keys = keys, isTablet = isTablet, modifier = Modifier.weight(1f))
            CryptoMode.DECRYPT -> DecryptWorkflow(keys = keys, isTablet = isTablet, modifier = Modifier.weight(1f))
            CryptoMode.SIGN -> SignWorkflow(keys = keys, isTablet = isTablet, modifier = Modifier.weight(1f))
            CryptoMode.VERIFY -> VerifyWorkflow(keys = keys, isTablet = isTablet, modifier = Modifier.weight(1f))
        }
    }
}

// ── Encrypt ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncryptWorkflow(keys: List<KeyRecord>, isTablet: Boolean, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val recipients = remember { mutableStateListOf<KeyRecord>() }
    var signingKey by remember { mutableStateOf<KeyRecord?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Pair<String, StatusType>?>(null) }
    var recipientDropdownExpanded by remember { mutableStateOf(false) }
    var signerDropdownExpanded by remember { mutableStateOf(false) }

    fun doEncrypt() {
        if (recipients.isEmpty()) { status = "Select at least one recipient" to StatusType.ERROR; return }
        scope.launch {
            status = null
            runCatching {
                withContext(Dispatchers.Default) {
                    PgpService.encrypt(
                        input, recipients,
                        signingKey?.takeIf { passphrase.isNotEmpty() },
                        passphrase.ifEmpty { null }
                    )
                }
            }.onSuccess { result ->
                output = result
                status = "Encrypted successfully" to StatusType.SUCCESS
            }.onFailure { e ->
                status = (e.message ?: "Encryption failed") to StatusType.ERROR
            }
        }
    }

    val publicKeys = keys.filter { it.hasPublic }
    val privateKeys = keys.filter { it.hasPrivate }

    if (isTablet) {
        Row(
            modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EncryptControls(
                    recipients = recipients, publicKeys = publicKeys, privateKeys = privateKeys,
                    signingKey = signingKey, passphrase = passphrase,
                    recipientDropdownExpanded = recipientDropdownExpanded, signerDropdownExpanded = signerDropdownExpanded,
                    onRecipientsExpand = { recipientDropdownExpanded = it }, onSignerExpand = { signerDropdownExpanded = it },
                    onAddRecipient = { if (!recipients.contains(it)) recipients.add(it) }, onRemoveRecipient = { recipients.remove(it) },
                    onSigningKeyChange = { signingKey = it }, onPassphraseChange = { passphrase = it }
                )
                PgpTextField(value = input, onValueChange = { input = it }, label = "Plaintext", minLines = 8)
                Button(onClick = ::doEncrypt, modifier = Modifier.fillMaxWidth()) { Text("Encrypt") }
                status?.let { (msg, type) -> StatusCard(msg, type) }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PgpTextField(
                    value = output, onValueChange = {}, label = "Encrypted Output",
                    readOnly = true, minLines = 6, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(output)) },
                        enabled = output.isNotEmpty()
                    ) { Text("Copy") }
                    OutlinedButton(onClick = { output = ""; input = "" }) { Text("Clear") }
                }
            }
        }
    } else {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EncryptControls(
                recipients = recipients, publicKeys = publicKeys, privateKeys = privateKeys,
                signingKey = signingKey, passphrase = passphrase,
                recipientDropdownExpanded = recipientDropdownExpanded, signerDropdownExpanded = signerDropdownExpanded,
                onRecipientsExpand = { recipientDropdownExpanded = it }, onSignerExpand = { signerDropdownExpanded = it },
                onAddRecipient = { if (!recipients.contains(it)) recipients.add(it) }, onRemoveRecipient = { recipients.remove(it) },
                onSigningKeyChange = { signingKey = it }, onPassphraseChange = { passphrase = it }
            )
            PgpTextField(value = input, onValueChange = { input = it }, label = "Plaintext")
            Button(onClick = ::doEncrypt, modifier = Modifier.fillMaxWidth()) { Text("Encrypt") }
            status?.let { (msg, type) -> StatusCard(msg, type) }
            if (output.isNotEmpty()) {
                PgpTextField(value = output, onValueChange = {}, label = "Encrypted Output", readOnly = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(output)) }) { Text("Copy") }
                    OutlinedButton(onClick = { output = ""; input = "" }) { Text("Clear") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncryptControls(
    recipients: List<KeyRecord>,
    publicKeys: List<KeyRecord>,
    privateKeys: List<KeyRecord>,
    signingKey: KeyRecord?,
    passphrase: String,
    recipientDropdownExpanded: Boolean,
    signerDropdownExpanded: Boolean,
    onRecipientsExpand: (Boolean) -> Unit,
    onSignerExpand: (Boolean) -> Unit,
    onAddRecipient: (KeyRecord) -> Unit,
    onRemoveRecipient: (KeyRecord) -> Unit,
    onSigningKeyChange: (KeyRecord?) -> Unit,
    onPassphraseChange: (String) -> Unit
) {
    SectionLabel("Recipients")
    if (recipients.isNotEmpty()) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            recipients.forEach { rec -> RecipientChip(record = rec, onRemove = { onRemoveRecipient(rec) }) }
        }
    }
    if (publicKeys.isNotEmpty()) {
        ExposedDropdownMenuBox(expanded = recipientDropdownExpanded, onExpandedChange = onRecipientsExpand) {
            OutlinedTextField(
                value = "Add recipient…",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recipientDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
             )
            ExposedDropdownMenu(expanded = recipientDropdownExpanded, onDismissRequest = { onRecipientsExpand(false) }) {
                publicKeys.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(key.label, maxLines = 1) },
                        onClick = { onAddRecipient(key); onRecipientsExpand(false) }
                    )
                }
            }
        }
    }
    SectionLabel("Sign with (optional)")
    ExposedDropdownMenuBox(expanded = signerDropdownExpanded, onExpandedChange = onSignerExpand) {
        OutlinedTextField(
            value = signingKey?.displayName ?: "None",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = signerDropdownExpanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
         )
        ExposedDropdownMenu(expanded = signerDropdownExpanded, onDismissRequest = { onSignerExpand(false) }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onSigningKeyChange(null); onSignerExpand(false) })
            privateKeys.forEach { key ->
                DropdownMenuItem(text = { Text(key.label, maxLines = 1) }, onClick = { onSigningKeyChange(key); onSignerExpand(false) })
            }
        }
    }
    if (signingKey != null) {
        OutlinedTextField(
            value = passphrase,
            onValueChange = onPassphraseChange,
            label = { Text("Signing passphrase") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Decrypt ───────────────────────────────────────────────────────────────────

@Composable
private fun DecryptWorkflow(keys: List<KeyRecord>, isTablet: Boolean, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Pair<String, StatusType>?>(null) }
    var passphraseDialogKey by remember { mutableStateOf<KeyRecord?>(null) }

    val privateKeys = keys.filter { it.hasPrivate }

    fun doDecryptWithPassphrase(passphrase: String) {
        scope.launch {
            status = null
            runCatching {
                withContext(Dispatchers.Default) {
                    PgpService.decrypt(input, privateKeys) { passphrase }
                }
            }.onSuccess { result ->
                output = result
                status = "Decrypted successfully" to StatusType.SUCCESS
            }.onFailure { e ->
                status = (e.message ?: "Decryption failed") to StatusType.ERROR
            }
        }
    }

    fun doDecrypt() {
        if (privateKeys.isEmpty()) { status = "No private keys available" to StatusType.ERROR; return }
        scope.launch {
            status = null
            runCatching {
                withContext(Dispatchers.Default) {
                    // Try with empty passphrase first; if it fails, the dialog will prompt
                    PgpService.decrypt(input, privateKeys) { null }
                }
            }.onSuccess { result ->
                output = result
                status = "Decrypted successfully" to StatusType.SUCCESS
            }.onFailure { e ->
                // Show passphrase dialog for the first available private key
                if (privateKeys.isNotEmpty()) passphraseDialogKey = privateKeys.first()
                else status = (e.message ?: "Decryption failed") to StatusType.ERROR
            }
        }
    }

    passphraseDialogKey?.let { keyRecord ->
        PassphraseDialog(
            keyName = keyRecord.displayName,
            onConfirm = { pp -> passphraseDialogKey = null; doDecryptWithPassphrase(pp) },
            onDismiss = { passphraseDialogKey = null }
        )
    }

    if (isTablet) {
        Row(
            modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PgpTextField(
                    value = input, onValueChange = { input = it }, label = "Encrypted Input",
                    minLines = 6, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = ::doDecrypt, modifier = Modifier.fillMaxWidth()) { Text("Decrypt") }
                status?.let { (msg, type) -> Spacer(Modifier.height(8.dp)); StatusCard(msg, type) }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PgpTextField(
                    value = output, onValueChange = {}, label = "Plaintext Output",
                    readOnly = true, minLines = 6, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(output)) },
                        enabled = output.isNotEmpty()
                    ) { Text("Copy") }
                    OutlinedButton(onClick = { output = ""; input = "" }) { Text("Clear") }
                }
            }
        }
    } else {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PgpTextField(value = input, onValueChange = { input = it }, label = "Encrypted Input")
            Button(onClick = ::doDecrypt, modifier = Modifier.fillMaxWidth()) { Text("Decrypt") }
            status?.let { (msg, type) -> StatusCard(msg, type) }
            if (output.isNotEmpty()) {
                PgpTextField(value = output, onValueChange = {}, label = "Plaintext Output", readOnly = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(output)) }) { Text("Copy") }
                    OutlinedButton(onClick = { output = ""; input = "" }) { Text("Clear") }
                }
            }
        }
    }
}

// ── Sign ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignWorkflow(keys: List<KeyRecord>, isTablet: Boolean, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var selectedKey by remember { mutableStateOf<KeyRecord?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Pair<String, StatusType>?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val privateKeys = keys.filter { it.hasPrivate }

    fun doSign() {
        val key = selectedKey ?: run { status = "Select a signing key" to StatusType.ERROR; return }
        if (passphrase.isEmpty()) { status = "Enter passphrase" to StatusType.ERROR; return }
        scope.launch {
            status = null
            runCatching {
                withContext(Dispatchers.Default) { PgpService.sign(input, key, passphrase) }
            }.onSuccess { result ->
                output = result
                status = "Signed successfully" to StatusType.SUCCESS
            }.onFailure { e ->
                status = (e.message ?: "Signing failed") to StatusType.ERROR
            }
        }
    }

    val controls: @Composable () -> Unit = {
        SectionLabel("Signing Key")
        ExposedDropdownMenuBox(expanded = dropdownExpanded, onExpandedChange = { dropdownExpanded = it }) {
            OutlinedTextField(
                value = selectedKey?.displayName ?: "Select key…",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
             )
            ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                privateKeys.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(key.label, maxLines = 1) },
                        onClick = { selectedKey = key; dropdownExpanded = false }
                    )
                }
            }
        }
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Passphrase") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (isTablet) {
        Row(
            modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                controls()
                PgpTextField(value = input, onValueChange = { input = it }, label = "Message to Sign", minLines = 8)
                Button(onClick = ::doSign, modifier = Modifier.fillMaxWidth()) { Text("Sign") }
                status?.let { (msg, type) -> StatusCard(msg, type) }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PgpTextField(
                    value = output, onValueChange = {}, label = "Signed Message",
                    readOnly = true, minLines = 6, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(output)) },
                        enabled = output.isNotEmpty()
                    ) { Text("Copy") }
                    OutlinedButton(onClick = { output = ""; input = "" }) { Text("Clear") }
                }
            }
        }
    } else {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            controls()
            PgpTextField(value = input, onValueChange = { input = it }, label = "Message to Sign")
            Button(onClick = ::doSign, modifier = Modifier.fillMaxWidth()) { Text("Sign") }
            status?.let { (msg, type) -> StatusCard(msg, type) }
            if (output.isNotEmpty()) {
                PgpTextField(value = output, onValueChange = {}, label = "Signed Message", readOnly = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(output)) }) { Text("Copy") }
                    OutlinedButton(onClick = { output = ""; input = "" }) { Text("Clear") }
                }
            }
        }
    }
}

// ── Verify ────────────────────────────────────────────────────────────────────

@Composable
private fun VerifyWorkflow(keys: List<KeyRecord>, isTablet: Boolean, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Pair<String, StatusType>?>(null) }

    val publicKeys = keys.filter { it.hasPublic }

    fun doVerify() {
        if (publicKeys.isEmpty()) { status = "No public keys available" to StatusType.ERROR; return }
        scope.launch {
            status = null
            runCatching {
                withContext(Dispatchers.Default) { PgpService.verify(input, publicKeys) }
            }.onSuccess { result ->
                output = result
                status = "Signature verified" to StatusType.SUCCESS
            }.onFailure { e ->
                status = (e.message ?: "Verification failed") to StatusType.ERROR
            }
        }
    }

    if (isTablet) {
        Row(
            modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PgpTextField(
                    value = input, onValueChange = { input = it }, label = "Signed Message",
                    minLines = 6, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = ::doVerify, modifier = Modifier.fillMaxWidth()) { Text("Verify") }
                status?.let { (msg, type) -> Spacer(Modifier.height(8.dp)); StatusCard(msg, type) }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PgpTextField(
                    value = output, onValueChange = {}, label = "Verified Plaintext",
                    readOnly = true, minLines = 6, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(output)) },
                    enabled = output.isNotEmpty()
                ) { Text("Copy") }
            }
        }
    } else {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PgpTextField(value = input, onValueChange = { input = it }, label = "Signed Message")
            Button(onClick = ::doVerify, modifier = Modifier.fillMaxWidth()) { Text("Verify") }
            status?.let { (msg, type) -> StatusCard(msg, type) }
            if (output.isNotEmpty()) {
                PgpTextField(value = output, onValueChange = {}, label = "Verified Plaintext", readOnly = true)
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(output)) }) { Text("Copy") }
            }
        }
    }
}

// ── Passphrase dialog ─────────────────────────────────────────────────────────

@Composable
private fun PassphraseDialog(
    keyName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Passphrase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Passphrase for $keyName", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(passphrase) }) { Text("Decrypt") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
