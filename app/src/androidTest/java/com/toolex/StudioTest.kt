package com.toolex

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val studio = Studio(ctx)

    private fun fixture(color: Int): Photo {
        val file = studio.temp("png")
        val bm = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        bm.eraseColor(color)
        file.outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bm.recycle()
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        return studio.inspect(uri).also { Assert.assertEquals(file.length(), it.bytes) }
    }

    @Test
    fun cropRotateAndLosslessEncoding() {
        val p = fixture(0x80cc2266.toInt())
        val edit = Edit(crop = Crop(0f, 0f, .5f, 1f), rotation = 90, format = "PNG")
        val a = studio.encode(p, edit)
        Assert.assertEquals(80, a.width)
        Assert.assertEquals(60, a.height)
        val bmp = BitmapFactory.decodeFile(a.file.path)
        Assert.assertEquals(128, Color.alpha(bmp.getPixel(30, 30)))
        bmp.recycle()
        val b = studio.encode(p, edit.copy(quality = 1))
        Assert.assertArrayEquals(a.file.readBytes(), b.file.readBytes())
        Assert.assertEquals(a.file.length(), a.file.readBytes().size.toLong())
    }

    @Test
    fun jpgAdjustmentsAndResize() {
        val p = fixture(Color.RED)
        val a =
            studio.encode(
                p,
                Edit(
                    width = 240,
                    height = 160,
                    filter = "Mono",
                    brightness = .15f,
                    contrast = .8f,
                    flip = true,
                    text = "Test",
                ),
            )
        val b = BitmapFactory.decodeFile(a.file.path)
        Assert.assertEquals(240, b.width)
        Assert.assertEquals(160, b.height)
        val color = b.getPixel(3, 3)
        Assert.assertTrue(kotlin.math.abs(Color.red(color) - Color.green(color)) < 4)
        Assert.assertEquals(0xff, a.file.readBytes()[0].toInt() and 255)
        b.recycle()
    }

    @Test
    fun generatedPdfOpensInOrderAndReportsUnreachableTarget() = runBlocking {
        val photos = listOf(fixture(Color.RED), fixture(Color.BLUE))
        val result = studio.document(photos, Paper(target = "0.1", margin = 0)) {}
        Assert.assertTrue(result.file.length() > 100)
        ParcelFileDescriptor.open(result.file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                Assert.assertEquals(2, r.pageCount)
                repeat(2) { i ->
                    r.openPage(i).use { page ->
                        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val c = b.getPixel(48, 48)
                        Assert.assertTrue(if (i == 0) Color.red(c) > 200 else Color.blue(c) > 200)
                        b.recycle()
                    }
                }
            }
        }
    }

    @Test
    fun fitsMeasuredTargetBelowFullQuality() = runBlocking {
        val photo =
            studio.inspect(
                android.net.Uri.parse("android.resource://com.toolex/${R.drawable.studio_photo}")
            )
        val pages = listOf(photo)
        val full = studio.document(pages, Paper(quality = 94)) {}
        val low = studio.document(pages, Paper(quality = 32)) {}
        val limit = (full.file.length() + low.file.length()) / 2
        Assert.assertTrue(limit < full.file.length())
        val optimized =
            studio.document(pages, Paper(quality = 94, target = (limit / 1000.0).toString())) {}
        Assert.assertTrue(optimized.file.length() <= limit)
        Assert.assertTrue(optimized.file.length() >= low.file.length())
    }

    @Test
    fun wrongFolderRejected() {
        val uri =
            android.provider.DocumentsContract.buildTreeDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Download/Toolex",
            )
        Assert.assertTrue(runCatching { SharedStorage(ctx).connect(uri) }.isFailure)
    }
}
