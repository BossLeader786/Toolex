package com.toolex.pdf.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toolex.pdf.engine.ImageLoader
import com.toolex.pdf.model.ImageInfo
import com.toolex.pdf.model.MarginSize
import com.toolex.pdf.model.PageSize
import com.toolex.pdf.ui.components.EmptyStateCard
import com.toolex.pdf.ui.components.GradientButton
import com.toolex.pdf.ui.components.ImageStrip
import com.toolex.pdf.ui.components.OptionRow
import com.toolex.pdf.ui.components.PagePreview
import com.toolex.pdf.ui.components.ResultCard
import com.toolex.pdf.ui.components.SizeCard
import com.toolex.pdf.ui.sheets.CustomSizeSheet
import com.toolex.pdf.ui.sheets.FitSheet
import com.toolex.pdf.ui.sheets.MarginSheet
import com.toolex.pdf.ui.sheets.OrientationSheet
import com.toolex.pdf.ui.sheets.OutputModeSheet
import com.toolex.pdf.ui.sheets.PageSizeSheet
import com.toolex.pdf.util.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolexScreen(viewModel: PdfViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // --- photo picker ------------------------------------------------------
    // Primary: Android's system photo picker (no permissions, nice UI).
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = PdfViewModel.MAX_IMAGES),
    ) { uris -> viewModel.addImages(uris) }

    // Fallback: for devices/launchers where the system picker is unavailable.
    val pickDocuments = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.addImages(uris) }

    val launchPicker: () -> Unit = {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        runCatching { pickPhotos.launch(request) }
            .onFailure { runCatching { pickDocuments.launch(arrayOf("image/*")) } }
    }

    // --- preview needs the first image's dimensions -------------------------
    var firstImageInfo by remember { mutableStateOf<ImageInfo?>(null) }
    LaunchedEffect(state.images.firstOrNull()?.uri) {
        val first = state.images.firstOrNull()
        firstImageInfo = if (first == null) null else withContext(Dispatchers.IO) {
            runCatching { ImageLoader(context).inspect(android.net.Uri.parse(first.uri)) }.getOrNull()
        }
    }

    // --- toasts / errors ----------------------------------------------------
    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                Header(
                    imageCount = state.imageCount,
                    onClear = viewModel::clearImages,
                )

                Spacer(Modifier.height(6.dp))

                if (state.images.isEmpty()) {
                    EmptyStateCard(onPick = launchPicker)
                } else {
                    ImageStrip(
                        images = state.images,
                        onAdd = launchPicker,
                        onRemove = viewModel::removeImage,
                        onMove = viewModel::moveImage,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Preview + quick facts
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PagePreview(
                            options = state.options,
                            image = firstImageInfo,
                            modifier = Modifier
                                .width(96.dp)
                                .height(132.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                if (state.imageCount <= 1) "1 page PDF" else "${state.imageCount} pages PDF",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${state.pageSizeLabel} · ${state.orientationLabel} · ${state.fitLabel}" +
                                    if (state.options.margins != MarginSize.NONE) " · ${state.marginLabel}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                if (state.isSeparate) "Separate PDFs → ZIP" else "Ek merged PDF",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                SizeCard(
                    state = state,
                    onNormal = viewModel::setSizeNormal,
                    onCustom = { viewModel.openSheet(Sheet.CustomSize) },
                )

                Spacer(Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        OptionRow(
                            icon = Icons.Rounded.AspectRatio,
                            title = "Page size",
                            value = state.pageSizeLabel,
                            onClick = { viewModel.openSheet(Sheet.PageSize) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        OptionRow(
                            icon = Icons.Rounded.ScreenRotation,
                            title = "Orientation",
                            value = state.orientationLabel,
                            enabled = state.options.pageSize != PageSize.ORIGINAL,
                            onClick = { viewModel.openSheet(Sheet.Orientation) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        OptionRow(
                            icon = Icons.Rounded.Crop,
                            title = "Fit / Fill",
                            value = state.fitLabel,
                            onClick = { viewModel.openSheet(Sheet.Fit) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        OptionRow(
                            icon = Icons.Rounded.CropFree,
                            title = "Margins",
                            value = state.marginLabel,
                            enabled = state.options.pageSize != PageSize.ORIGINAL,
                            onClick = { viewModel.openSheet(Sheet.Margins) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        OptionRow(
                            icon = Icons.Rounded.Layers,
                            title = "Output",
                            value = if (state.isSeparate) "ZIP" else "Merged",
                            enabled = state.imageCount > 1,
                            onClick = { viewModel.openSheet(Sheet.Output) },
                        )
                    }
                }

                state.result?.let { result ->
                    Spacer(Modifier.height(14.dp))
                    ResultCard(
                        result = result,
                        onOpen = viewModel::openResult,
                        onShare = viewModel::shareResult,
                        onSave = {
                            viewModel.saveResultToDownloads { location ->
                                Toast.makeText(context, "Saved: $location", Toast.LENGTH_LONG).show()
                            }
                        },
                        onDismiss = viewModel::dismissResult,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // CTA
                if (state.running) {
                    RunningCard(
                        progressPercent = state.progress?.percent ?: 0,
                        label = state.progress?.label ?: "Kaam chal raha hai…",
                        onCancel = viewModel::cancelConversion,
                    )
                } else {
                    GradientButton(
                        text = when {
                            state.imageCount == 0 -> "Images add karke shuru karo"
                            state.isSeparate -> "${state.imageCount} PDFs banao"
                            else -> "PDF banao"
                        },
                        icon = Icons.Rounded.PictureAsPdf,
                        enabled = state.imageCount > 0,
                        onClick = viewModel::convert,
                    )
                }

                if (state.skippedNotice.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        state.skippedNotice.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Sab kuch is phone par process hota hai · max ${PdfViewModel.MAX_IMAGES} images / " +
                        FileStore.formatBytes(PdfViewModel.MAX_TOTAL_BYTES),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(24.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    // --- sheets -------------------------------------------------------------
    when (val sheet = state.sheet) {
        Sheet.PageSize -> PageSizeSheet(
            selected = state.options.pageSize,
            onSelect = { viewModel.setPageSize(it); viewModel.openSheet(null) },
            onDismiss = { viewModel.openSheet(null) },
        )
        Sheet.Orientation -> OrientationSheet(
            selected = state.options.orientation,
            onSelect = { viewModel.setOrientation(it); viewModel.openSheet(null) },
            onDismiss = { viewModel.openSheet(null) },
        )
        Sheet.Fit -> FitSheet(
            selected = state.options.fit,
            onSelect = { viewModel.setFit(it); viewModel.openSheet(null) },
            onDismiss = { viewModel.openSheet(null) },
        )
        Sheet.Margins -> MarginSheet(
            selected = state.options.margins,
            onSelect = { viewModel.setMargins(it); viewModel.openSheet(null) },
            onDismiss = { viewModel.openSheet(null) },
        )
        Sheet.Output -> OutputModeSheet(
            selected = state.options.outputMode,
            imageCount = state.imageCount,
            onSelect = { viewModel.setOutputMode(it); viewModel.openSheet(null) },
            onDismiss = { viewModel.openSheet(null) },
        )
        Sheet.CustomSize -> CustomSizeSheet(
            estimate = state.estimate,
            currentTarget = state.customTargetBytes,
            isSeparate = state.isSeparate,
            onApply = { viewModel.setCustomTarget(it); viewModel.openSheet(null) },
            onDismiss = { viewModel.openSheet(null) },
        )
        null -> Unit
    }
}

@Composable
private fun Header(imageCount: Int, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                Icons.Rounded.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(10.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("Toolex", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Images → PDF, offline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (imageCount > 0) {
            TextButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear")
            }
        }
    }
}

@Composable
private fun RunningCard(progressPercent: Int, label: String, onCancel: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$progressPercent%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCancel, modifier = Modifier.clip(CircleShape).size(30.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}
