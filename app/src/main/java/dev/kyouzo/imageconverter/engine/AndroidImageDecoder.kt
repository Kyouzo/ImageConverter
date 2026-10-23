package dev.kyouzo.imageconverter.engine

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import dev.kyouzo.imageconverter.format.CodecSource
import dev.kyouzo.imageconverter.format.ImageFormat
import dev.kyouzo.imageconverter.format.ImageFormatRegistry
import dev.kyouzo.imageconverter.format.PixelImage
import dev.kyouzo.imageconverter.format.codec.IcoCodec
import dev.kyouzo.imageconverter.format.codec.ImageCodecException
import dev.kyouzo.imageconverter.format.codec.TgaCodec

/** Decodes a source image (by content URI) into an ARGB_8888 [Bitmap]. */
class AndroidImageDecoder(
    private val resolver: ContentResolver,
    private val registry: ImageFormatRegistry,
) {

    /**
     * @throws ConversionException with a user-facing message on any failure.
     * @throws OutOfMemoryError propagated so the caller can report "image too large".
     */
    fun decode(uri: Uri, sourceFormat: ImageFormat?): Bitmap {
        val cap = sourceFormat?.let { registry.decoder(it) }
        return when (cap?.source) {
            CodecSource.CUSTOM -> decodeCustom(uri, sourceFormat)
            CodecSource.NONE -> throw ConversionException(
                cap.reason ?: "${sourceFormat?.displayName ?: "This"} format cannot be decoded on this device"
            )
            // PLATFORM, or unknown format (let the platform try to sniff it).
            else -> decodePlatform(uri)
        }
    }

    private fun decodePlatform(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            // ImageDecoder applies EXIF orientation automatically and yields the
            // first frame for animated sources.
            try {
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }.let { ensureArgb(it) }
            } catch (e: OutOfMemoryError) {
                throw e
            } catch (e: Exception) {
                throw ConversionException("This image could not be decoded (it may be corrupted or an unsupported variant)", e)
            }
        } else {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw ConversionException("The image could not be read (it may be inaccessible or corrupted)")
            applyExifOrientation(uri, ensureArgb(bmp))
        }
    }

    private fun decodeCustom(uri: Uri, format: ImageFormat): Bitmap {
        val bytes = readAllBytes(uri)
        return try {
            when (format) {
                ImageFormat.TGA -> BitmapConversions.toBitmap(TgaCodec.decode(bytes))
                ImageFormat.ICO -> when (val r = IcoCodec.extractBest(bytes)) {
                    is IcoCodec.Result.Png ->
                        BitmapFactory.decodeByteArray(r.bytes, 0, r.bytes.size)
                            ?.let { ensureArgb(it) }
                            ?: throw ConversionException("The ICO contained a PNG that could not be decoded")
                    is IcoCodec.Result.Raster -> BitmapConversions.toBitmap(r.image)
                }
                else -> throw ConversionException("No custom decoder for ${format.displayName}")
            }
        } catch (e: ImageCodecException) {
            throw ConversionException("Could not decode ${format.displayName}: ${e.message}", e)
        }
    }

    private fun ensureArgb(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        val converted = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        bitmap.recycle()
        return converted
    }

    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            ) } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) return bitmap

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun readAllBytes(uri: Uri): ByteArray =
        resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ConversionException("The image could not be read (it may be inaccessible)")
}
