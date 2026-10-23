package dev.kyouzo.imageconverter.format.codec

import dev.kyouzo.imageconverter.format.PixelImage

/**
 * Pure-Kotlin Truevision TGA codec. Android has no native TGA support, so both
 * directions live here. Supports:
 *   - encode: uncompressed true-color, 24-bit (BGR) or 32-bit (BGRA), top-left origin
 *   - decode: uncompressed (type 2) and RLE (type 10) true-color, 24/32-bit,
 *             honoring the origin bit
 *
 * Unit tested on the host.
 */
object TgaCodec {

    fun encode(image: PixelImage, includeAlpha: Boolean): ByteArray {
        val w = image.width
        val h = image.height
        val bpp = if (includeAlpha) 32 else 24
        val bytesPerPixel = bpp / 8
        val header = 18
        val out = ByteArray(header + w * h * bytesPerPixel)
        // Header
        out[0] = 0                     // id length
        out[1] = 0                     // no color map
        out[2] = 2                     // uncompressed true-color
        // color map spec (5 bytes) left zero
        // x/y origin left zero
        le16(out, 12, w)
        le16(out, 14, h)
        out[16] = bpp.toByte()
        // Image descriptor: bits 0-3 = alpha channel depth, bit 5 = top-left origin
        out[17] = ((if (includeAlpha) 0x08 else 0x00) or 0x20).toByte()
        var p = header
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = image.get(x, y)
                out[p++] = PixelImage.b(c).toByte()
                out[p++] = PixelImage.g(c).toByte()
                out[p++] = PixelImage.r(c).toByte()
                if (includeAlpha) out[p++] = PixelImage.a(c).toByte()
            }
        }
        return out
    }

    fun decode(bytes: ByteArray): PixelImage {
        if (bytes.size < 18) throw ImageCodecException("TGA too small")
        val idLength = bytes[0].toInt() and 0xFF
        val colorMapType = bytes[1].toInt() and 0xFF
        val imageType = bytes[2].toInt() and 0xFF
        val width = rle16(bytes, 12)
        val height = rle16(bytes, 14)
        val bpp = bytes[16].toInt() and 0xFF
        val descriptor = bytes[17].toInt() and 0xFF
        val topLeft = (descriptor and 0x20) != 0

        if (colorMapType != 0) throw ImageCodecException("Color-mapped TGA is not supported")
        if (imageType != 2 && imageType != 10) throw ImageCodecException("Only true-color TGA (type 2/10) is supported")
        if (bpp != 24 && bpp != 32) throw ImageCodecException("Only 24/32-bit TGA is supported (got $bpp)")
        if (width <= 0 || height <= 0) throw ImageCodecException("Invalid TGA dimensions ${width}x$height")

        val bytesPerPixel = bpp / 8
        // Skip image id and (absent) color map.
        var p = 18 + idLength
        val linear = IntArray(width * height)
        val total = width * height

        if (imageType == 2) {
            if (p + total * bytesPerPixel > bytes.size) throw ImageCodecException("TGA pixel data truncated")
            for (i in 0 until total) {
                linear[i] = readPixel(bytes, p, bpp)
                p += bytesPerPixel
            }
        } else {
            // RLE
            var i = 0
            while (i < total) {
                if (p >= bytes.size) throw ImageCodecException("TGA RLE data truncated")
                val packet = bytes[p++].toInt() and 0xFF
                val count = (packet and 0x7F) + 1
                if (packet and 0x80 != 0) {
                    val px = readPixel(bytes, p, bpp); p += bytesPerPixel
                    repeat(count) { if (i < total) linear[i++] = px }
                } else {
                    repeat(count) {
                        if (i < total) { linear[i++] = readPixel(bytes, p, bpp); p += bytesPerPixel }
                    }
                }
            }
        }

        // linear is in storage order. If origin is bottom-left, flip vertically.
        val pixels = IntArray(total)
        for (y in 0 until height) {
            val dstY = if (topLeft) y else height - 1 - y
            System.arraycopy(linear, y * width, pixels, dstY * width, width)
        }
        return PixelImage(width, height, pixels)
    }

    private fun readPixel(bytes: ByteArray, p: Int, bpp: Int): Int {
        val b = bytes[p].toInt() and 0xFF
        val g = bytes[p + 1].toInt() and 0xFF
        val r = bytes[p + 2].toInt() and 0xFF
        val a = if (bpp == 32) bytes[p + 3].toInt() and 0xFF else 0xFF
        return PixelImage.argb(a, r, g, b)
    }

    private fun le16(a: ByteArray, o: Int, v: Int) {
        a[o] = (v and 0xFF).toByte()
        a[o + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun rle16(a: ByteArray, o: Int): Int =
        (a[o].toInt() and 0xFF) or ((a[o + 1].toInt() and 0xFF) shl 8)
}
