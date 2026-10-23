package dev.kyouzo.imageconverter.format.codec

import dev.kyouzo.imageconverter.format.PixelImage

/**
 * Pure-Kotlin ICO reader. An .ico is a directory of images; each entry is either
 * an embedded PNG or a Windows DIB (BITMAPINFOHEADER with a doubled height that
 * includes a 1-bpp AND transparency mask).
 *
 * [extractBest] picks the largest/highest-depth entry and returns either the raw
 * PNG bytes (to be handed to the platform PNG decoder) or a decoded [PixelImage]
 * for DIB entries. Supports 32- and 24-bit DIBs with the AND mask applied.
 *
 * Unit tested on the host.
 */
object IcoCodec {

    private val PNG_SIG = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    data class Entry(
        val width: Int,   // 0 in the directory means 256
        val height: Int,
        val bitCount: Int,
        val dataOffset: Int,
        val dataLength: Int,
    )

    /** Either embedded PNG bytes, or an already-decoded DIB image. */
    sealed interface Result {
        data class Png(val bytes: ByteArray) : Result
        data class Raster(val image: PixelImage) : Result
    }

    fun parseDirectory(bytes: ByteArray): List<Entry> {
        if (bytes.size < 6) throw ImageCodecException("ICO too small")
        if (rle16(bytes, 0) != 0 || rle16(bytes, 2) != 1) throw ImageCodecException("Not an ICO file")
        val count = rle16(bytes, 4)
        if (count <= 0) throw ImageCodecException("ICO has no images")
        val entries = ArrayList<Entry>(count)
        var p = 6
        repeat(count) {
            if (p + 16 > bytes.size) throw ImageCodecException("ICO directory truncated")
            val w = (bytes[p].toInt() and 0xFF).let { if (it == 0) 256 else it }
            val h = (bytes[p + 1].toInt() and 0xFF).let { if (it == 0) 256 else it }
            val bitCount = rle16(bytes, p + 6)
            val len = rle32(bytes, p + 8)
            val off = rle32(bytes, p + 12)
            entries.add(Entry(w, h, bitCount, off, len))
            p += 16
        }
        return entries
    }

    fun bestEntry(entries: List<Entry>): Entry =
        entries.maxWith(compareBy({ it.width * it.height }, { it.bitCount }))

    fun extractBest(bytes: ByteArray): Result {
        val entry = bestEntry(parseDirectory(bytes))
        if (entry.dataOffset + entry.dataLength > bytes.size) throw ImageCodecException("ICO image data truncated")
        val isPng = entry.dataLength >= 8 && (0 until 8).all {
            (bytes[entry.dataOffset + it].toInt() and 0xFF) == PNG_SIG[it]
        }
        if (isPng) {
            return Result.Png(bytes.copyOfRange(entry.dataOffset, entry.dataOffset + entry.dataLength))
        }
        return Result.Raster(decodeDib(bytes, entry))
    }

    private fun decodeDib(bytes: ByteArray, entry: Entry): PixelImage {
        val base = entry.dataOffset
        val headerSize = rle32(bytes, base)
        if (headerSize < 40) throw ImageCodecException("Unsupported ICO DIB header")
        val width = rle32(bytes, base + 4)
        // In an ICO, biHeight is twice the real height (color data + AND mask).
        val storedHeight = rle32(bytes, base + 8)
        val height = storedHeight / 2
        val bpp = rle16(bytes, base + 14)
        if (width <= 0 || height <= 0) throw ImageCodecException("Invalid ICO dimensions")
        if (bpp != 32 && bpp != 24) {
            throw ImageCodecException("Only 24/32-bit ICO DIB entries are supported (got $bpp)")
        }
        val bytesPerPixel = bpp / 8
        val rowBytes = width * bytesPerPixel
        val xorPadded = rowBytes + (4 - rowBytes % 4) % 4
        var p = base + headerSize
        val pixels = IntArray(width * height)
        // XOR (color) rows are stored bottom-up.
        for (row in 0 until height) {
            val y = height - 1 - row
            var q = p + row * xorPadded
            for (x in 0 until width) {
                val b = bytes[q++].toInt() and 0xFF
                val g = bytes[q++].toInt() and 0xFF
                val r = bytes[q++].toInt() and 0xFF
                val a = if (bpp == 32) bytes[q++].toInt() and 0xFF else 0xFF
                pixels[y * width + x] = PixelImage.argb(a, r, g, b)
            }
        }
        // AND mask (1 bpp, bottom-up, rows padded to 32 bits): 1 => transparent.
        val maskStart = p + height * xorPadded
        val maskRowBytes = ((width + 31) / 32) * 4
        val hasMaskRoom = maskStart + height * maskRowBytes <= bytes.size
        val colorHasAlpha = bpp == 32 && pixels.any { (it ushr 24) != 0 }
        if (hasMaskRoom && !colorHasAlpha) {
            for (row in 0 until height) {
                val y = height - 1 - row
                val rowOff = maskStart + row * maskRowBytes
                for (x in 0 until width) {
                    val bit = (bytes[rowOff + x / 8].toInt() shr (7 - (x % 8))) and 1
                    if (bit == 1) pixels[y * width + x] = pixels[y * width + x] and 0x00FFFFFF
                    else pixels[y * width + x] = pixels[y * width + x] or (0xFF shl 24)
                }
            }
        } else if (!colorHasAlpha) {
            for (i in pixels.indices) pixels[i] = pixels[i] or (0xFF shl 24)
        }
        return PixelImage(width, height, pixels)
    }

    private fun rle16(a: ByteArray, o: Int): Int =
        (a[o].toInt() and 0xFF) or ((a[o + 1].toInt() and 0xFF) shl 8)

    private fun rle32(a: ByteArray, o: Int): Int =
        (a[o].toInt() and 0xFF) or ((a[o + 1].toInt() and 0xFF) shl 8) or
            ((a[o + 2].toInt() and 0xFF) shl 16) or ((a[o + 3].toInt() and 0xFF) shl 24)
}
