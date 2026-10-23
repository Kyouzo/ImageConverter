package dev.kyouzo.imageconverter.engine

import android.graphics.Bitmap
import android.os.Build
import dev.kyouzo.imageconverter.format.ImageFormat
import dev.kyouzo.imageconverter.format.ImageFormatRegistry
import dev.kyouzo.imageconverter.format.codec.BmpCodec
import dev.kyouzo.imageconverter.format.codec.TgaCodec
import java.io.OutputStream

/**
 * Encodes an ARGB_8888 [Bitmap] to the requested [ImageFormat]. Platform formats
 * go through Bitmap.compress(); BMP and TGA go through the bundled pure-Kotlin
 * codecs. Formats the registry marks as non-encodable are rejected up front.
 */
class AndroidImageEncoder(
    private val registry: ImageFormatRegistry,
) {

    /**
     * @param bitmap the (already orientation-corrected, already flattened if the
     *               target has no alpha) source bitmap.
     * @param target the output format.
     * @param quality 1..100, used only for lossy targets.
     * @throws ConversionException if the format cannot be encoded or encoding fails.
     */
    fun encode(bitmap: Bitmap, target: ImageFormat, quality: Int, out: OutputStream) {
        val cap = registry.encoder(target)
        if (!cap.available) throw ConversionException(cap.reason ?: "${target.displayName} cannot be encoded")

        when (target) {
            ImageFormat.JPEG -> compress(bitmap, Bitmap.CompressFormat.JPEG, quality, out)
            ImageFormat.PNG -> compress(bitmap, Bitmap.CompressFormat.PNG, 100, out)
            ImageFormat.WEBP -> compressWebp(bitmap, quality, out)
            ImageFormat.BMP -> out.write(BmpCodec.encode(BitmapConversions.toPixelImage(bitmap), includeAlpha = bitmap.hasAlpha()))
            ImageFormat.TGA -> out.write(TgaCodec.encode(BitmapConversions.toPixelImage(bitmap), includeAlpha = bitmap.hasAlpha()))
            else -> throw ConversionException("${target.displayName} encoding is not supported")
        }
    }

    private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int, out: OutputStream) {
        if (!bitmap.compress(format, quality.coerceIn(1, 100), out)) {
            throw ConversionException("Encoding failed for ${format.name}")
        }
    }

    @Suppress("DEPRECATION")
    private fun compressWebp(bitmap: Bitmap, quality: Int, out: OutputStream) {
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Choose lossy vs lossless based on the quality request; 100 => lossless.
            val fmt = if (quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS
            else Bitmap.CompressFormat.WEBP_LOSSY
            bitmap.compress(fmt, quality.coerceIn(1, 100), out)
        } else {
            bitmap.compress(Bitmap.CompressFormat.WEBP, quality.coerceIn(1, 100), out)
        }
        if (!ok) throw ConversionException("WebP encoding failed")
    }
}
