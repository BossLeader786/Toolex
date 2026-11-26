package com.toolex.pdf.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tiny dependency-free thumbnail loader: one shared LruCache + parallel decode.
 * Deliberately simple — the grid only needs small squares, and keeping the app
 * free of image-loading libraries keeps the APK small.
 */
object ThumbCache {
    // ~1/8th of the app's available heap, in KB, is plenty for thumbnails.
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceIn(4_096, 32_768),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(uri: String): Bitmap? = cache.get(uri)
    fun put(uri: String, bitmap: Bitmap) { cache.put(uri, bitmap) }
    fun clear() = cache.evictAll()
}

/** Decodes [uri] at (about) [targetPx] and caches it. */
private suspend fun loadThumb(context: Context, uri: String, targetPx: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        ThumbCache.get("$uri@$targetPx")?.let { return@withContext it }
        try {
            val parsed = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return@withContext null
            if (bounds.outWidth <= 0) return@withContext null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            var bitmap = context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@withContext null

            // Center-crop to a square so the grid looks even.
            val side = minOf(bitmap.width, bitmap.height)
            if (side > 0) {
                val cropped = Bitmap.createBitmap(
                    bitmap,
                    (bitmap.width - side) / 2,
                    (bitmap.height - side) / 2,
                    side,
                    side,
                )
                if (cropped != bitmap) bitmap.recycle()
                bitmap = cropped
            }
            ThumbCache.put("$uri@$targetPx", bitmap)
            bitmap
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

/** Square thumbnail with a neutral placeholder while it decodes. */
@Composable
fun ImageThumb(
    uri: String,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    corner: Dp = 14.dp,
) {
    val context = LocalContext.current
    val targetPx = (size.value * (context.resources.displayMetrics.densityDpi / 160f)).toInt()
        .coerceIn(64, 512)
    val bitmap by produceState<Bitmap?>(initialValue = ThumbCache.get("$uri@$targetPx"), uri, targetPx) {
        if (value == null) value = loadThumb(context, uri, targetPx)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}
