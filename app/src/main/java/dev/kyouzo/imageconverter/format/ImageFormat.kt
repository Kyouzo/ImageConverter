package dev.kyouzo.imageconverter.format

/**
 * The image formats the app knows about.
 *
 * This enum is pure JVM logic with no Android dependencies so it can be unit
 * tested on the host. Whether a format can actually be decoded or encoded on a
 * given device is NOT expressed here (it depends on the OS version and which
 * codecs are implemented) - see [ImageFormatRegistry].
 *
 * @param id            stable identifier used in UI state and tests
 * @param displayName   label shown in the UI
 * @param primaryExtension canonical lower-case extension WITHOUT the dot
 * @param extensions    every lower-case extension (without dot) that maps to this format
 * @param mimeType      IANA media type used for MediaStore + sharing
 * @param supportsAlpha true if the container can represent per-pixel transparency
 * @param lossy         true if the encoder exposes a meaningful quality setting (1..100)
 */
enum class ImageFormat(
    val id: String,
    val displayName: String,
    val primaryExtension: String,
    val extensions: List<String>,
    val mimeType: String,
    val supportsAlpha: Boolean,
    val lossy: Boolean,
) {
    JPEG("jpeg", "JPEG", "jpg", listOf("jpg", "jpeg", "jpe", "jfif"), "image/jpeg", supportsAlpha = false, lossy = true),
    PNG("png", "PNG", "png", listOf("png"), "image/png", supportsAlpha = true, lossy = false),
    WEBP("webp", "WebP", "webp", listOf("webp"), "image/webp", supportsAlpha = true, lossy = true),
    BMP("bmp", "BMP", "bmp", listOf("bmp", "dib"), "image/bmp", supportsAlpha = true, lossy = false),
    GIF("gif", "GIF", "gif", listOf("gif"), "image/gif", supportsAlpha = true, lossy = false),
    TIFF("tiff", "TIFF", "tiff", listOf("tif", "tiff"), "image/tiff", supportsAlpha = true, lossy = false),
    HEIC("heic", "HEIC", "heic", listOf("heic", "heics"), "image/heic", supportsAlpha = false, lossy = true),
    HEIF("heif", "HEIF", "heif", listOf("heif", "heifs"), "image/heif", supportsAlpha = false, lossy = true),
    AVIF("avif", "AVIF", "avif", listOf("avif"), "image/avif", supportsAlpha = true, lossy = true),
    ICO("ico", "ICO", "ico", listOf("ico"), "image/x-icon", supportsAlpha = true, lossy = false),
    TGA("tga", "TGA", "tga", listOf("tga", "icb", "vda", "vst"), "image/x-tga", supportsAlpha = true, lossy = false),
    WBMP("wbmp", "WBMP", "wbmp", listOf("wbmp"), "image/vnd.wap.wbmp", supportsAlpha = false, lossy = false),
    JXL("jxl", "JPEG XL", "jxl", listOf("jxl"), "image/jxl", supportsAlpha = true, lossy = true);

    companion object {
        /** Match a file extension (with or without leading dot, any case) to a format. */
        fun fromExtension(ext: String?): ImageFormat? {
            if (ext.isNullOrBlank()) return null
            val cleaned = ext.trim().removePrefix(".").lowercase()
            return entries.firstOrNull { cleaned in it.extensions }
        }

        /** Match on the file name's extension. */
        fun fromFileName(name: String?): ImageFormat? {
            if (name.isNullOrBlank()) return null
            val dot = name.lastIndexOf('.')
            if (dot < 0 || dot == name.length - 1) return null
            return fromExtension(name.substring(dot + 1))
        }

        fun fromId(id: String?): ImageFormat? = entries.firstOrNull { it.id == id }
    }
}
