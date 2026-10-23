package dev.kyouzo.imageconverter.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import dev.kyouzo.imageconverter.format.PixelImage

/** Bridges the pure [PixelImage] used by the custom codecs and Android [Bitmap]. */
object BitmapConversions {

    fun toBitmap(image: PixelImage): Bitmap {
        val bmp = createBitmap(image.width, image.height)
        bmp.setPixels(image.pixels, 0, image.width, 0, 0, image.width, image.height)
        return bmp
    }

    fun toPixelImage(bitmap: Bitmap): PixelImage {
        val src = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        if (src !== bitmap) src.recycle()
        return PixelImage(w, h, pixels)
    }

    /**
     * Flatten any transparency onto an opaque [background] (0xFFRRGGBB), returning
     * a new ARGB_8888 bitmap. Used before encoding to a format without alpha (JPEG).
     */
    fun flattenOnto(bitmap: Bitmap, background: Int): Bitmap {
        val out = createBitmap(bitmap.width, bitmap.height)
        val canvas = Canvas(out)
        canvas.drawColor(background or (0xFF shl 24))
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return out
    }

    @Suppress("unused")
    private fun Bitmap.clear() = eraseColor(Color.TRANSPARENT)
}
