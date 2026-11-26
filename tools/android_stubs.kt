/*
 * Type-check-only stubs for the Android / Compose APIs used by Toolex.
 *
 * They exist so the pure engine code (PageGeometry, PdfBuilder, PdfConverter,
 * SizeEstimator, Rungs, models) can be compiled and executed on a plain JVM
 * without the Android SDK or an emulator. THIS FILE IS NOT PART OF THE APP.
 *
 * See tools/verify_engine_kotlin.sh
 */

package android.graphics

class Bitmap {
    var width: Int = 1
    var height: Int = 1

    companion object {
        fun createBitmap(w: Int, h: Int, config: Config): Bitmap = Bitmap()
        fun createBitmap(src: Bitmap, x: Int, y: Int, w: Int, h: Int, m: Matrix?, f: Boolean): Bitmap = Bitmap()
        fun createBitmap(src: Bitmap, x: Int, y: Int, w: Int, h: Int, m: Matrix?, f: Boolean, c: Config?): Bitmap = Bitmap()
        fun createScaledBitmap(src: Bitmap, w: Int, h: Int, f: Boolean): Bitmap = Bitmap()
    }

    enum class Config { ARGB_8888, RGB_565 }
    enum class CompressFormat { JPEG, PNG, WEBP }

    fun recycle() {}
    fun compress(format: CompressFormat, quality: Int, out: java.io.OutputStream): Boolean = true
}

class Matrix { fun postRotate(deg: Float): Matrix = this }
class Paint(flags: Int = 0) { companion object { const val FILTER_BITMAP_FLAG = 2; const val ANTI_ALIAS_FLAG = 1 } }
class Rect(var left: Int = 0, var top: Int = 0, var right: Int = 0, var bottom: Int = 0)
class RectF(var left: Float = 0f, var top: Float = 0f, var right: Float = 0f, var bottom: Float = 0f)
class Canvas(private val bitmap: Bitmap) {
    fun drawColor(color: Int) {}
    fun drawBitmap(bmp: Bitmap, src: Rect, dst: RectF, paint: Paint?) {}
    fun drawBitmap(bmp: Bitmap, x: Float, y: Float, paint: Paint?) {}
}

val Color: ColorUtils get() = ColorUtils
object ColorUtils { val WHITE: Int get() = 0xFFFFFFFF.toInt() }

class BitmapFactory {
    class Options {
        var inJustDecodeBounds = false
        var inSampleSize = 1
        var inPreferredConfig: Bitmap.Config? = null
        var outWidth = 0
        var outHeight = 0
    }

    companion object {
        fun decodeStream(input: java.io.InputStream, padding: Rect?, opts: Options): Bitmap? = null
    }
}
