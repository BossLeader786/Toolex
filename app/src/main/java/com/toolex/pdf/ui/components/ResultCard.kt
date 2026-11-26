package com.toolex.pdf.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toolex.pdf.model.ConversionResult
import com.toolex.pdf.model.TargetStatus
import com.toolex.pdf.util.FileStore

/** Success card: what was produced, whether the size goal was met, and actions. */
@Composable
fun ResultCard(
    result: ConversionResult,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isZip = result.mimeType == "application/zip"
    val accent = if (result.targetStatus == TargetStatus.MISSED) colors.error else colors.primary

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.14f), modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isZip) Icons.Rounded.FolderZip else Icons.Rounded.Description,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isZip) "PDFs ready (ZIP)" else "PDF ready",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        result.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Size", FileStore.formatBytes(result.byteCount), Modifier.weight(1f))
                Stat(
                    if (isZip) "PDFs" else "Pages",
                    "${if (isZip) result.perFileBytes.size else result.pageCount}",
                    Modifier.weight(1f),
                )
                Stat("DPI", "${result.dpiUsed}", Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))

            TargetBadge(result)

            if (result.skipped.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${result.skipped.size} file${if (result.skipped.size > 1) "s" else ""} skip hui: " +
                        result.skipped.joinToString { it.name.take(24) },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
            }

            Spacer(Modifier.height(14.dp))

            SecondaryActions(
                primaryLabel = "Open",
                primaryIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                onPrimary = onOpen,
                secondaryLabel = "Save",
                secondaryIcon = Icons.Rounded.Download,
                onSecondary = onSave,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = onShare,
                shape = RoundedCornerShape(13.dp),
                color = colors.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun TargetBadge(result: ConversionResult) {
    val colors = MaterialTheme.colorScheme
    val (icon, text, tint) = when (result.targetStatus) {
        TargetStatus.MET -> Triple(
            Icons.Rounded.CheckCircle,
            "Target size mil gaya (${result.requestedBytes?.let { FileStore.formatBytes(it) }})",
            colors.primary,
        )
        TargetStatus.MISSED -> Triple(
            Icons.Rounded.ErrorOutline,
            "Requested ${result.requestedBytes?.let { FileStore.formatBytes(it) }} me fit nahi hua — " +
                "ye sabse chhoti possible file hai",
            colors.error,
        )
        TargetStatus.NOT_REQUESTED -> Triple(
            Icons.Rounded.CheckCircle,
            "Normal quality • ${result.durationMs / 1000.0}s",
            colors.onSurfaceVariant,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = tint)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
