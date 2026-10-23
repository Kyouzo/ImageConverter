package dev.kyouzo.imageconverter.vm

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kyouzo.imageconverter.engine.AndroidImageDecoder
import dev.kyouzo.imageconverter.engine.ConversionException
import dev.kyouzo.imageconverter.engine.ConversionOutput
import dev.kyouzo.imageconverter.engine.ImageConverter
import dev.kyouzo.imageconverter.engine.ImageMetadataReader
import dev.kyouzo.imageconverter.engine.ImageSaver
import dev.kyouzo.imageconverter.engine.ImageSharer
import dev.kyouzo.imageconverter.engine.SelectedImage
import dev.kyouzo.imageconverter.format.ConversionRequest
import dev.kyouzo.imageconverter.format.ImageFormat
import dev.kyouzo.imageconverter.format.ImageFormatRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConverterViewModel(app: Application) : AndroidViewModel(app) {

    private val registry = ImageFormatRegistry(Build.VERSION.SDK_INT)
    private val metadataReader = ImageMetadataReader(app.contentResolver)
    private val decoder = AndroidImageDecoder(app.contentResolver, registry)
    private val converter = ImageConverter(app, registry)
    private val saver = ImageSaver(app)
    private val sharer = ImageSharer(app)

    private val _state = MutableStateFlow(
        ConverterUiState(outputOptions = buildOptions())
    )
    val state = _state.asStateFlow()

    private var conversionJob: Job? = null

    private fun buildOptions(): List<OutputOption> =
        registry.outputOptions().map { (fmt, cap) -> OutputOption(fmt, cap) }

    private val maxPreviewEdge = 2048

    /** Called when the Photo Picker returns a URI (or null if the user cancelled). */
    fun onImagePicked(uri: Uri?) {
        if (uri == null) {
            // Cancellation: keep current state, no error.
            return
        }
        converter.clearCache()
        _state.update {
            ConverterUiState(
                phase = Phase.LOADING,
                outputOptions = buildOptions(),
            )
        }
        viewModelScope.launch {
            try {
                val meta = withContext(Dispatchers.IO) { metadataReader.read(uri) }
                val full = withContext(Dispatchers.Default) {
                    try { decoder.decode(uri, meta.detectedFormat) }
                    catch (oom: OutOfMemoryError) { throw ConversionException("This image is too large to preview on this device") }
                }
                val trueW = full.width
                val trueH = full.height
                val hasAlpha = full.hasAlpha()
                val preview = scaleForPreview(full)
                if (preview !== full) full.recycle()

                val source = meta.copy(
                    width = if (meta.width > 0) meta.width else trueW,
                    height = if (meta.height > 0) meta.height else trueH,
                )
                val defaultTarget = defaultTargetFor(meta.detectedFormat)
                _state.update {
                    it.copy(
                        phase = Phase.READY,
                        source = source,
                        previewBitmap = preview.asImageBitmap(),
                        sourceWidth = trueW,
                        sourceHeight = trueH,
                        sourceHasAlpha = hasAlpha,
                        target = defaultTarget,
                        qualityApplicable = registry.qualityApplicable(defaultTarget),
                        needsBackgroundChoice = needsBackground(defaultTarget, hasAlpha),
                        errorMessage = null,
                        infoMessage = null,
                        result = null,
                        resultPreview = null,
                        savedMessage = null,
                    )
                }
            } catch (e: ConversionException) {
                _state.update { ConverterUiState(phase = Phase.EMPTY, outputOptions = buildOptions(), errorMessage = e.message) }
            } catch (e: Exception) {
                _state.update { ConverterUiState(phase = Phase.EMPTY, outputOptions = buildOptions(), errorMessage = "Could not open this image: ${e.message}") }
            }
        }
    }

    fun onTargetSelected(format: ImageFormat) {
        _state.update {
            it.copy(
                target = format,
                qualityApplicable = registry.qualityApplicable(format),
                needsBackgroundChoice = needsBackground(format, it.sourceHasAlpha),
                infoMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onQualityChanged(value: Int) {
        _state.update { it.copy(quality = value.coerceIn(1, 100)) }
    }

    fun onBackgroundColorChanged(color: Int) {
        _state.update { it.copy(backgroundColor = color) }
    }

    fun convert() {
        val s = _state.value
        val source = s.source ?: return
        if (conversionJob?.isActive == true) return // prevent duplicate jobs

        _state.update { it.copy(phase = Phase.CONVERTING, errorMessage = null, infoMessage = null) }
        conversionJob = viewModelScope.launch {
            try {
                val request = ConversionRequest(
                    target = s.target,
                    quality = if (s.qualityApplicable) s.quality else null,
                    jpegBackgroundColor = if (s.needsBackgroundChoice) s.backgroundColor else null,
                    sourceHasAlpha = s.sourceHasAlpha,
                )
                val output = converter.convert(source, request)
                val resultPreview = withContext(Dispatchers.Default) { decodeResultPreview(output) }
                _state.update {
                    it.copy(
                        phase = Phase.RESULT,
                        result = output,
                        resultPreview = resultPreview?.asImageBitmap(),
                        savedMessage = null,
                    )
                }
            } catch (e: ConversionException) {
                _state.update { it.copy(phase = Phase.READY, errorMessage = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(phase = Phase.READY, errorMessage = "Conversion failed: ${e.message}") }
            }
        }
    }

    fun save() {
        val output = _state.value.result ?: return
        viewModelScope.launch {
            try {
                saver.save(output)
                _state.update { it.copy(savedMessage = "Saved to Pictures/${ImageSaver.SUBDIR}") }
            } catch (e: ConversionException) {
                _state.update { it.copy(errorMessage = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Save failed: ${e.message}") }
            }
        }
    }

    /** Build the share chooser Intent (the Activity launches it). */
    fun buildShareIntent(): Intent? =
        _state.value.result?.let {
            runCatching { sharer.shareIntent(it) }.getOrNull()
        }

    fun convertAnother() {
        converter.clearCache()
        _state.update { ConverterUiState(phase = Phase.EMPTY, outputOptions = buildOptions()) }
    }

    fun clearSelection() = convertAnother()

    fun consumeSavedMessage() = _state.update { it.copy(savedMessage = null) }
    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    /** True on API < 29 where a legacy write permission is needed before saving. */
    fun requiresLegacyStoragePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private fun needsBackground(target: ImageFormat, sourceHasAlpha: Boolean): Boolean =
        !target.supportsAlpha && sourceHasAlpha

    private fun defaultTargetFor(source: ImageFormat?): ImageFormat {
        val encodable = registry.encodableFormats()
        val preferred = if (source == ImageFormat.JPEG) ImageFormat.PNG else ImageFormat.JPEG
        return when {
            preferred in encodable && preferred != source -> preferred
            else -> encodable.firstOrNull { it != source } ?: encodable.first()
        }
    }

    private fun scaleForPreview(src: Bitmap): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxPreviewEdge) return src
        val scale = maxPreviewEdge.toFloat() / longest
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun decodeResultPreview(output: ConversionOutput): Bitmap? {
        // Only platform-decodable results get a result thumbnail; BMP/TGA are not
        // decodable by BitmapFactory, so we skip the preview for those.
        return try {
            when (output.format) {
                ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP -> {
                    val bmp = android.graphics.BitmapFactory.decodeFile(output.file.absolutePath)
                    bmp?.let { scaleForPreview(it).also { s -> if (s !== it) it.recycle() } }
                }
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
