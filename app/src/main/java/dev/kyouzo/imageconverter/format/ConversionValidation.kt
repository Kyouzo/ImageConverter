package dev.kyouzo.imageconverter.format

/** A requested conversion, independent of any Android object. */
data class ConversionRequest(
    val target: ImageFormat,
    val quality: Int? = null,          // 1..100, only meaningful for lossy targets
    val jpegBackgroundColor: Int? = null, // 0xFFRRGGBB, used when flattening alpha to JPEG
    val sourceHasAlpha: Boolean = false,
)

/** The reason a conversion request is invalid. [message] is user-facing. */
sealed class ConversionError(val message: String) {
    class EncoderUnavailable(format: ImageFormat, reason: String?) :
        ConversionError(reason ?: "${format.displayName} cannot be encoded on this device")

    class QualityOutOfRange(val value: Int) :
        ConversionError("Quality must be between 1 and 100 (was $value)")

    object SameFormatNoChange :
        ConversionError("The image is already in this format")
}

/** Validates a [ConversionRequest] against device capabilities. Pure logic. */
object ConversionValidation {

    /**
     * @param sourceFormat the detected input format, or null if unknown.
     * @return the first blocking [ConversionError], or null if the request is valid.
     */
    fun validate(
        registry: ImageFormatRegistry,
        request: ConversionRequest,
        sourceFormat: ImageFormat? = null,
    ): ConversionError? {
        val encoder = registry.encoder(request.target)
        if (!encoder.available) {
            return ConversionError.EncoderUnavailable(request.target, encoder.reason)
        }
        if (registry.qualityApplicable(request.target)) {
            val q = request.quality
            if (q != null && (q < 1 || q > 100)) {
                return ConversionError.QualityOutOfRange(q)
            }
        }
        // Converting to the identical format is allowed (e.g. re-encode/quality
        // change) EXCEPT it is a no-op for lossless same-format; surface as info,
        // not a hard error, so callers may choose to proceed. We only flag it when
        // source is known AND equals target AND target is lossless.
        if (sourceFormat != null && sourceFormat == request.target && !request.target.lossy) {
            return ConversionError.SameFormatNoChange
        }
        return null
    }

    /** The effective JPEG background, defaulting to white when none is chosen. */
    fun effectiveBackground(request: ConversionRequest): Int =
        request.jpegBackgroundColor ?: ColorUtils.WHITE

    /** The effective quality, defaulting to 90 for lossy targets. */
    fun effectiveQuality(request: ConversionRequest): Int =
        (request.quality ?: 90).coerceIn(1, 100)
}
