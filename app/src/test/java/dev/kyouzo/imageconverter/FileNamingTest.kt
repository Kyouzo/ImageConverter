package dev.kyouzo.imageconverter

import dev.kyouzo.imageconverter.format.FileNaming
import dev.kyouzo.imageconverter.format.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamingTest {

    @Test fun baseNameHandlesPathsAndExtensions() {
        assertEquals("holiday", FileNaming.baseName("/a/b/holiday.webp"))
        assertEquals("scan", FileNaming.baseName("C:\\pics\\scan.jpg"))
        assertEquals("photo", FileNaming.baseName("photo"))
        assertEquals("image", FileNaming.baseName(null))
        assertEquals("c", FileNaming.baseName("a/b/c.png"))
    }

    @Test fun baseNameSanitizesIllegalCharacters() {
        assertEquals("a_b_c", FileNaming.baseName("a:b*c?.png"))
    }

    @Test fun outputNameSwapsExtension() {
        assertEquals("holiday.jpg", FileNaming.outputName("holiday.webp", ImageFormat.JPEG))
        assertEquals("screenshot.webp", FileNaming.outputName("screenshot.png", ImageFormat.WEBP))
        assertEquals("scan.png", FileNaming.outputName("scan.jpg", ImageFormat.PNG))
        assertEquals("image.png", FileNaming.outputName(null, ImageFormat.PNG))
    }

    @Test fun uniqueNameAvoidsCollisions() {
        val taken = setOf("holiday.jpg", "holiday (1).jpg")
        assertEquals("holiday (2).jpg", FileNaming.uniqueName("holiday.jpg") { it in taken })
        assertEquals("fresh.jpg", FileNaming.uniqueName("fresh.jpg") { it in taken })
    }
}
