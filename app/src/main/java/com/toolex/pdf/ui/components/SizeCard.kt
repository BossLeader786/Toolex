package com.toolex.pdf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toolex.pdf.engine.SizeEstimate
import com.toolex.pdf.model.SizeMode
import com.toolex.pdf.ui.PdfUiState
import com.toolex.pdf.util.FileStore

/**
 * "Output size" card — this is where the special logic of the app lives:
 * source bytes and estimated PDF bytes are shown separately (like PaperLite's
 * update note), and picking Custom opens a target-size sheet.
 */
@Composable
fun SizeCard(
    state: PdfUiState,
    onNormal: () -> Unit,
    onCustom: () -> Unit,
) {
    val estimate: SizeEstimate? = state.estimate
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Output size", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.estimating) {
                    InlineLoading("Estimate…")
                } else if (estimate != null) {
                    Text(
                        "~${FileStore.formatBytes(estimate.estimatedPdfBytes)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Two numbers, clearly separated.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat(
                    label = "Photos",
                    value = FileStore.formatBytes(state.sourceBytesTotal),
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    label = if (state.isSeparate) "Est. PDFs" else "Est. PDF",
                    value = estimate?.let { FileStore.formatBytes(it.estimatedPdfBytes) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeTile(
                    title = "Normal",
                    subtitle = "Auto quality",
                    icon = Icons.Rounded.Bolt,
                    selected = state.sizeMode == SizeMode.NORMAL,
                    onClick = onNormal,
                    modifier = Modifier.weight(1f),
                )
                ModeTile(
                    title = "Custom size",
                    subtitle = state.customTargetBytes?.let { "max ${FileStore.formatBytes(it)}" } ?: "KB / MB limit",
                    icon = Icons.Rounded.Tune,
                    selected = state.sizeMode == SizeMode.CUSTOM,
                    onClick = onCustom,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.sizeMode == SizeMode.CUSTOM) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        buildString {
                            append("Selected size ek maximum limit hai, guarantee nahi. ")
                            if (state.isSeparate) append("Har PDF ke liye alag limit lagegi. ")
                            append("Fit hone ke liye DPI/quality apne aap ghategi.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ModeTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.primary else colors.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Column {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.height(7.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** Chip row with the estimate-derived ceilings. */
@Composable
fun SuggestionChips(
    suggestions: List<Long>,
    selected: Long?,
    onSelect: (Long) -> Unit,
) {
    if (suggestions.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.take(4).forEach { value ->
            val isSelected = selected == value
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onSelect(value) },
            ) {
                Text(
                    FileStore.formatBytes(value),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                )
            }
        }
    }
}
