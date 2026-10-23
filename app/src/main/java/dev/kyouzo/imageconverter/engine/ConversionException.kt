package dev.kyouzo.imageconverter.engine

/** A failure during decode/convert/encode/save with a message safe to show the user. */
class ConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)
