package com.toolex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(vm: Workspace, connect: () -> Unit) {
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.import(listOf(uri), true)
        }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (vm.photo == null)
            vm.import(
                listOf(
                    android.net.Uri.parse(
                        "android.resource://${context.packageName}/${R.drawable.studio_photo}"
                    )
                ),
                true,
            )
    }
    TextButton(onClick = { if (!vm.importing) picker.launch(arrayOf("image/*")) }) {
        Icon(Icons.Outlined.AddPhotoAlternate, null)
        Spacer(Modifier.width(8.dp))
        Text("Select a photo")
    }
    val p = vm.photo ?: return
    val e = vm.edit
    var tool by rememberSaveable { mutableStateOf("Adjust") }
    if (tool == "Crop") CropView(vm, p, e)
    else
        key(p) {
            val source by
                produceState<Bitmap?>(null, p) {
                    value =
                        withContext(Dispatchers.IO) {
                            runCatching { vm.studio.decode(p, 800) }.getOrNull()
                        }
                }
            val live by
                produceState<Bitmap?>(null, source, e) {
                    val original = source ?: return@produceState
                    value =
                        withContext(Dispatchers.Default) {
                            runCatching {
                                    val (w, h) = vm.studio.dimensions(p, e)
                                    val scale = min(1.0, 720.0 / max(w, h))
                                    vm.studio.render(
                                        original,
                                        e,
                                        max(1, (w * scale).roundToInt()),
                                        max(1, (h * scale).roundToInt()),
                                    )
                                }
                                .getOrNull()
                        }
                }
            val preview by
                produceState<Bitmap?>(null, vm.image) {
                    vm.image?.let { output ->
                        val bm =
                            withContext(Dispatchers.IO) {
                                var sample = 1
                                while (max(output.width, output.height) / sample > 1200) sample *= 2
                                BitmapFactory.decodeFile(
                                    output.file.path,
                                    BitmapFactory.Options().apply { inSampleSize = sample },
                                )
                            }
                        if (bm != null) value = bm
                    }
                }
            val modifier = Modifier.fillMaxWidth().height(240.dp)
            (if (vm.image != null) preview ?: live else live)?.let {
                Image(it.asImageBitmap(), "Edited image", modifier)
            } ?: Thumb(vm, p, 640, modifier)
        }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf("Crop", "Adjust", "Filters", "Text", "Resize").forEach {
            FilterChip(tool == it, { tool = it }, { Text(it) })
        }
    }
    when (tool) {
        "Adjust" -> {
            Dial("Brightness", e.brightness, -1f..1f) { vm.change(e.copy(brightness = it)) }
            Dial("Contrast", e.contrast, 0.2f..2f) { vm.change(e.copy(contrast = it)) }
            Dial("Saturation", e.saturation, 0f..2f) { vm.change(e.copy(saturation = it)) }
        }
        "Crop" ->
            Row {
                OutlinedButton({
                    vm.change(e.copy(rotation = (e.rotation + 90) % 360, width = 0, height = 0))
                }) {
                    Text("Rotate 90°")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton({ vm.change(e.copy(flip = !e.flip)) }) { Text("Flip horizontal") }
            }
        "Filters" ->
            Choice("Filter", e.filter, listOf("Natural", "Mono", "Warm")) {
                vm.change(e.copy(filter = it))
            }
        "Text" ->
            OutlinedTextField(
                e.text,
                { vm.change(e.copy(text = it.take(160))) },
                label = { Text("Caption") },
                singleLine = true,
            )
        "Resize" -> Resize(vm, p, e)
    }
    Row {
        TextButton({ vm.history(true) }, enabled = vm.canUndo) { Text("Undo") }
        TextButton({ vm.history(false) }, enabled = vm.canRedo) { Text("Redo") }
        TextButton(vm::reset) { Text("Reset") }
    }
    HorizontalDivider()
    Choice("Export format", e.format, listOf("JPG", "PNG")) { vm.change(e.copy(format = it)) }
    if (e.format == "JPG")
        Dial("JPG quality", e.quality.toFloat(), 1f..100f) {
            vm.change(e.copy(quality = it.roundToInt()))
        }
    else Text("Lossless PNG · transparency preserved", style = MaterialTheme.typography.bodySmall)
    Text("Original   ${p.width} × ${p.height} · ${bytes(p.bytes)}")
    if (vm.imageStatus.isNotEmpty()) Text(vm.imageStatus)
    vm.image?.let {
        Text("Output   ${it.width} × ${it.height} · ${e.format} · ${bytes(it.file.length())}")
        Export(vm, it, p.name.substringBeforeLast('.', p.name) + "_edited", connect)
    }
}

@Composable
private fun Resize(vm: Workspace, p: Photo, e: Edit) {
    val natural = vm.studio.dimensions(p, e.copy(width = 0, height = 0))
    var width by
        remember(e.width, natural) {
            mutableStateOf((e.width.takeIf { it > 0 } ?: natural.first).toString())
        }
    var height by
        remember(e.height, natural) {
            mutableStateOf((e.height.takeIf { it > 0 } ?: natural.second).toString())
        }
    var message by remember { mutableStateOf("") }
    val ratio = natural.first.toDouble() / natural.second
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            width,
            { v ->
                width = v
                if (e.locked)
                    v.toIntOrNull()?.let { height = max(1, (it / ratio).roundToInt()).toString() }
            },
            Modifier.weight(1f),
            label = { Text("Width (px)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            height,
            { v ->
                height = v
                if (e.locked)
                    v.toIntOrNull()?.let { width = max(1, (it * ratio).roundToInt()).toString() }
            },
            Modifier.weight(1f),
            label = { Text("Height (px)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(e.locked, { vm.change(e.copy(locked = it)) })
        Text("Maintain aspect ratio")
    }
    LaunchedEffect(width, height) {
        val w = width.toIntOrNull()
        val h = height.toIntOrNull()
        if (w == null || h == null || w < 1 || h < 1) {
            message = "Enter positive pixel dimensions."
            return@LaunchedEffect
        }
        if (
            w == (if (e.width == 0) natural.first else e.width) &&
                h == (if (e.height == 0) natural.second else e.height)
        ) {
            message = ""
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(350)
        val next = e.copy(width = w, height = h)
        try {
            vm.studio.dimensions(p, next)
            message = ""
            vm.change(next)
        } catch (ex: Exception) {
            message = ex.message.orEmpty()
        }
    }
    if (message.isNotEmpty()) Text(message)
}

@Composable
private fun CropView(vm: Workspace, p: Photo, e: Edit) {
    var ratioName by remember(p) { mutableStateOf("Free") }
    val ratio =
        when (ratioName) {
            "1:1" -> 1f
            "4:3" -> 4f / 3
            "16:9" -> 16f / 9
            else -> 0f
        }
    val aspect = p.width.toFloat() / p.height
    var draft by remember(e.crop, p) { mutableStateOf(e.crop) }
    Choice("Crop ratio", ratioName, listOf("Free", "1:1", "4:3", "16:9")) {
        ratioName = it
        if (it != "Free") {
            val a = it.substringBefore(':').toFloat() / it.substringAfter(':').toFloat() / aspect
            val w = min(1f, a)
            val h = min(1f, 1 / a)
            vm.change(
                e.copy(
                    crop = Crop((1 - w) / 2, (1 - h) / 2, (1 + w) / 2, (1 + h) / 2),
                    width = 0,
                    height = 0,
                )
            )
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(250.dp),
        contentAlignment = Alignment.Center,
    ) {
        val w = minOf(maxWidth, maxHeight * aspect)
        Box(Modifier.size(w, (w / aspect).coerceAtMost(maxHeight))) {
            Thumb(vm, p, 900, Modifier.fillMaxSize())
            Canvas(
                Modifier.fillMaxSize().pointerInput(p, e, ratio) {
                    var left = false
                    var top = false
                    var moving = false
                    detectDragGestures(
                        onDragStart = { pos ->
                            val x = pos.x / size.width
                            val y = pos.y / size.height
                            left = x < (draft.left + draft.right) / 2
                            top = y < (draft.top + draft.bottom) / 2
                            moving =
                                x > draft.left + .07f &&
                                    x < draft.right - .07f &&
                                    y > draft.top + .07f &&
                                    y < draft.bottom - .07f
                        },
                        onDragEnd = { vm.change(e.copy(crop = draft, width = 0, height = 0)) },
                        onDragCancel = { draft = e.crop },
                    ) { change, drag ->
                        change.consume()
                        val c = draft
                        if (moving) {
                            val dx = (drag.x / size.width).coerceIn(-c.left, 1 - c.right)
                            val dy = (drag.y / size.height).coerceIn(-c.top, 1 - c.bottom)
                            draft = Crop(c.left + dx, c.top + dy, c.right + dx, c.bottom + dy)
                        } else {
                            val ax = if (left) c.right else c.left
                            val ay = if (top) c.bottom else c.top
                            val maxW = if (left) ax else 1 - ax
                            val maxH = if (top) ay else 1 - ay
                            var cw =
                                ((c.right - c.left) + (if (left) -1 else 1) * drag.x / size.width)
                                    .coerceIn(min(.03f, maxW), maxW)
                            var ch =
                                ((c.bottom - c.top) + (if (top) -1 else 1) * drag.y / size.height)
                                    .coerceIn(min(.03f, maxH), maxH)
                            if (ratio > 0) {
                                val r = ratio / aspect
                                cw =
                                    min(
                                        maxW,
                                        min(maxH * r, if (abs(drag.x) > abs(drag.y)) cw else ch * r),
                                    )
                                ch = cw / r
                            }
                            draft =
                                Crop(
                                    if (left) ax - cw else ax,
                                    if (top) ay - ch else ay,
                                    if (left) ax else ax + cw,
                                    if (top) ay else ay + ch,
                                )
                        }
                    }
                }
            ) {
                val l = draft.left * size.width
                val t = draft.top * size.height
                val r = draft.right * size.width
                val b = draft.bottom * size.height
                drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
                for (x in listOf(l, r)) for (y in listOf(t, b)) drawCircle(
                    Gold,
                    6.dp.toPx(),
                    Offset(x, y),
                )
            }
        }
    }
    Text("Drag a corner to crop. Drag inside to move.", style = MaterialTheme.typography.bodySmall)
}
