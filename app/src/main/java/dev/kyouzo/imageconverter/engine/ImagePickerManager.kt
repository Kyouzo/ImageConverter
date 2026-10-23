package dev.kyouzo.imageconverter.engine

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia

/**
 * Centralizes the Android Photo Picker configuration so the UI always launches
 * the system Photos / Photo Picker (NOT a file manager).
 *
 * The actual launcher is created in Compose with
 * `rememberLauncherForActivityResult(PickVisualMedia()) { uri -> ... }`
 * and launched with [imageOnlyRequest]. On devices without the modern platform
 * picker, the AndroidX activity library automatically falls back to the
 * bundled/Play-services photo picker — no code change and no file browser.
 */
object ImagePickerManager {

    /** A request restricting the picker to images only. */
    fun imageOnlyRequest(): PickVisualMediaRequest =
        PickVisualMediaRequest(PickVisualMedia.ImageOnly)
}
