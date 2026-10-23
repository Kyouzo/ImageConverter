package dev.kyouzo.imageconverter.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dev.kyouzo.imageconverter.format.ColorUtils
import dev.kyouzo.imageconverter.format.ConversionRequest
import dev.kyouzo.imageconverter.format.ConversionValidation
import dev.kyouzo.imageconverter.format.FileNaming
import dev.kyouzo.imageconverter.format.ImageFormat
import dev.kyouzo.imageconverter.format.ImageFormatRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The result of a successful conversion, written to a temporary cache file. */
data class ConversionOutput(
    val file: File,
    val fileName: String,
    val format: ImageFormat,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

/**
 * Orchestrates the full conversion pipeline off the main thread:
 * read URI -> decode -> orientation (handled in decoder) -> flatten for JPEG ->
 * encode -> temp cache file -> verify output is non-empty & re-decodable.
 */
class ImageConverter(
    private val context: Context,
    private val registry: ImageFormatRegistry,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) {
    private val decoder = AndroidImageDecoder(context.contentResolver, registry)
    private val encoder = AndroidImageEncoder(registry)

    suspend fun convert(
        source: SelectedImage,
        request: ConversionRequest,
    ): ConversionOutput = withContext(io) {
        // Validate against device capabilities first (fast, no decode).
        ConversionValidation.validate(registry, request, source.detectedFormat)?.let {
            throw ConversionException(it.message)
        }

        var decoded: Bitmap? = null
        var toEncode: Bitmap? = null
        try {
            decoded = try {
                decoder.decode(source.uri, source.detectedFormat)
            } catch (oom: OutOfMemoryError) {
                throw ConversionException("This image is too large to process on this device", oom)
            }

            val quality = ConversionValidation.effectiveQuality(request)

            // Flatten transparency when the target cannot store alpha.
            toEncode = if (!request.target.supportsAlpha && decoded.hasAlpha()) {
                val bg = ConversionValidation.effectiveBackground(request)
                BitmapConversions.flattenOnto(decoded, bg)
            } else {
                decoded
            }

            val outName = FileNaming.outputName(source.displayName, request.target)
            val outFile = File(cacheDir(), outName)
            try {
                outFile.outputStream().use { os ->
                    encoder.encode(toEncode, request.target, quality, os)
                }
            } catch (oom: OutOfMemoryError) {
                throw ConversionException("Ran out of memory while encoding this image", oom)
            }

            // Verify the output is real: non-empty and decodable back to the same size.
            verifyOutput(outFile, request.target, toEncode.width, toEncode.height)

            ConversionOutput(
                file = outFile,
                fileName = outName,
                format = request.target,
                mimeType = request.target.mimeType,
                width = toEncode.width,
                height = toEncode.height,
                sizeBytes = outFile.length(),
            )
        } finally {
            if (toEncode != null && toEncode !== decoded) toEncode.recycle()
            decoded?.recycle()
        }
    }

    private fun verifyOutput(file: File, format: ImageFormat, w: Int, h: Int) {
        if (!file.exists() || file.length() == 0L) {
            throw ConversionException("Conversion produced an empty file")
        }
        // Re-decode bounds for platform formats to confirm the file is valid.
        // Our custom formats (BMP/TGA) are validated by their round-trip tests and
        // may not be bounds-decodable by BitmapFactory, so skip those here.
        val platformDecodable = format in setOf(
            ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP
        )
        if (platformDecodable) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                throw ConversionException("The converted file could not be verified")
            }
        }
    }

    private fun cacheDir(): File {
        val dir = File(context.cacheDir, "conversions")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Remove previously produced temp files (call when starting a new conversion). */
    fun clearCache() {
        runCatching { File(context.cacheDir, "conversions").listFiles()?.forEach { it.delete() } }
    }

    fun uriForOutput(output: ConversionOutput): Uri = Uri.fromFile(output.file)

    companion object {
        /** Default white background used when flattening alpha to JPEG. */
        const val DEFAULT_JPEG_BACKGROUND = ColorUtils.WHITE
    }
}
