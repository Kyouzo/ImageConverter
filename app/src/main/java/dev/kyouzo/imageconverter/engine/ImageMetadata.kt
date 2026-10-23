package dev.kyouzo.imageconverter.engine

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import dev.kyouzo.imageconverter.format.FormatSignatures
import dev.kyouzo.imageconverter.format.ImageFormat

/** Metadata probed from a selected image without fully decoding it. */
data class SelectedImage(
    val uri: Uri,
    val displayName: String?,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    /** Format detected from the file signature, falling back to the extension. */
    val detectedFormat: ImageFormat?,
)

/** Reads lightweight metadata from a content URI (name, size, dimensions, true format). */
class ImageMetadataReader(private val resolver: ContentResolver) {

    fun read(uri: Uri): SelectedImage {
        var name: String? = null
        var size = 0L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                        if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                    }
                }
        } catch (_: Exception) { /* name/size are best-effort */ }

        // Detect true format from the leading bytes; fall back to the file name.
        val header = ByteArray(FormatSignatures.RECOMMENDED_HEADER_BYTES)
        var read = 0
        try {
            resolver.openInputStream(uri)?.use { input ->
                var n = input.read(header, read, header.size - read)
                while (n > 0 && read < header.size) { read += n; n = input.read(header, read, header.size - read) }
            }
        } catch (_: Exception) { /* fall through to extension */ }
        val detected = (if (read > 0) FormatSignatures.detect(header.copyOf(read)) else null)
            ?: ImageFormat.fromFileName(name)

        // Dimensions via bounds-only decode (cheap, no full bitmap allocation).
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) { /* dimensions may be unknown for custom formats */ }

        return SelectedImage(
            uri = uri,
            displayName = name,
            sizeBytes = size,
            width = opts.outWidth.coerceAtLeast(0),
            height = opts.outHeight.coerceAtLeast(0),
            detectedFormat = detected,
        )
    }
}
