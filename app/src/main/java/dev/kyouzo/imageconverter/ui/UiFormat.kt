package dev.kyouzo.imageconverter.ui

import java.util.Locale

/** Human-readable byte size, e.g. 1536 -> "1.5 KB". */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024; unit++
    }
    return if (unit == 0) "$bytes B"
    else String.format(Locale.US, "%.1f %s", value, units[unit])
}

/** Dimensions label, e.g. "1920 × 1080". */
fun formatDimensions(w: Int, h: Int): String =
    if (w > 0 && h > 0) "$w × $h" else "Unknown size"
