package dev.kyouzo.imageconverter.format

/** Where a decode/encode capability comes from. */
enum class CodecSource {
    /** Handled by an Android platform codec (BitmapFactory / ImageDecoder / Bitmap.compress). */
    PLATFORM,

    /** Handled by a pure-Kotlin codec bundled in this app. */
    CUSTOM,

    /** Not available on this device / in this build. */
    NONE,
}

/** A format's capability together with a human-readable reason when unavailable. */
data class Capability(
    val source: CodecSource,
    val reason: String? = null,
) {
    val available: Boolean get() = source != CodecSource.NONE
}

/**
 * The single source of truth for "can this device decode/encode format X".
 *
 * Capabilities depend on the running OS version (HEIC/AVIF platform support is
 * gated by API level) so the registry is constructed with [sdkInt]. All of this
 * is pure logic and unit tested on the host by passing synthetic SDK levels.
 */
class ImageFormatRegistry(
    private val sdkInt: Int,
) {
    companion object {
        // Platform decode gates (android.os.Build.VERSION_CODES values, inlined to
        // keep this file Android-free and host-testable).
        const val API_P = 28 // Android 9  - HEIF/HEIC decode via ImageDecoder
        const val API_S = 31 // Android 12 - AVIF decode
    }

    private fun decoderOf(format: ImageFormat): Capability = when (format) {
        ImageFormat.JPEG,
        ImageFormat.PNG,
        ImageFormat.WEBP,
        ImageFormat.GIF,   // decoded to a single (first) frame
        ImageFormat.BMP,
        ImageFormat.WBMP -> Capability(CodecSource.PLATFORM)

        ImageFormat.HEIC,
        ImageFormat.HEIF ->
            if (sdkInt >= API_P) Capability(CodecSource.PLATFORM)
            else Capability(CodecSource.NONE, "HEIC/HEIF decoding needs Android 9 (API 28) or newer")

        ImageFormat.AVIF ->
            if (sdkInt >= API_S) Capability(CodecSource.PLATFORM)
            else Capability(CodecSource.NONE, "AVIF decoding needs Android 12 (API 31) or newer")

        ImageFormat.TGA,
        ImageFormat.ICO -> Capability(CodecSource.CUSTOM)

        ImageFormat.TIFF -> Capability(CodecSource.NONE, "No Android-compatible TIFF decoder is bundled in this MVP")
        ImageFormat.JXL -> Capability(CodecSource.NONE, "JPEG XL is not supported by Android platform codecs")
    }

    private fun encoderOf(format: ImageFormat): Capability = when (format) {
        ImageFormat.JPEG,
        ImageFormat.PNG,
        ImageFormat.WEBP -> Capability(CodecSource.PLATFORM)

        ImageFormat.BMP,
        ImageFormat.TGA -> Capability(CodecSource.CUSTOM)

        ImageFormat.GIF -> Capability(CodecSource.NONE, "GIF encoding (palette quantization + LZW) is not implemented in this MVP")
        ImageFormat.TIFF -> Capability(CodecSource.NONE, "TIFF encoding is not available on Android without a native codec")
        ImageFormat.HEIC,
        ImageFormat.HEIF -> Capability(CodecSource.NONE, "Android does not expose HEIC/HEIF encoding through Bitmap.compress")
        ImageFormat.AVIF -> Capability(CodecSource.NONE, "Android does not expose AVIF encoding through Bitmap.compress")
        ImageFormat.ICO -> Capability(CodecSource.NONE, "ICO encoding is not implemented in this MVP")
        ImageFormat.WBMP -> Capability(CodecSource.NONE, "WBMP is a 1-bit format and encoding is not implemented in this MVP")
        ImageFormat.JXL -> Capability(CodecSource.NONE, "JPEG XL encoding is not supported on Android")
    }

    fun decoder(format: ImageFormat): Capability = decoderOf(format)
    fun encoder(format: ImageFormat): Capability = encoderOf(format)

    fun canDecode(format: ImageFormat): Boolean = decoderOf(format).available
    fun canEncode(format: ImageFormat): Boolean = encoderOf(format).available

    /** Formats offered in the output selector, in display order, with availability. */
    fun outputOptions(): List<Pair<ImageFormat, Capability>> =
        ImageFormat.entries.map { it to encoderOf(it) }

    fun decodableFormats(): List<ImageFormat> = ImageFormat.entries.filter { canDecode(it) }
    fun encodableFormats(): List<ImageFormat> = ImageFormat.entries.filter { canEncode(it) }

    /** True when a quality slider should be shown for [format] (lossy AND encodable). */
    fun qualityApplicable(format: ImageFormat): Boolean = format.lossy && canEncode(format)
}
