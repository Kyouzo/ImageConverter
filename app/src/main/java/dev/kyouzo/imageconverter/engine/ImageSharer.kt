package dev.kyouzo.imageconverter.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Shares a converted image via the Android Sharesheet using a FileProvider URI. */
class ImageSharer(private val context: Context) {

    private val authority: String get() = "${context.packageName}.fileprovider"

    /** Build a share chooser Intent granting temporary read access to [output]. */
    fun shareIntent(output: ConversionOutput): Intent {
        val shareDir = File(context.cacheDir, "shared").apply { if (!exists()) mkdirs() }
        val shared = File(shareDir, output.fileName)
        if (output.file.absolutePath != shared.absolutePath) {
            output.file.inputStream().use { input -> shared.outputStream().use { input.copyTo(it) } }
        }
        val uri: Uri = FileProvider.getUriForFile(context, authority, shared)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = output.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share image").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
