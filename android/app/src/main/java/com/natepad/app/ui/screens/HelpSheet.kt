package com.natepad.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.natepad.app.ui.theme.NatepadMotion

private data class FaqItem(val question: String, val answer: String)

private val faq = listOf(
    FaqItem(
        "What is PGP?",
        "PGP (Pretty Good Privacy) is a long-established standard for encrypting and " +
            "signing messages. Each person has a key pair: a public key they share and a " +
            "private key they keep secret. Anything encrypted with the public key can only " +
            "be read with the matching private key."
    ),
    FaqItem(
        "How do I send someone an encrypted message?",
        "Import their public key under Keys (paste it, open a .asc file, or scan their QR " +
            "code). Then open Encrypt, pick them as a recipient, type your message, and " +
            "share the encrypted result over any channel — messenger, email, anything. " +
            "Only they can decrypt it."
    ),
    FaqItem(
        "How do people send encrypted messages to me?",
        "Share your public key: open your key under Keys and use Share or the QR code. " +
            "The public key is safe to post anywhere. When someone sends you an encrypted " +
            "message, paste it into Decrypt — or share it straight into NatePad."
    ),
    FaqItem(
        "What's the difference between encrypting and signing?",
        "Encrypting hides the content so only the recipient can read it. Signing proves " +
            "the message came from you and wasn't altered — anyone with your public key " +
            "can check the signature. You can also do both at once with Sign & Encrypt."
    ),
    FaqItem(
        "What if I lose my key or forget its passphrase?",
        "There is no recovery — that's what keeps the encryption trustworthy. Export an " +
            "encrypted backup under Settings and keep it somewhere safe. It's also wise to " +
            "generate a revocation certificate (key detail → Revocation) so you can tell " +
            "everyone to stop using a lost key."
    ),
    FaqItem(
        "Are my keys safe on this device?",
        "Keys are stored encrypted with a hardware-backed key (Android Keystore, " +
            "AES-256-GCM). NatePad has no internet permission, so nothing can leave the " +
            "device. You can additionally require biometric unlock under Settings."
    ),
    FaqItem(
        "How do backups work?",
        "Settings → Export creates one file containing all your keys, encrypted with a " +
            "password you choose (AES-256-GCM, PBKDF2 with 600k iterations). The same " +
            "file restores on both the Android and iOS versions of NatePad."
    ),
    FaqItem(
        "Why does the clipboard clear itself?",
        "Decrypted text you copy is sensitive, so NatePad marks it hidden from clipboard " +
            "previews and clears it after 60 seconds — other apps and clipboard histories " +
            "shouldn't hold onto your plaintext."
    )
)

/** Expandable FAQ presented as a bottom sheet from Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Help & FAQ",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(faq) { item -> FaqCard(item) }
        }
    }
}

@Composable
private fun FaqCard(item: FaqItem) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f, label = "faq_chevron"
    )
    Card(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.question,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(NatepadMotion.spatialDefault()) + fadeIn(NatepadMotion.effectsDefault()),
                exit = shrinkVertically(NatepadMotion.spatialFast()) + fadeOut(NatepadMotion.effectsFast())
            ) {
                Text(
                    text = item.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}
