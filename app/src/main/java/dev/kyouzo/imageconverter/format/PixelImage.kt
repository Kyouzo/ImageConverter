package dev.kyouzo.imageconverter.format

/**
 * A device-independent raster: ARGB_8888 pixels packed as 0xAARRGGBB, row-major
 * (index = y * width + x). Used by the pure-Kotlin codecs and their tests so
 * that encoding/decoding can be verified without an Android [android.graphics.Bitmap].
 */
class PixelImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "width/height must be positive ($width x $height)" }
        require(pixels.size == width * height) {
            "pixels length ${pixels.size} != ${width * height}"
        }
    }

    fun get(x: Int, y: Int): Int = pixels[y * width + x]

    /** True if any pixel has a non-opaque alpha channel. */
    fun hasAlpha(): Boolean = pixels.any { (it ushr 24) != 0xFF }

    companion object {
        fun a(c: Int) = (c ushr 24) and 0xFF
        fun r(c: Int) = (c ushr 16) and 0xFF
        fun g(c: Int) = (c ushr 8) and 0xFF
        fun b(c: Int) = c and 0xFF
        fun argb(a: Int, r: Int, g: Int, b: Int): Int =
            ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }
}

/** Alpha-compositing helpers used when flattening transparency onto a background. */
object ColorUtils {
    /**
     * Composite a source pixel over an opaque [background] (0xFFRRGGBB), returning
     * an opaque result. Used when converting transparent images to JPEG.
     */
    fun flattenOver(pixel: Int, background: Int): Int {
        val alpha = PixelImage.a(pixel)
        if (alpha == 0xFF) return pixel or (0xFF shl 24)
        if (alpha == 0) return background or (0xFF shl 24)
        val inv = 255 - alpha
        val r = (PixelImage.r(pixel) * alpha + PixelImage.r(background) * inv) / 255
        val g = (PixelImage.g(pixel) * alpha + PixelImage.g(background) * inv) / 255
        val b = (PixelImage.b(pixel) * alpha + PixelImage.b(background) * inv) / 255
        return PixelImage.argb(0xFF, r, g, b)
    }

    /** Flatten an entire image onto [background]; returns a new opaque image. */
    fun flatten(image: PixelImage, background: Int): PixelImage {
        val out = IntArray(image.pixels.size)
        for (i in image.pixels.indices) out[i] = flattenOver(image.pixels[i], background)
        return PixelImage(image.width, image.height, out)
    }

    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
}
