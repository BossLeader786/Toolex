package com.toolex

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun bytes(n: Long) = java.text.NumberFormat.getIntegerInstance().format(n) + " B"

@Composable
fun Thumb(vm: Workspace, p: Photo, side: Int, modifier: Modifier) {
    val bitmap by
        produceState<Bitmap?>(null, p, side) {
            value =
                withContext(Dispatchers.IO) {
                    try {
                        vm.studio.decode(p, side)
                    } catch (e: Exception) {
                        null
                    } catch (e: OutOfMemoryError) {
                        null
                    }
                }
        }
    bitmap?.let { Image(it.asImageBitmap(), p.name, modifier) } ?: Spacer(modifier)
}

@Composable
fun PdfScreen(vm: Workspace, connect: () -> Unit) {
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            vm.import(it, false)
        }
    var options by rememberSaveable { mutableStateOf(false) }
    val pages = vm.pages
    var arrange by rememberSaveable { mutableStateOf(true) }
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.fillMaxWidth().height(if (pages.isEmpty()) 190.dp else 100.dp).clickable {
            if (!vm.importing) picker.launch(arrayOf("image/*"))
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = min(size.width / 2, size.height * .91f)
            val path =
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(1f, size.height)
                    lineTo(1f, r)
                    cubicTo(1f, -r * .32f, size.width - 1, -r * .32f, size.width - 1, r)
                    lineTo(size.width - 1, size.height)
                }
            drawPath(
                path,
                ink.copy(alpha = .65f),
                style =
                    androidx.compose.ui.graphics.drawscope.Stroke(
                        1.dp.toPx(),
                        pathEffect =
                            androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(5f, 5f)
                            ),
                    ),
            )
            drawLine(
                ink,
                androidx.compose.ui.geometry.Offset(0f, size.height),
                androidx.compose.ui.geometry.Offset(size.width, size.height),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(42.dp))
            Text("Add Images", style = MaterialTheme.typography.titleMedium)
            Text(
                if (vm.importing) "Reading images…" else "Choose photos from your device",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Entry(
        "Arrange Pages",
        if (pages.isEmpty()) "Add images to arrange pages"
        else "${pages.size} pages · original ${bytes(pages.sumOf { it.bytes })}",
        Icons.Outlined.Collections,
    ) {
        if (pages.isEmpty()) picker.launch(arrayOf("image/*")) else arrange = !arrange
    }
    if (pages.isNotEmpty() && arrange) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(pages) { i, p ->
                Column(Modifier.width(154.dp)) {
                    Thumb(vm, p, 224, Modifier.fillMaxWidth().height(94.dp))
                    Text(
                        "${i+1} · ${p.name}",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(bytes(p.bytes), style = MaterialTheme.typography.bodySmall)
                    Row {
                        IconButton(onClick = { vm.move(i, i - 1) }, enabled = i > 0) {
                            Icon(Icons.Outlined.ArrowBack, "Move earlier")
                        }
                        IconButton(onClick = { vm.move(i, i + 1) }, enabled = i < pages.lastIndex) {
                            Icon(Icons.Outlined.ArrowForward, "Move later")
                        }
                        IconButton(onClick = { vm.move(i, -1) }) {
                            Icon(Icons.Outlined.Close, "Remove page")
                        }
                    }
                }
            }
        }
    }
    Entry(
        "PDF Settings",
        "${vm.paper.size} · ${vm.paper.orientation} · ${vm.paper.fit}",
        Icons.Outlined.Tune,
    ) {
        options = !options
    }
    androidx.compose.animation.AnimatedVisibility(options) {
        val p = vm.paper
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Page size", p.size, sizes) { vm.configure(p.copy(size = it)) }
            Choice("Orientation", p.orientation, listOf("Auto", "Portrait", "Landscape")) {
                vm.configure(p.copy(orientation = it))
            }
            Choice("Placement", p.fit, listOf("Fit", "Fill")) { vm.configure(p.copy(fit = it)) }
            Dial("Margin (pt)", p.margin.toFloat(), 0f..72f) {
                vm.configure(p.copy(margin = it.toInt()))
            }
            Dial("JPEG quality", p.quality.toFloat(), 10f..100f) {
                vm.configure(p.copy(quality = it.toInt()))
            }
            OutlinedTextField(
                p.target,
                { vm.configure(p.copy(target = it)) },
                label = { Text("Target size (optional)") },
                singleLine = true,
            )
            Choice("Target unit", p.unit, listOf("KB", "MB")) { vm.configure(p.copy(unit = it)) }
        }
    }
    DocumentAction(
        busy = vm.generating || vm.importing,
        action = { if (pages.isEmpty()) picker.launch(arrayOf("image/*")) else vm.generate() },
        hasPages = pages.isNotEmpty(),
    )
    if (vm.pdfStatus.isNotEmpty()) Text(vm.pdfStatus)
    vm.pdf?.let { product ->
        Text(
            "Generated PDF · ${bytes(product.file.length())}",
            style = MaterialTheme.typography.titleMedium,
        )
        var preview by remember(product) { mutableStateOf(false) }
        val context = LocalContext.current
        Row {
            TextButton({ preview = true }) { Text("Preview") }
            listOf(false, true).forEach { share ->
                TextButton(
                    onClick = {
                        try {
                            val uri =
                                FileProvider.getUriForFile(
                                    context,
                                    context.packageName + ".fileprovider",
                                    product.file,
                                )
                            val i =
                                if (share)
                                    Intent(Intent.ACTION_SEND)
                                        .setType(product.mime)
                                        .putExtra(Intent.EXTRA_STREAM, uri)
                                else Intent(Intent.ACTION_VIEW).setDataAndType(uri, product.mime)
                            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            vm.keepForViewer(product)
                            context.startActivity(
                                if (share) Intent.createChooser(i, "Share PDF") else i
                            )
                        } catch (e: Exception) {
                            vm.notice = "No compatible app is available."
                        }
                    }
                ) {
                    Text(if (share) "Share" else "Open")
                }
            }
        }
        Export(vm, product, "Document", connect)
        if (preview) PdfPreview(product, pages.size) { preview = false }
    }
}

@Composable
fun Export(vm: Workspace, p: Product, initial: String, connect: () -> Unit) {
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        name,
        { name = it },
        Modifier.fillMaxWidth(),
        label = { Text("File name") },
        suffix = { Text(".${p.ext}") },
        singleLine = true,
    )
    Button(
        onClick = { if (vm.connected) vm.save(p, name) else connect() },
        enabled = !vm.saving,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (vm.saving) "Saving…"
            else if (vm.connected) "Save to Toolex" else "Connect export folder"
        )
    }
}

@Composable
private fun PdfPreview(p: Product, count: Int, close: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val bm by
        produceState<Bitmap?>(null, page, p) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            ParcelFileDescriptor.open(p.file, ParcelFileDescriptor.MODE_READ_ONLY)
                                .use { fd ->
                                    PdfRenderer(fd).use { renderer ->
                                        renderer.openPage(page).use { pdfPage ->
                                            val scale = 1000.0 / max(pdfPage.width, pdfPage.height)
                                            Bitmap.createBitmap(
                                                    max(1, (pdfPage.width * scale).toInt()),
                                                    max(1, (pdfPage.height * scale).toInt()),
                                                    Bitmap.Config.ARGB_8888,
                                                )
                                                .also {
                                                    it.eraseColor(android.graphics.Color.WHITE)
                                                    pdfPage.render(
                                                        it,
                                                        null,
                                                        null,
                                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                                    )
                                                }
                                        }
                                    }
                                }
                        }
                        .getOrNull()
                }
        }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Page ${page+1} of $count") },
        text = {
            bm?.let {
                Image(
                    it.asImageBitmap(),
                    "PDF page ${page+1}",
                    Modifier.fillMaxWidth().heightIn(max = 420.dp),
                )
            }
        },
        confirmButton = { TextButton(close) { Text("Close") } },
        dismissButton = {
            Row {
                IconButton({ page-- }, enabled = page > 0) {
                    Icon(Icons.Outlined.ArrowBack, "Previous page")
                }
                IconButton({ page++ }, enabled = page < count - 1) {
                    Icon(Icons.Outlined.ArrowForward, "Next page")
                }
            }
        },
    )
}
