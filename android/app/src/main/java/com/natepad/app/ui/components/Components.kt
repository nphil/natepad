package com.natepad.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.natepad.app.data.KeyRecord
import androidx.compose.material.icons.filled.Close
import com.natepad.app.ui.theme.NatepadMotion

// ── Layout constants ──────────────────────────────────────────────────────────

/** Single-pane content on wide screens is capped to this width and centered. */
val ContentMaxWidth: Dp = 840.dp

/** Cap + fill: use inside a horizontally-centering parent. */
fun Modifier.contentWidth(max: Dp = ContentMaxWidth): Modifier =
    this.widthIn(max = max).fillMaxWidth()

// ── Status ────────────────────────────────────────────────────────────────────

enum class StatusType { SUCCESS, ERROR, INFO, WARNING }

/**
 * Status card that springs in/out and cross-fades when the message changes.
 * Pass null to animate it away.
 */
@Composable
fun AnimatedStatusCard(
    status: Pair<String, StatusType>?,
    modifier: Modifier = Modifier
) {
    // Keep the last message around so the exit animation has content to show.
    var lastStatus by remember { mutableStateOf(status) }
    if (status != null) lastStatus = status

    AnimatedVisibility(
        visible = status != null,
        enter = expandVertically(NatepadMotion.spatialDefault()) +
            fadeIn(NatepadMotion.effectsDefault()),
        exit = shrinkVertically(NatepadMotion.spatialFast()) +
            fadeOut(NatepadMotion.effectsFast()),
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = lastStatus,
            transitionSpec = {
                fadeIn(NatepadMotion.effectsDefault()) togetherWith
                    fadeOut(NatepadMotion.effectsFast())
            },
            label = "status_content"
        ) { current ->
            current?.let { (msg, type) -> StatusCard(msg, type) }
        }
    }
}

@Composable
fun StatusCard(
    message: String,
    type: StatusType,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = when (type) {
        StatusType.SUCCESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusType.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusType.INFO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StatusType.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (type) {
                    StatusType.SUCCESS -> Icons.Filled.CheckCircle
                    StatusType.ERROR -> Icons.Outlined.Cancel
                    StatusType.WARNING -> Icons.Filled.Warning
                    StatusType.INFO -> Icons.Outlined.Info
                },
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}

// ── Text fields ───────────────────────────────────────────────────────────────

@Composable
fun PgpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    minLines: Int = 6,
    maxLines: Int = Int.MAX_VALUE,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = if (label.isNotEmpty()) ({ Text(label) }) else null,
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, style = MaterialTheme.typography.bodySmall) }) else null,
        modifier = modifier.fillMaxWidth(),
        readOnly = readOnly,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    )
}

// ── Small pieces ──────────────────────────────────────────────────────────────

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun RecipientChip(
    record: KeyRecord,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(record.displayName, maxLines = 1) },
        trailingIcon = {
            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
        },
        modifier = modifier,
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
fun KeyBadge(
    hasPublic: Boolean,
    hasPrivate: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (hasPublic) {
            Badge(text = "PUB", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        if (hasPrivate) {
            Badge(text = "PRIV", color = MaterialTheme.colorScheme.tertiaryContainer, textColor = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun Badge(text: String, color: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
