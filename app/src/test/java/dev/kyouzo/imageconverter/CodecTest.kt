package dev.kyouzo.imageconverter

import dev.kyouzo.imageconverter.format.PixelImage
import dev.kyouzo.imageconverter.format.codec.BmpCodec
import dev.kyouzo.imageconverter.format.codec.IcoCodec
import dev.kyouzo.imageconverter.format.codec.ImageCodecException
import dev.kyouzo.imageconverter.format.codec.TgaCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecTest {

    private fun testImage(w: Int, h: Int): PixelImage {
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val a = (((x + y) * 31) and 0xFF).let { if (it < 16) 0xFF else it }
            px[y * w + x] = PixelImage.argb(a, (x * 37) and 0xFF, (y * 53) and 0xFF, ((x * y) * 7) and 0xFF)
        }
        return PixelImage(w, h, px)
    }

    private fun rgbEquals(a: PixelImage, b: PixelImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (i in a.pixels.indices) if ((a.pixels[i] and 0x00FFFFFF) != (b.pixels[i] and 0x00FFFFFF)) return false
        return true
    }

    @Test fun bmp24RoundTrip() {
        val img = testImage(5, 3)
        val dec = BmpCodec.decode(BmpCodec.encode(img, includeAlpha = false))
        assertEquals(5, dec.width); assertEquals(3, dec.height)
        assertTrue(rgbEquals(img, dec))
    }

    @Test fun bmp32RoundTripPreservesAlpha() {
        val img = testImage(5, 3)
        val dec = BmpCodec.decode(BmpCodec.encode(img, includeAlpha = true))
        assertTrue(img.pixels.contentEquals(dec.pixels))
    }

    @Test(expected = ImageCodecException::class)
    fun bmpRejectsGarbage() {
        BmpCodec.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
    }

    @Test fun tga32RoundTrip() {
        val img = testImage(7, 4)
        val dec = TgaCodec.decode(TgaCodec.encode(img, includeAlpha = true))
        assertEquals(7, dec.width); assertEquals(4, dec.height)
        assertTrue(img.pixels.contentEquals(dec.pixels))
    }

    @Test fun tga24RoundTrip() {
        val img = testImage(7, 4)
        val dec = TgaCodec.decode(TgaCodec.encode(img, includeAlpha = false))
        assertTrue(rgbEquals(img, dec))
    }

    @Test fun icoEmbeddedPngIsDetected() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
        val ico = buildIco(16, 16, 32, png)
        val entries = IcoCodec.parseDirectory(ico)
        assertEquals(1, entries.size)
        assertEquals(16, entries[0].width)
        val res = IcoCodec.extractBest(ico)
        assertTrue(res is IcoCodec.Result.Png)
        assertTrue((res as IcoCodec.Result.Png).bytes.contentEquals(png))
    }

    @Test fun icoDibIsDecoded() {
        val dib = buildDib32(2, 2)
        val ico = buildIco(2, 2, 32, dib)
        val res = IcoCodec.extractBest(ico)
        assertTrue(res is IcoCodec.Result.Raster)
        val im = (res as IcoCodec.Result.Raster).image
        assertEquals(2, im.width); assertEquals(2, im.height)
    }

    // --- helpers to synthesize ICO containers ---
    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v ushr 8) and 0xFF).toByte())
    private fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v ushr 8) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(), ((v ushr 24) and 0xFF).toByte(),
    )

    private fun buildIco(w: Int, h: Int, bpp: Int, payload: ByteArray): ByteArray {
        val out = ArrayList<Byte>()
        fun add(b: ByteArray) = b.forEach { out.add(it) }
        add(le16(0)); add(le16(1)); add(le16(1))
        out.add((if (w == 256) 0 else w).toByte())
        out.add((if (h == 256) 0 else h).toByte())
        out.add(0); out.add(0)
        add(le16(1)); add(le16(bpp))
        add(le32(payload.size))
        add(le32(6 + 16))
        add(payload)
        return out.toByteArray()
    }

    private fun buildDib32(w: Int, h: Int): ByteArray {
        val out = ArrayList<Byte>()
        fun add(b: ByteArray) = b.forEach { out.add(it) }
        add(le32(40)); add(le32(w)); add(le32(h * 2))
        add(le16(1)); add(le16(32)); add(le32(0))
        add(le32(0)); add(le32(0)); add(le32(0)); add(le32(0)); add(le32(0))
        for (y in 0 until h) for (x in 0 until w) {
            out.add(0x10); out.add(0x20); out.add(0x30); out.add(0xFF.toByte())
        }
        val maskRow = ((w + 31) / 32) * 4
        repeat(maskRow * h) { out.add(0) }
        return out.toByteArray()
    }
}
