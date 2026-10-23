package dev.kyouzo.imageconverter

import dev.kyouzo.imageconverter.format.FormatSignatures
import dev.kyouzo.imageconverter.format.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatDetectionTest {

    @Test fun extensionMapping() {
        assertEquals(ImageFormat.JPEG, ImageFormat.fromExtension("jpg"))
        assertEquals(ImageFormat.JPEG, ImageFormat.fromExtension(".JPEG"))
        assertEquals(ImageFormat.JPEG, ImageFormat.fromExtension("jfif"))
        assertEquals(ImageFormat.TIFF, ImageFormat.fromExtension("tif"))
        assertNull(ImageFormat.fromExtension("xyz"))
    }

    @Test fun fileNameMapping() {
        assertEquals(ImageFormat.WEBP, ImageFormat.fromFileName("holiday.webp"))
        assertNull(ImageFormat.fromFileName("holiday"))
        assertNull(ImageFormat.fromFileName("holiday."))
    }

    @Test fun formatProperties() {
        assertEquals(false, ImageFormat.JPEG.supportsAlpha)
        assertEquals(true, ImageFormat.PNG.supportsAlpha)
        assertEquals(true, ImageFormat.JPEG.lossy)
        assertEquals(false, ImageFormat.PNG.lossy)
    }

    @Test fun signatureDetection() {
        assertEquals(ImageFormat.JPEG, FormatSignatures.detect(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)))
        assertEquals(ImageFormat.PNG, FormatSignatures.detect(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals(ImageFormat.GIF, FormatSignatures.detect("GIF89a..".toByteArray(Charsets.US_ASCII)))
        assertEquals(ImageFormat.GIF, FormatSignatures.detect("GIF87a..".toByteArray(Charsets.US_ASCII)))
        assertEquals(ImageFormat.BMP, FormatSignatures.detect("BM....".toByteArray(Charsets.US_ASCII)))
        assertEquals(ImageFormat.TIFF, FormatSignatures.detect(byteArrayOf(0x49, 0x49, 0x2A, 0x00)))
        assertEquals(ImageFormat.TIFF, FormatSignatures.detect(byteArrayOf(0x4D, 0x4D, 0x00, 0x2A)))
        assertEquals(ImageFormat.ICO, FormatSignatures.detect(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x10)))
        assertEquals(ImageFormat.JXL, FormatSignatures.detect(byteArrayOf(0xFF.toByte(), 0x0A)))
        assertNull(FormatSignatures.detect(byteArrayOf(1, 2, 3, 4)))
    }

    @Test fun signatureWebp() {
        val b = ByteArray(16)
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(b, 0)
        "WEBP".toByteArray(Charsets.US_ASCII).copyInto(b, 8)
        assertEquals(ImageFormat.WEBP, FormatSignatures.detect(b))
    }

    @Test fun signatureIsoBmff() {
        fun ftyp(brand: String): ByteArray {
            val b = ByteArray(16)
            "ftyp".toByteArray(Charsets.US_ASCII).copyInto(b, 4)
            brand.toByteArray(Charsets.US_ASCII).copyInto(b, 8)
            return b
        }
        assertEquals(ImageFormat.AVIF, FormatSignatures.detect(ftyp("avif")))
        assertEquals(ImageFormat.HEIC, FormatSignatures.detect(ftyp("heic")))
        assertEquals(ImageFormat.HEIF, FormatSignatures.detect(ftyp("mif1")))
    }

    @Test fun tgaFooterDetection() {
        val footer = "TRUEVISION-XFILE.".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val b = ByteArray(40) + footer
        assertEquals(ImageFormat.TGA, FormatSignatures.detect(b))
    }
}
