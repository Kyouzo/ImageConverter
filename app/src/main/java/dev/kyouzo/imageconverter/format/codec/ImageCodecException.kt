package dev.kyouzo.imageconverter.format.codec

/** Thrown by the pure-Kotlin codecs when input is malformed or unsupported. */
class ImageCodecException(message: String, cause: Throwable? = null) : Exception(message, cause)
