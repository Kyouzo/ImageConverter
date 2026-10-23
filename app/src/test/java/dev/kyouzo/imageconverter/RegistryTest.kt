package dev.kyouzo.imageconverter

import dev.kyouzo.imageconverter.format.CodecSource
import dev.kyouzo.imageconverter.format.ImageFormat
import dev.kyouzo.imageconverter.format.ImageFormatRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryTest {

    private val modern = ImageFormatRegistry(37)
    private val legacy = ImageFormatRegistry(26)

    @Test fun apiGatedDecoders() {
        assertTrue(modern.canDecode(ImageFormat.HEIC))
        assertTrue(modern.canDecode(ImageFormat.AVIF))
        assertFalse(legacy.canDecode(ImageFormat.HEIC))
        assertFalse(legacy.canDecode(ImageFormat.AVIF))
    }

    @Test fun decoderSources() {
        assertEquals(CodecSource.PLATFORM, modern.decoder(ImageFormat.BMP).source)
        assertEquals(CodecSource.CUSTOM, modern.decoder(ImageFormat.TGA).source)
        assertEquals(CodecSource.CUSTOM, modern.decoder(ImageFormat.ICO).source)
        assertEquals(CodecSource.NONE, modern.decoder(ImageFormat.TIFF).source)
        assertNotNull(modern.decoder(ImageFormat.TIFF).reason)
    }

    @Test fun encoderSources() {
        assertEquals(CodecSource.PLATFORM, modern.encoder(ImageFormat.JPEG).source)
        assertEquals(CodecSource.PLATFORM, modern.encoder(ImageFormat.PNG).source)
        assertEquals(CodecSource.PLATFORM, modern.encoder(ImageFormat.WEBP).source)
        assertEquals(CodecSource.CUSTOM, modern.encoder(ImageFormat.BMP).source)
        assertEquals(CodecSource.CUSTOM, modern.encoder(ImageFormat.TGA).source)
        assertEquals(CodecSource.NONE, modern.encoder(ImageFormat.GIF).source)
        assertEquals(CodecSource.NONE, modern.encoder(ImageFormat.HEIC).source)
        assertEquals(CodecSource.NONE, modern.encoder(ImageFormat.AVIF).source)
        assertNotNull(modern.encoder(ImageFormat.HEIC).reason)
    }

    @Test fun qualityApplicability() {
        assertTrue(modern.qualityApplicable(ImageFormat.JPEG))
        assertTrue(modern.qualityApplicable(ImageFormat.WEBP))
        assertFalse(modern.qualityApplicable(ImageFormat.PNG))
        // AVIF is lossy but has no encoder -> no quality slider
        assertFalse(modern.qualityApplicable(ImageFormat.AVIF))
    }

    @Test fun encodableSet() {
        assertEquals(
            setOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP, ImageFormat.BMP, ImageFormat.TGA),
            modern.encodableFormats().toSet(),
        )
        assertEquals(ImageFormat.entries.size, modern.outputOptions().size)
    }
}
