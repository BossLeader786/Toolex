package com.toolex.pdf.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.toolex.pdf.model.FitMode
import com.toolex.pdf.model.MarginSize
import com.toolex.pdf.model.OutputMode
import com.toolex.pdf.model.Orientation
import com.toolex.pdf.model.PageSize
import com.toolex.pdf.ui.components.SheetScaffold

/** A single selectable line with a check mark. */
@Composable
internal fun SelectableLine(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: String? = null,
    enabled: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun PageSizeSheet(
    selected: PageSize,
    onSelect: (PageSize) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetScaffold(
        title = "Page size",
        description = "Photo kitni badi page par baithegi. Original = photo ke apne proportion.",
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageSize.entries.forEach { size ->
                SelectableLine(
                    title = size.label,
                    subtitle = when (size) {
                        PageSize.A4 -> "210 × 297 mm · documents, print ke liye best"
                        PageSize.A5 -> "148 × 210 mm · chhoti booklet"
                        PageSize.LETTER -> "8.5 × 11 in · US/Canada printing"
                        PageSize.ORIGINAL -> "Photo ke exact pixels (96 DPI par map)"
                    },
                    selected = size == selected,
                    onClick = { onSelect(size) },
                )
            }
        }
    }
}

@Composable
fun OrientationSheet(
    selected: Orientation,
    onSelect: (Orientation) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetScaffold(
        title = "Orientation",
        description = "Auto har photo ke hisaab se portrait/landscape chunta hai.",
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Orientation.entries.forEach { orientation ->
                SelectableLine(
                    title = orientation.label,
                    subtitle = when (orientation) {
                        Orientation.AUTO -> "Landscape photo → landscape page"
                        Orientation.PORTRAIT -> "Sab pages khade"
                        Orientation.LANDSCAPE -> "Sab pages aade"
                    },
                    selected = orientation == selected,
                    onClick = { onSelect(orientation) },
                )
            }
        }
    }
}

@Composable
fun FitSheet(
    selected: FitMode,
    onSelect: (FitMode) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetScaffold(
        title = "Photo kaise baithe",
        description = "Aspect ratio match na ho to kya karein.",
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FitMode.entries.forEach { fit ->
                SelectableLine(
                    title = fit.label,
                    subtitle = fit.hint,
                    selected = fit == selected,
                    onClick = { onSelect(fit) },
                )
            }
        }
    }
}

@Composable
fun MarginSheet(
    selected: MarginSize,
    onSelect: (MarginSize) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetScaffold(
        title = "Margins",
        description = "Page ke kinare par khaali jagah (print safe area ke liye useful).",
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MarginSize.entries.forEach { margin ->
                SelectableLine(
                    title = margin.label,
                    subtitle = when (margin) {
                        MarginSize.NONE -> "Poora page photo ke liye"
                        MarginSize.SMALL -> "6.3 mm"
                        MarginSize.MEDIUM -> "12.7 mm"
                        MarginSize.LARGE -> "19 mm"
                    },
                    selected = margin == selected,
                    onClick = { onSelect(margin) },
                )
            }
        }
    }
}

@Composable
fun OutputModeSheet(
    selected: OutputMode,
    imageCount: Int,
    onSelect: (OutputMode) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetScaffold(
        title = "Output",
        description = if (imageCount <= 1) {
            "Ek image hai — merged aur separate dono same PDF denge."
        } else {
            "Ek PDF chahiye ya har photo ki alag PDF?"
        },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutputMode.entries.forEach { mode ->
                SelectableLine(
                    title = mode.label,
                    subtitle = mode.hint,
                    selected = mode == selected,
                    enabled = imageCount > 1 || mode == OutputMode.MERGED,
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

/** Just a visual radio row, used inside the custom-size sheet. */
@Composable
internal fun UnitRadioRow(isMb: Boolean, onUnit: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(false to "KB", true to "MB").forEach { (mb, label) ->
            Row(
                modifier = Modifier
                    .selectable(selected = isMb == mb, onClick = { onUnit(mb) })
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isMb == mb, onClick = { onUnit(mb) }, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
