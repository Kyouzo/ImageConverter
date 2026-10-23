package dev.kyouzo.imageconverter.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.kyouzo.imageconverter.format.FileNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves a converted file into the public Pictures/ImageConverter/ collection via
 * MediaStore so it appears in the Gallery. Never overwrites: a unique name is
 * generated on collision.
 *
 * API 29+ : scoped storage, no permission required (RELATIVE_PATH + IS_PENDING).
 * API 24-28: legacy insert; caller must hold WRITE_EXTERNAL_STORAGE.
 */
class ImageSaver(private val context: Context) {

    companion object {
        const val SUBDIR = "ImageConverter"
        val RELATIVE_DIR = "${Environment.DIRECTORY_PICTURES}/$SUBDIR"
    }

    /** @return the MediaStore content URI of the saved image. */
    suspend fun save(output: ConversionOutput): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(output)
        else saveLegacy(output)
    }

    private fun saveScoped(output: ConversionOutput): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val name = uniqueDisplayName(output.fileName)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, output.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw ConversionException("Could not create an entry in the gallery")
        try {
            resolver.openOutputStream(uri)?.use { os ->
                output.file.inputStream().use { it.copyTo(os) }
            } ?: throw ConversionException("Could not open the gallery entry for writing")
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            if (e is ConversionException) throw e
            throw ConversionException("Saving to the gallery failed: ${e.message}", e)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(output: ConversionOutput): Uri {
        val picturesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            SUBDIR
        )
        if (!picturesDir.exists()) picturesDir.mkdirs()
        val target = File(picturesDir, FileNaming.uniqueName(output.fileName) { File(picturesDir, it).exists() })
        output.file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, target.name)
            put(MediaStore.Images.Media.MIME_TYPE, output.mimeType)
            put(MediaStore.Images.Media.DATA, target.absolutePath)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: Uri.fromFile(target)
    }

    /** Find a display name not already present under Pictures/ImageConverter. */
    private fun uniqueDisplayName(desired: String): String =
        FileNaming.uniqueName(desired) { candidate -> existsInCollection(candidate) }

    private fun existsInCollection(displayName: String): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH}=? AND ${MediaStore.Images.Media.DISPLAY_NAME}=?"
        val args = arrayOf("$RELATIVE_DIR/", displayName)
        return try {
            resolver.query(collection, projection, selection, args, null)?.use { it.count > 0 } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
