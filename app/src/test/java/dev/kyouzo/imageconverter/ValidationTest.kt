package dev.kyouzo.imageconverter

import dev.kyouzo.imageconverter.format.ColorUtils
import dev.kyouzo.imageconverter.format.ConversionError
import dev.kyouzo.imageconverter.format.ConversionRequest
import dev.kyouzo.imageconverter.format.ConversionValidation
import dev.kyouzo.imageconverter.format.ImageFormat
import dev.kyouzo.imageconverter.format.ImageFormatRegistry
import dev.kyouzo.imageconverter.format.PixelImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    private val registry = ImageFormatRegistry(37)

    @Test fun validRequestPasses() {
        assertNull(ConversionValidation.validate(registry, ConversionRequest(ImageFormat.PNG), ImageFormat.JPEG))
    }

    @Test fun unencodableTargetBlocked() {
        val e = ConversionValidation.validate(registry, ConversionRequest(ImageFormat.TIFF))
        assertTrue(e is ConversionError.EncoderUnavailable)
    }

    @Test fun qualityRangeChecked() {
        val e = ConversionValidation.validate(registry, ConversionRequest(ImageFormat.JPEG, quality = 500))
        assertTrue(e is ConversionError.QualityOutOfRange)
    }

    @Test fun samLosslessFormatFlagged() {
        val e = ConversionValidation.validate(registry, ConversionRequest(ImageFormat.PNG), ImageFormat.PNG)
        assertTrue(e is ConversionError.SameFormatNoChange)
    }

    @Test fun defaults() {
        assertEquals(90, ConversionValidation.effectiveQuality(ConversionRequest(ImageFormat.JPEG)))
        assertEquals(ColorUtils.WHITE, ConversionValidation.effectiveBackground(ConversionRequest(ImageFormat.JPEG)))
    }

    @Test fun jpegBackgroundFlatten() {
        // Transparent pixel over white becomes opaque white.
        assertEquals(0xFFFFFFFF.toInt(), ColorUtils.flattenOver(0x00000000, ColorUtils.WHITE))
        // Opaque pixel is unchanged.
        assertEquals(0xFFFF0000.toInt(), ColorUtils.flattenOver(0xFFFF0000.toInt(), ColorUtils.WHITE))
        // 50% black over white is mid-gray and fully opaque.
        val half = ColorUtils.flattenOver(0x80000000.toInt(), ColorUtils.WHITE)
        assertEquals(0xFF, PixelImage.a(half))
        assertTrue(PixelImage.r(half) in 126..129)
    }
}
