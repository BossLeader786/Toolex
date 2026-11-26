package com.toolex.pdf.ui.sheets

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.toolex.pdf.engine.PageGeometry
import com.toolex.pdf.engine.SizeEstimate
import com.toolex.pdf.ui.components.SheetScaffold
import com.toolex.pdf.ui.components.SuggestionChips
import com.toolex.pdf.util.FileStore
import com.toolex.pdf.util.MathHeuristics

/**
 * Custom max-size picker.
 *
 * Two ways in, both from the web app's behaviour:
 *  1. suggested chips computed from this job's own estimate, and
 *  2. a free KB/MB entry for manual control.
 *
 * The value can never exceed the request's natural size (that would be
 * meaningless) and never goes under the 10 KB floor.
 */
@Composable
fun CustomSizeSheet(
    estimate: SizeEstimate?,
    currentTarget: Long?,
    isSeparate: Boolean,
    onApply: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val naturalBytes = estimate?.estimatedPdfBytes ?: 0L
    val suggestions = remember(estimate) {
        (estimate?.suggestions ?: emptyList())
            .filter { naturalBytes == 0L || it <= naturalBytes }
            .map { MathHeuristics.roundNice(it) }
            .distinct()
    }
    val initial = currentTarget ?: suggestions.getOrNull(2) ?: (naturalBytes * 0.7).toLong()
    var unitMb by remember { mutableStateOf(initial >= 1024L * 1024L) }
    var text by remember {
        mutableStateOf(
            if (unitMb) String.format(java.util.Locale.US, "%.1f", initial / 1048576.0)
            else "${(initial / 1024L).coerceAtLeast(10)}",
        )
    }

    val parsed = MathHeuristics.parseSize(text, unitMb)
    val capped = parsed?.coerceAtMost(if (naturalBytes > 0) naturalBytes else parsed)
    val floorOk = capped == null || capped >= PageGeometry.MIN_TARGET_BYTES
    val hint = if (capped != null && naturalBytes > 0) {
        MathHeuristics.feasibilityHint(capped, naturalBytes)
    } else null
    val overOriginal = parsed != null && naturalBytes > 0 && parsed > naturalBytes

    SheetScaffold(
        title = if (isSeparate) "Max size per PDF" else "Max PDF size",
        description = "Limit ke andar fit karne ke liye DPI aur JPEG quality apne aap adjust hoti hai. " +
            "Ye ek maximum hai — exact size guarantee nahi.",
        onDismiss = onDismiss,
    ) {
        if (estimate != null) {
            Text(
                "Is job ka natural size: ~${FileStore.formatBytes(estimate.estimatedPdfBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text("Suggested", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            SuggestionChips(
                suggestions = suggestions,
                selected = capped,
                onSelect = { value ->
                    unitMb = value >= 1024L * 1024L
                    text = if (unitMb) String.format(java.util.Locale.US, "%.1f", value / 1048576.0)
                    else "${value / 1024L}"
                },
            )
            Spacer(Modifier.height(18.dp))
        }

        Text("Apna size likho", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        UnitRadioRow(isMb = unitMb, onUnit = { mb ->
            // Convert the typed value so it keeps the same meaning.
            MathHeuristics.parseSize(text, unitMb)?.let { bytes ->
                text = if (mb) String.format(java.util.Locale.US, "%.2f", bytes / 1048576.0)
                else "${(bytes / 1024L).coerceAtLeast(1)}"
            }
            unitMb = mb
        })
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    // digits + single dot only
                    if (input.length <= 8 && input.all { it.isDigit() || it == '.' }) text = input
                },
                singleLine = true,
                isError = !floorOk || overOriginal,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                label = { Text(if (unitMb) "Size (MB)" else "Size (KB)") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (unitMb) "MB" else "KB",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (overOriginal) {
            Spacer(Modifier.height(8.dp))
            Note("Ye original se bada hai — automatically ${FileStore.formatBytes(naturalBytes)} par cap ho jayega.")
        } else if (!floorOk) {
            Spacer(Modifier.height(8.dp))
            Note("Minimum 10 KB hona chahiye.")
        } else if (capped != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Final limit: ${FileStore.formatBytes(capped)}" + if (isSeparate) " per PDF" else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (hint != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { capped?.let(onApply) },
            enabled = capped != null && floorOk,
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text("Apply limit", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
