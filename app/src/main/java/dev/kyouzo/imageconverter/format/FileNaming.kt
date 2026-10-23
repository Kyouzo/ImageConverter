package dev.kyouzo.imageconverter.format

/**
 * Builds output file names: preserves the original base name, swaps the
 * extension for the chosen output format, and generates a unique name when a
 * collision would occur. Pure logic, unit tested on the host.
 */
object FileNaming {

    private const val FALLBACK_BASE = "image"

    // Characters that are unsafe in Android/Windows display names.
    private val ILLEGAL = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

    /** Strip the directory and the extension from a file name, returning the base. */
    fun baseName(fileName: String?): String {
        if (fileName.isNullOrBlank()) return FALLBACK_BASE
        // Drop any path component (handle both separators).
        val name = fileName.substringAfterLast('/').substringAfterLast('\\')
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val sanitized = sanitize(base)
        return sanitized.ifBlank { FALLBACK_BASE }
    }

    private fun sanitize(value: String): String {
        val sb = StringBuilder(value.length)
        for (c in value) {
            if (c in ILLEGAL || c.code < 0x20) sb.append('_') else sb.append(c)
        }
        // Collapse whitespace runs and trim trailing dots/spaces/underscores.
        return sb.toString().replace(Regex("\\s+"), " ").trim().trimEnd('.', '_', ' ')
    }

    /**
     * Produce the output file name for converting [originalName] to [target].
     * e.g. ("holiday.webp", JPEG) -> "holiday.jpg".
     */
    fun outputName(originalName: String?, target: ImageFormat): String =
        "${baseName(originalName)}.${target.primaryExtension}"

    /**
     * Ensure the name does not collide. If [exists] reports the candidate is
     * taken, append " (1)", " (2)", ... before the extension until it is free.
     *
     * @param fileName a full name such as "holiday.jpg".
     * @param exists   predicate that returns true if a file with that name already exists.
     */
    fun uniqueName(fileName: String, exists: (String) -> Boolean): String {
        if (!exists(fileName)) return fileName
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!exists(candidate)) return candidate
            i++
        }
    }
}
