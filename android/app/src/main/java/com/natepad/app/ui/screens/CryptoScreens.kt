package com.natepad.app.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.natepad.app.data.KeyRecord
import com.natepad.app.data.KeyRepository
import com.natepad.app.pgp.PgpService
import com.natepad.app.ui.components.AnimatedStatusCard
import com.natepad.app.ui.components.ContentMaxWidth
import com.natepad.app.ui.components.PgpTextField
import com.natepad.app.ui.components.RecipientChip
import com.natepad.app.ui.components.SectionLabel
import com.natepad.app.ui.components.StatusType
import com.natepad.app.ui.components.contentWidth
import com.natepad.app.util.SecureClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.natepad.app.ui.theme.NatepadMotion

enum class CryptoMode(val label: String) {
    ENCRYPT("Encrypt"),
    DECRYPT("Decrypt"),
    SIGN("Sign"),
    VERIFY("Verify")
}

// ── Per-mode state, retained across mode switches ─────────────────────────────
//
// Each workflow's text and selections live here (not in the workflow composable),
// so switching modes — or resizing the window — never loses what you typed.

@Stable
internal open class ModeState {
    var input by mutableStateOf("")
    var output by mutableStateOf("")
    var status by mutableStateOf<Pair<String, StatusType>?>(null)
    var working by mutableStateOf(false)
}

@Stable
internal class EncryptState : ModeState() {
    val recipients = mutableStateListOf<KeyRecord>()
    var signingKey by mutableStateOf<KeyRecord?>(null)
    var passphrase by mutableStateOf("")
}

@Stable
internal class SignState : ModeState() {
    var selectedKey by mutableStateOf<KeyRecord?>(null)
    var passphrase by mutableStateOf("")
}

@Stable
internal class CryptoScreenState {
    val encrypt = EncryptState()
    val decrypt = ModeState()
    val sign = SignState()
    val verify = ModeState()

    fun stateFor(mode: CryptoMode): ModeState = when (mode) {
        CryptoMode.ENCRYPT -> encrypt
        CryptoMode.DECRYPT -> decrypt
        CryptoMode.SIGN -> sign
        CryptoMode.VERIFY -> verify
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CryptoScreen(
    states: CryptoScreenState,
    initialMode: CryptoMode = CryptoMode.ENCRYPT,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { KeyRepository.getInstance(context) }
    val keys by repo.keys.collectAsState()

    var selectedMode by remember { mutableStateOf(initialMode) }

    // Expanded windows (tablets in landscape, large desktop windows) get the
    // input/output side by side; everything narrower stacks vertically.
    val isWide = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)


    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("PGP Notepad") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .contentWidth(max = if (isWide) 560.dp else ContentMaxWidth)
                    .padding(horizontal = 16.dp)
            ) {
                CryptoMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = CryptoMode.entries.size),
                        icon = {}
                    ) {
                        Text(mode.label, maxLines = 1)
                    }
                }
            }

            AnimatedContent(
                targetState = selectedMode,
                transitionSpec = {
                    // Shared-axis X toward the newly selected segment.
                    val toRight = targetState.ordinal > initialState.ordinal
                    val spatial = NatepadMotion.spatialDefault<IntOffset>()
                    (slideInHorizontally(spatial) { if (toRight) it / 6 else -it / 6 } +
                        fadeIn(NatepadMotion.effectsDefault())) togetherWith
                        (slideOutHorizontally(spatial) { if (toRight) -it / 6 else it / 6 } +
                            fadeOut(NatepadMotion.effectsFast()))
                },
                label = "crypto_mode",
                modifier = Modifier.fillMaxSize()
            ) { mode ->
                when (mode) {
                    CryptoMode.ENCRYPT -> EncryptWorkflow(states.encrypt, keys, isWide)
                    CryptoMode.DECRYPT -> DecryptWorkflow(states.decrypt, keys, isWide)
                    CryptoMode.SIGN -> SignWorkflow(states.sign, keys, isWide)
                    CryptoMode.VERIFY -> VerifyWorkflow(states.verify, keys, isWide)
                }
            }
        }
    }
}

// ── Shared workflow scaffolding ───────────────────────────────────────────────

/**
 * Lays a workflow out as one scrolling column on compact windows, or as
 * input | output panes side by side on expanded windows.
 */
@Composable
private fun WorkflowLayout(
    isWide: Boolean,
    inputContent: @Composable () -> Unit,
    outputContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isWide) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .wrapContentWidth()
                .contentWidth(max = 1200.dp)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) { inputContent() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) { outputContent() }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .wrapContentWidth()
                .contentWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            inputContent()
            outputContent()
        }
    }
}

/** Primary action button with built-in progress state. */
@Composable
private fun ActionButton(
    label: String,
    working: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled && !working,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        if (working) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Output block: monospace result + copy/share/clear actions, springs in when produced. */
@Composable
private fun OutputSection(
    label: String,
    output: String,
    sensitive: Boolean,
    onCopied: (Pair<String, StatusType>?) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    AnimatedVisibility(
        visible = output.isNotEmpty(),
        enter = expandVertically(NatepadMotion.spatialDefault()) + fadeIn(NatepadMotion.effectsDefault()),
        exit = shrinkVertically(NatepadMotion.spatialFast()) + fadeOut(NatepadMotion.effectsFast())
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PgpTextField(value = output, onValueChange = {}, label = label, readOnly = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (sensitive) {
                        SecureClipboard.copySensitive(context, output)
                        onCopied("Copied — the clipboard clears itself in 60 s" to StatusType.INFO)
                    } else {
                        SecureClipboard.copy(context, output)
                        onCopied("Copied to clipboard" to StatusType.INFO)
                    }
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                if (!sensitive) {
                    OutlinedButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, output)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

// ── Encrypt ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncryptWorkflow(state: EncryptState, keys: List<KeyRecord>, isWide: Boolean) {
    val scope = rememberCoroutineScope()
    val publicKeys = keys.filter { it.hasPublic }
    val privateKeys = keys.filter { it.hasPrivate }

    fun doEncrypt() {
        if (state.recipients.isEmpty()) {
            state.status = "Select at least one recipient" to StatusType.ERROR; return
        }
        if (state.signingKey != null && state.passphrase.isEmpty()) {
            // Never fall back to an unsigned message silently.
            state.status = "Enter the signing key's passphrase (or set signing to None)" to StatusType.ERROR
            return
        }
        scope.launch {
            state.status = null
            state.working = true
            runCatching {
                withContext(Dispatchers.Default) {
                    PgpService.encrypt(
                        state.input, state.recipients.toList(),
                        state.signingKey,
                        state.passphrase.ifEmpty { null }
                    )
                }
            }.onSuccess { result ->
                state.output = result
                state.status = if (state.signingKey != null) {
                    "Signed and encrypted successfully" to StatusType.SUCCESS
                } else {
                    "Encrypted successfully" to StatusType.SUCCESS
                }
            }.onFailure { e ->
                state.status = (e.message ?: "Encryption failed") to StatusType.ERROR
            }
            state.working = false
        }
    }

    WorkflowLayout(
        isWide = isWide,
        inputContent = {
            SectionLabel("Recipients")
            if (state.recipients.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.recipients.forEach { rec ->
                        RecipientChip(record = rec, onRemove = { state.recipients.remove(rec) })
                    }
                }
            }
            if (publicKeys.isNotEmpty()) {
                KeyDropdown(
                    label = "Add recipient…",
                    value = null,
                    options = publicKeys.filter { it !in state.recipients },
                    onSelect = { rec -> rec?.let { if (it !in state.recipients) state.recipients.add(it) } },
                    includeNone = false
                )
            } else {
                Text(
                    "No public keys yet — import or generate one under Keys.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionLabel("Sign with (optional)")
            KeyDropdown(
                label = "None",
                value = state.signingKey,
                options = privateKeys,
                onSelect = { state.signingKey = it },
                includeNone = true
            )
            AnimatedVisibility(visible = state.signingKey != null) {
                PassphraseField(
                    value = state.passphrase,
                    onValueChange = { state.passphrase = it },
                    label = "Signing passphrase"
                )
            }

            PgpTextField(
                value = state.input,
                onValueChange = { state.input = it },
                label = "Plaintext",
                minLines = if (isWide) 10 else 6
            )
            ActionButton(
                label = if (state.signingKey != null) "Sign & Encrypt" else "Encrypt",
                working = state.working,
                enabled = state.input.isNotEmpty(),
                onClick = ::doEncrypt
            )
            AnimatedStatusCard(state.status)
        },
        outputContent = {
            OutputSection(
                label = "Encrypted message",
                output = state.output,
                sensitive = false,
                onCopied = { state.status = it },
                onClear = { state.output = ""; state.status = null }
            )
        }
    )
}

// ── Decrypt ───────────────────────────────────────────────────────────────────

@Composable
private fun DecryptWorkflow(state: ModeState, keys: List<KeyRecord>, isWide: Boolean) {
    val scope = rememberCoroutineScope()
    var passphraseDialogKey by remember { mutableStateOf<KeyRecord?>(null) }

    val privateKeys = keys.filter { it.hasPrivate }
    val publicKeys = keys.filter { it.hasPublic }

    fun statusFor(sig: PgpService.SignatureStatus): Pair<String, StatusType> = when (sig) {
        PgpService.SignatureStatus.NotSigned ->
            "Decrypted successfully" to StatusType.SUCCESS
        is PgpService.SignatureStatus.Valid ->
            "Decrypted • signature by ${sig.signer ?: sig.keyId} verified" to StatusType.SUCCESS
        is PgpService.SignatureStatus.UnknownKey ->
            "Decrypted • signed by unknown key ${sig.keyId} (import the sender's public key to verify)" to StatusType.WARNING
        is PgpService.SignatureStatus.Invalid ->
            "Decrypted, but the signature is INVALID: ${sig.reason}" to StatusType.WARNING
    }

    fun runDecrypt(passphrase: String?) {
        scope.launch {
            state.status = null
            state.working = true
            runCatching {
                withContext(Dispatchers.Default) {
                    PgpService.decrypt(state.input, privateKeys, publicKeys) { passphrase }
                }
            }.onSuccess { result ->
                state.output = result.plaintext
                state.status = statusFor(result.signature)
            }.onFailure { e ->
                when (e) {
                    is PgpService.PassphraseRequiredException -> passphraseDialogKey = e.record
                    is PgpService.WrongPassphraseException -> {
                        state.status = "Wrong passphrase for ${e.record.displayName} — try again" to StatusType.ERROR
                        passphraseDialogKey = e.record
                    }
                    else -> state.status = (e.message ?: "Decryption failed") to StatusType.ERROR
                }
            }
            state.working = false
        }
    }

    fun doDecrypt() {
        if (privateKeys.isEmpty()) {
            state.status = "No private keys available" to StatusType.ERROR; return
        }
        runDecrypt(null)
    }

    passphraseDialogKey?.let { keyRecord ->
        PassphraseDialog(
            keyName = keyRecord.displayName,
            onConfirm = { pp -> passphraseDialogKey = null; runDecrypt(pp) },
            onDismiss = { passphraseDialogKey = null }
        )
    }

    WorkflowLayout(
        isWide = isWide,
        inputContent = {
            PgpTextField(
                value = state.input,
                onValueChange = { state.input = it },
                label = "Encrypted message",
                placeholder = "-----BEGIN PGP MESSAGE-----\n…",
                minLines = if (isWide) 10 else 6
            )
            ActionButton(
                label = "Decrypt",
                working = state.working,
                enabled = state.input.isNotEmpty(),
                onClick = ::doDecrypt
            )
            AnimatedStatusCard(state.status)
        },
        outputContent = {
            OutputSection(
                label = "Plaintext",
                output = state.output,
                sensitive = true,
                onCopied = { state.status = it },
                onClear = { state.output = ""; state.status = null }
            )
        }
    )
}

// ── Sign ──────────────────────────────────────────────────────────────────────

@Composable
private fun SignWorkflow(state: SignState, keys: List<KeyRecord>, isWide: Boolean) {
    val scope = rememberCoroutineScope()
    val privateKeys = keys.filter { it.hasPrivate }

    fun doSign() {
        val key = state.selectedKey
            ?: run { state.status = "Select a signing key" to StatusType.ERROR; return }
        if (state.passphrase.isEmpty()) {
            state.status = "Enter the key's passphrase" to StatusType.ERROR; return
        }
        scope.launch {
            state.status = null
            state.working = true
            runCatching {
                withContext(Dispatchers.Default) { PgpService.sign(state.input, key, state.passphrase) }
            }.onSuccess { result ->
                state.output = result
                state.status = "Signed successfully" to StatusType.SUCCESS
            }.onFailure { e ->
                state.status = (e.message ?: "Signing failed") to StatusType.ERROR
            }
            state.working = false
        }
    }

    WorkflowLayout(
        isWide = isWide,
        inputContent = {
            SectionLabel("Signing key")
            KeyDropdown(
                label = "Select key…",
                value = state.selectedKey,
                options = privateKeys,
                onSelect = { state.selectedKey = it },
                includeNone = false
            )
            PassphraseField(
                value = state.passphrase,
                onValueChange = { state.passphrase = it },
                label = "Passphrase"
            )
            PgpTextField(
                value = state.input,
                onValueChange = { state.input = it },
                label = "Message to sign",
                minLines = if (isWide) 10 else 6
            )
            ActionButton(
                label = "Sign",
                working = state.working,
                enabled = state.input.isNotEmpty(),
                onClick = ::doSign
            )
            AnimatedStatusCard(state.status)
        },
        outputContent = {
            OutputSection(
                label = "Signed message",
                output = state.output,
                sensitive = false,
                onCopied = { state.status = it },
                onClear = { state.output = ""; state.status = null }
            )
        }
    )
}

// ── Verify ────────────────────────────────────────────────────────────────────

@Composable
private fun VerifyWorkflow(state: ModeState, keys: List<KeyRecord>, isWide: Boolean) {
    val scope = rememberCoroutineScope()
    val publicKeys = keys.filter { it.hasPublic }

    fun doVerify() {
        if (publicKeys.isEmpty()) {
            state.status = "No public keys available" to StatusType.ERROR; return
        }
        scope.launch {
            state.status = null
            state.working = true
            runCatching {
                withContext(Dispatchers.Default) { PgpService.verify(state.input, publicKeys) }
            }.onSuccess { result ->
                state.output = result
                state.status = "Signature verified" to StatusType.SUCCESS
            }.onFailure { e ->
                state.status = (e.message ?: "Verification failed") to StatusType.ERROR
            }
            state.working = false
        }
    }

    WorkflowLayout(
        isWide = isWide,
        inputContent = {
            PgpTextField(
                value = state.input,
                onValueChange = { state.input = it },
                label = "Signed message",
                placeholder = "-----BEGIN PGP SIGNED MESSAGE-----\n…",
                minLines = if (isWide) 10 else 6
            )
            ActionButton(
                label = "Verify",
                working = state.working,
                enabled = state.input.isNotEmpty(),
                onClick = ::doVerify
            )
            AnimatedStatusCard(state.status)
        },
        outputContent = {
            OutputSection(
                label = "Verified plaintext",
                output = state.output,
                sensitive = true,
                onCopied = { state.status = it },
                onClear = { state.output = ""; state.status = null }
            )
        }
    )
}

// ── Shared inputs ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyDropdown(
    label: String,
    value: KeyRecord?,
    options: List<KeyRecord>,
    onSelect: (KeyRecord?) -> Unit,
    includeNone: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value?.displayName ?: label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (includeNone) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = { onSelect(null); expanded = false }
                )
            }
            options.forEach { key ->
                DropdownMenuItem(
                    text = { Text(key.label, maxLines = 1) },
                    onClick = { onSelect(key); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { }),
        modifier = Modifier.fillMaxWidth()
    )
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (passphrase.isNotEmpty()) onConfirm(passphrase) }),
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
