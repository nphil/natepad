package com.natepad.app.ui.screens

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.natepad.app.ui.components.SectionLabel
import com.natepad.app.ui.components.contentWidth
import com.natepad.app.util.PgpContentDetector
import com.natepad.app.util.PgpContentKind
import com.natepad.app.ui.theme.NatepadMotion

private data class HomeAction(
    val icon: ImageVector,
    val label: String,
    val description: String,
    val mode: CryptoMode
)

private val actions = listOf(
    HomeAction(Icons.Default.Lock, "Encrypt", "Encrypt a message for recipients", CryptoMode.ENCRYPT),
    HomeAction(Icons.Default.LockOpen, "Decrypt", "Decrypt a PGP-encrypted message", CryptoMode.DECRYPT),
    HomeAction(Icons.Outlined.Create, "Sign", "Sign with your private key", CryptoMode.SIGN),
    HomeAction(Icons.Default.VerifiedUser, "Verify", "Verify a signed message", CryptoMode.VERIFY)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onModeSelected: (CryptoMode) -> Unit,
    onNavigateToKeys: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenWithInput: (CryptoMode, String) -> Unit = { mode, _ -> onModeSelected(mode) },
    onImportKey: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var clipboardHasText by remember { mutableStateOf(false) }
    var clipboardNote by remember { mutableStateOf<String?>(null) }

    // Cheap, silent check (no clipboard-access toast) that only looks at the clip
    // description. Keyed on window focus: Android 10+ only exposes the clipboard to
    // the focused app, and focus can arrive after ON_RESUME.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardHasText =
                cm.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
            clipboardNote = null
        }
    }

    fun checkClipboard() {
        clipboardNote = null
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isBlank()) {
            clipboardNote = "The clipboard has no text"
            return
        }
        when (PgpContentDetector.detect(text)) {
            PgpContentKind.ENCRYPTED_MESSAGE -> onOpenWithInput(CryptoMode.DECRYPT, text)
            PgpContentKind.SIGNED_MESSAGE -> onOpenWithInput(CryptoMode.VERIFY, text)
            PgpContentKind.PUBLIC_KEY, PgpContentKind.PRIVATE_KEY -> onImportKey(text)
            null -> clipboardNote = "No PGP block found on the clipboard"
        }
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
                .contentWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Brand header ──────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.shapes.extraLarge
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "NatePad",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Encrypt, sign and manage your PGP keys",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // ── Clipboard quick action ────────────────────────────────────────
            ClipboardCard(
                visible = clipboardHasText,
                note = clipboardNote,
                onClick = ::checkClipboard
            )

            // ── Operations ────────────────────────────────────────────────────
            Column {
                SectionLabel("Operations", modifier = Modifier.padding(bottom = 12.dp))
                // Columns follow the available width, not the device type, so the
                // grid reflows continuously in split-screen / desktop windows.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    // ~180dp per card, like GridCells.Adaptive — reflows continuously
                    // as the window is resized, between 2 and 4 columns.
                    val columns = (maxWidth / 180.dp).toInt().coerceIn(2, 4)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions.chunked(columns).forEach { rowActions ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max)
                            ) {
                                rowActions.forEach { action ->
                                    ActionCard(
                                        action = action,
                                        onClick = { onModeSelected(action.mode) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                                // Keep last row's cards the same size as the others.
                                repeat(columns - rowActions.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // ── Key management ────────────────────────────────────────────────
            Column {
                SectionLabel("Key Management", modifier = Modifier.padding(bottom = 12.dp))
                PressableCard(
                    onClick = onNavigateToKeys,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                    MaterialTheme.shapes.medium
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        ) {
                            Text(
                                text = "Manage Keys",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Generate, import, export and delete PGP keys",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

@Composable
private fun ClipboardCard(
    visible: Boolean,
    note: String?,
    onClick: () -> Unit
) {
    Column {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(NatepadMotion.spatialDefault()) + fadeIn(NatepadMotion.effectsDefault()),
            exit = shrinkVertically(NatepadMotion.spatialFast()) + fadeOut(NatepadMotion.effectsFast())
        ) {
            PressableCard(
                onClick = onClick,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Text(
                            text = "Check Clipboard",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Open a copied PGP message, signature, or key",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        AnimatedVisibility(visible = note != null) {
            Text(
                text = note.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionCard(
    action: HomeAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PressableCard(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = action.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = action.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Card with expressive press feedback: springs down to 97% scale while pressed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PressableCard(
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = NatepadMotion.spatialFast(),
        label = "press_scale"
    )
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}
