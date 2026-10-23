package dev.kyouzo.imageconverter.format

/**
 * Detects an image's true format from its leading bytes (magic numbers) rather
 * than trusting the file extension. Pure JVM logic, unit tested on the host.
 *
 * Some formats (WBMP, and TGA without its optional footer) have no reliable
 * signature; for those [detect] returns null and callers should fall back to
 * the extension.
 */
object FormatSignatures {

    /** Longest prefix we ever need to inspect. */
    const val RECOMMENDED_HEADER_BYTES = 32

    private fun ByteArray.u(i: Int): Int = this[i].toInt() and 0xFF

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean {
        if (size < bytes.size) return false
        for (i in bytes.indices) if (u(i) != (bytes[i] and 0xFF)) return false
        return true
    }

    private fun ByteArray.asciiAt(offset: Int, text: String): Boolean {
        if (size < offset + text.length) return false
        for (i in text.indices) if (u(offset + i) != text[i].code) return false
        return true
    }

    /**
     * @param data a prefix of the file (at least [RECOMMENDED_HEADER_BYTES] bytes
     *             is ideal; the full file enables TGA-footer detection).
     * @return the detected [ImageFormat], or null if it could not be determined.
     */
    fun detect(data: ByteArray): ImageFormat? {
        if (data.size < 2) return null

        // JPEG: FF D8 FF
        if (data.startsWith(0xFF, 0xD8, 0xFF)) return ImageFormat.JPEG

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (data.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return ImageFormat.PNG

        // GIF: "GIF87a" or "GIF89a"
        if (data.asciiAt(0, "GIF87a") || data.asciiAt(0, "GIF89a")) return ImageFormat.GIF

        // BMP: "BM"
        if (data.asciiAt(0, "BM")) return ImageFormat.BMP

        // RIFF....WEBP
        if (data.asciiAt(0, "RIFF") && data.asciiAt(8, "WEBP")) return ImageFormat.WEBP

        // TIFF: little-endian "II*\0" or big-endian "MM\0*"
        if (data.startsWith(0x49, 0x49, 0x2A, 0x00) || data.startsWith(0x4D, 0x4D, 0x00, 0x2A)) return ImageFormat.TIFF

        // ICO: 00 00 01 00 (CUR is 00 00 02 00 - not an image format we output)
        if (data.startsWith(0x00, 0x00, 0x01, 0x00)) return ImageFormat.ICO

        // JPEG XL: raw codestream FF 0A, or ISOBMFF container 00 00 00 0C 4A 58 4C 20 0D 0A 87 0A
        if (data.startsWith(0xFF, 0x0A)) return ImageFormat.JXL
        if (data.startsWith(0x00, 0x00, 0x00, 0x0C, 0x4A, 0x58, 0x4C, 0x20)) return ImageFormat.JXL

        // ISO-BMFF family ("....ftyp<brand>") covers HEIC/HEIF/AVIF
        if (data.asciiAt(4, "ftyp")) {
            val brand = if (data.size >= 12) buildString {
                for (i in 8 until 12) append(data.u(i).toChar())
            }.lowercase() else ""
            return when (brand) {
                "avif", "avis" -> ImageFormat.AVIF
                "heic", "heix", "hevc", "hevx" -> ImageFormat.HEIC
                "mif1", "msf1", "heim", "heis", "hevm", "hevs" -> ImageFormat.HEIF
                else -> ImageFormat.HEIF // unknown ISO-BMFF image brand: treat as HEIF
            }
        }

        // TGA: no header magic. The optional footer "TRUEVISION-XFILE.\0" sits in
        // the last 18 bytes when present and lets us detect it from a full file.
        if (data.size >= 18) {
            val footerStart = data.size - 18
            if (data.asciiAt(footerStart, "TRUEVISION-XFILE")) return ImageFormat.TGA
        }

        return null
    }
}
