package dev.kyouzo.imageconverter.format.codec

import dev.kyouzo.imageconverter.format.PixelImage

/**
 * Pure-Kotlin BMP (Windows bitmap) codec. Android's Bitmap.compress() cannot
 * write BMP, so encoding goes through here. Supports:
 *   - encode: 24-bit (BGR, bottom-up) or 32-bit (BGRA, top-down) BI_RGB
 *   - decode: 24-bit and 32-bit BI_RGB, top-down or bottom-up
 *
 * These are the variants produced by common tools and are enough for the MVP's
 * round-trips. No Android dependency, so it is unit tested on the host.
 */
object BmpCodec {

    private const val FILE_HEADER = 14
    private const val INFO_HEADER = 40

    fun encode(image: PixelImage, includeAlpha: Boolean): ByteArray =
        if (includeAlpha) encode32(image) else encode24(image)

    private fun encode24(image: PixelImage): ByteArray {
        val w = image.width
        val h = image.height
        val rowBytes = w * 3
        val padding = (4 - (rowBytes % 4)) % 4
        val paddedRow = rowBytes + padding
        val pixelData = paddedRow * h
        val size = FILE_HEADER + INFO_HEADER + pixelData
        val out = ByteArray(size)
        var p = writeHeaders(out, w, h, 24, pixelData, topDown = false)
        // Bottom-up: last image row written first.
        for (y in h - 1 downTo 0) {
            for (x in 0 until w) {
                val c = image.get(x, y)
                out[p++] = PixelImage.b(c).toByte()
                out[p++] = PixelImage.g(c).toByte()
                out[p++] = PixelImage.r(c).toByte()
            }
            repeat(padding) { out[p++] = 0 }
        }
        return out
    }

    private fun encode32(image: PixelImage): ByteArray {
        val w = image.width
        val h = image.height
        val pixelData = w * h * 4
        val size = FILE_HEADER + INFO_HEADER + pixelData
        val out = ByteArray(size)
        // Negative height => top-down rows.
        var p = writeHeaders(out, w, h, 32, pixelData, topDown = true)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = image.get(x, y)
                out[p++] = PixelImage.b(c).toByte()
                out[p++] = PixelImage.g(c).toByte()
                out[p++] = PixelImage.r(c).toByte()
                out[p++] = PixelImage.a(c).toByte()
            }
        }
        return out
    }

    private fun writeHeaders(
        out: ByteArray,
        w: Int,
        h: Int,
        bpp: Int,
        pixelDataSize: Int,
        topDown: Boolean,
    ): Int {
        // BITMAPFILEHEADER
        out[0] = 'B'.code.toByte()
        out[1] = 'M'.code.toByte()
        le32(out, 2, FILE_HEADER + INFO_HEADER + pixelDataSize) // bfSize
        le32(out, 6, 0)                                         // reserved
        le32(out, 10, FILE_HEADER + INFO_HEADER)                // bfOffBits
        // BITMAPINFOHEADER
        le32(out, 14, INFO_HEADER)
        le32(out, 18, w)
        le32(out, 22, if (topDown) -h else h)
        le16(out, 26, 1)                                        // planes
        le16(out, 28, bpp)
        le32(out, 30, 0)                                        // BI_RGB
        le32(out, 34, pixelDataSize)
        le32(out, 38, 2835)                                     // ~72 DPI
        le32(out, 42, 2835)
        le32(out, 46, 0)
        le32(out, 50, 0)
        return FILE_HEADER + INFO_HEADER
    }

    /** @throws ImageCodecException on malformed or unsupported input. */
    fun decode(bytes: ByteArray): PixelImage {
        if (bytes.size < FILE_HEADER + INFO_HEADER) throw ImageCodecException("BMP too small")
        if (bytes[0] != 'B'.code.toByte() || bytes[1] != 'M'.code.toByte()) {
            throw ImageCodecException("Not a BMP (bad magic)")
        }
        val dataOffset = rle32(bytes, 10)
        val headerSize = rle32(bytes, 14)
        if (headerSize < INFO_HEADER) throw ImageCodecException("Unsupported BMP header size $headerSize")
        val width = rle32(bytes, 18)
        val rawHeight = rle32(bytes, 22)
        val topDown = rawHeight < 0
        val height = kotlin.math.abs(rawHeight)
        val bpp = rle16(bytes, 28)
        val compression = rle32(bytes, 30)
        if (compression != 0) throw ImageCodecException("Only uncompressed (BI_RGB) BMP is supported")
        if (bpp != 24 && bpp != 32) throw ImageCodecException("Only 24/32-bit BMP is supported (got $bpp)")
        if (width <= 0 || height <= 0) throw ImageCodecException("Invalid BMP dimensions ${width}x$height")

        val bytesPerPixel = bpp / 8
        val rowBytes = width * bytesPerPixel
        val paddedRow = if (bpp == 24) rowBytes + (4 - rowBytes % 4) % 4 else rowBytes
        val pixels = IntArray(width * height)
        for (row in 0 until height) {
            val srcY = if (topDown) row else height - 1 - row
            var p = dataOffset + row * paddedRow
            if (p + rowBytes > bytes.size) throw ImageCodecException("BMP pixel data truncated")
            for (x in 0 until width) {
                val b = bytes[p++].toInt() and 0xFF
                val g = bytes[p++].toInt() and 0xFF
                val r = bytes[p++].toInt() and 0xFF
                val a = if (bpp == 32) bytes[p++].toInt() and 0xFF else 0xFF
                // 32-bit BI_RGB often stores 0 in the alpha byte; treat all-zero
                // alpha as opaque so such files are not rendered fully transparent.
                pixels[srcY * width + x] = PixelImage.argb(a, r, g, b)
            }
        }
        // If a 32-bit image came in fully transparent (alpha byte unused), force opaque.
        if (bpp == 32 && pixels.all { (it ushr 24) == 0 }) {
            for (i in pixels.indices) pixels[i] = pixels[i] or (0xFF shl 24)
        }
        return PixelImage(width, height, pixels)
    }

    private fun le16(a: ByteArray, o: Int, v: Int) {
        a[o] = (v and 0xFF).toByte()
        a[o + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun le32(a: ByteArray, o: Int, v: Int) {
        a[o] = (v and 0xFF).toByte()
        a[o + 1] = ((v ushr 8) and 0xFF).toByte()
        a[o + 2] = ((v ushr 16) and 0xFF).toByte()
        a[o + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun rle16(a: ByteArray, o: Int): Int =
        (a[o].toInt() and 0xFF) or ((a[o + 1].toInt() and 0xFF) shl 8)

    private fun rle32(a: ByteArray, o: Int): Int =
        (a[o].toInt() and 0xFF) or ((a[o + 1].toInt() and 0xFF) shl 8) or
            ((a[o + 2].toInt() and 0xFF) shl 16) or ((a[o + 3].toInt() and 0xFF) shl 24)
}
