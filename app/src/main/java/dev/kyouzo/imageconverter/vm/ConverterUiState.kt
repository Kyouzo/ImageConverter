package dev.kyouzo.imageconverter.vm

import androidx.compose.ui.graphics.ImageBitmap
import dev.kyouzo.imageconverter.engine.ConversionOutput
import dev.kyouzo.imageconverter.engine.SelectedImage
import dev.kyouzo.imageconverter.format.Capability
import dev.kyouzo.imageconverter.format.ImageFormat

/** The high-level screen phase. */
enum class Phase { EMPTY, LOADING, READY, CONVERTING, RESULT }

/** An output format row for the selector, with availability + reason. */
data class OutputOption(
    val format: ImageFormat,
    val capability: Capability,
) {
    val enabled: Boolean get() = capability.available
}

/** Immutable UI state rendered by the Compose screen. */
data class ConverterUiState(
    val phase: Phase = Phase.EMPTY,
    val source: SelectedImage? = null,
    val previewBitmap: ImageBitmap? = null,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val sourceHasAlpha: Boolean = false,

    val outputOptions: List<OutputOption> = emptyList(),
    val target: ImageFormat = ImageFormat.PNG,
    val quality: Int = 90,
    val qualityApplicable: Boolean = false,
    val needsBackgroundChoice: Boolean = false,
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),

    val result: ConversionOutput? = null,
    val resultPreview: ImageBitmap? = null,
    val savedMessage: String? = null,

    // Deleting the original after a successful save.
    val requestDeletionAfterSave: Boolean = false,
    val originalDeleted: Boolean = false,
    val deleteStatus: String? = null,

    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    val canConvert: Boolean
        get() = (phase == Phase.READY) && outputOptions.any { it.format == target && it.enabled }
}
