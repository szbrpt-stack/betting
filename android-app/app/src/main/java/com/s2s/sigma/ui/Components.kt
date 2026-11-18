package com.s2s.sigma.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.s2s.sigma.data.EventStatus
import com.s2s.sigma.data.SigmaVerdict
import com.s2s.sigma.ui.theme.*

@Composable
fun SigmaHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
        } else {
            SigmaMark(42.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SigmaMark(size: androidx.compose.ui.unit.Dp = 48.dp) {
    Box(
        Modifier.size(size).clip(MaterialTheme.shapes.medium).background(Cyan),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Σ",
            color = DeepNavy,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun DemoTag(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Warning.copy(alpha = .12f),
        contentColor = Warning,
        shape = CircleShape,
        border = BorderStroke(1.dp, Warning.copy(alpha = .35f)),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Warning))
            Spacer(Modifier.width(6.dp))
            Text("DATOS DEMO", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SigmaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
    loading: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = MutedText) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Cyan) },
        trailingIcon = when {
            loading -> ({ CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) })
            value.isNotBlank() -> ({
                Row {
                    if (onSubmit != null) {
                        IconButton(onClick = onSubmit) { Icon(Icons.Default.ArrowForward, "Buscar") }
                    }
                    IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Close, "Limpiar") }
                }
            })
            else -> null
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSubmit?.invoke() }),
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DarkPanel.copy(alpha = .82f),
            unfocusedContainerColor = DarkPanel.copy(alpha = .62f),
            focusedBorderColor = Cyan,
            unfocusedBorderColor = Border,
        ),
    )
}

@Composable
fun SigmaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier.then(interaction),
        color = DarkPanel.copy(alpha = .86f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, (accent ?: Border).copy(alpha = if (accent == null) .55f else .45f)),
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (accent != null) Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
            Column(
                modifier = Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SectionTitle(
    text: String,
    eyebrow: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            eyebrow?.let {
                Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = Cyan)
                Spacer(Modifier.height(3.dp))
            }
            Text(text, style = MaterialTheme.typography.titleLarge)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun StatusPill(status: EventStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        EventStatus.SCHEDULED -> "Programado" to Cyan
        EventStatus.LIVE -> "En vivo" to ErrorRed
        EventStatus.FINISHED -> "Finalizado" to Success
        EventStatus.POSTPONED -> "Aplazado" to Warning
        EventStatus.CANCELLED -> "Cancelado" to MutedText
    }
    LabelPill(label, color, modifier)
}

@Composable
fun VerdictPill(verdict: SigmaVerdict, modifier: Modifier = Modifier) {
    val color = when (verdict) {
        SigmaVerdict.SUPPORTED -> Success
        SigmaVerdict.CONDITIONAL -> Warning
        SigmaVerdict.DESCRIPTIVE -> Cyan
        SigmaVerdict.INSUFFICIENT -> ErrorRed
    }
    LabelPill(verdict.name, color, modifier)
}

@Composable
fun LabelPill(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = .12f),
        contentColor = color,
        shape = CircleShape,
        border = BorderStroke(1.dp, color.copy(alpha = .28f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = Cyan,
) {
    Surface(
        modifier = modifier,
        color = ElevatedPanel.copy(alpha = .72f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Border.copy(alpha = .5f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = PrimaryText)
            Text(label, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = Cyan, modifier: Modifier = Modifier) {
    Box(
        modifier.size(42.dp).clip(MaterialTheme.shapes.medium).background(tint.copy(alpha = .12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun EmptyState(
    title: String,
    detail: String,
    icon: ImageVector = Icons.Default.SearchOff,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Cyan.copy(alpha = .1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun DetailRow(icon: ImageVector, label: String, value: String, tint: Color = Cyan) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MutedText)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = PrimaryText)
        }
    }
}
