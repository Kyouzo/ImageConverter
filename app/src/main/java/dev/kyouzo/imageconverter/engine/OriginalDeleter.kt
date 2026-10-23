package dev.kyouzo.imageconverter.engine

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Deletes the originally-selected image by resolving its real filesystem path
 * and calling [File.delete].
 *
 * This requires broad file access:
 *   - Android 11+ (R): "All files access" (MANAGE_EXTERNAL_STORAGE), which the
 *     user toggles in Settings. The UI ([ConverterViewModel]) gates on it before
 *     calling here.
 *   - Android 10 (Q): legacy external storage (requestLegacyExternalStorage) + WRITE.
 *   - API <= 28: WRITE_EXTERNAL_STORAGE.
 *
 * Path resolution tries MediaStore's DATA column first (via the picker URI's row
 * id, then by display name), then falls back to a filesystem search by name+size
 * so it can also remove files that are not indexed in MediaStore.
 *
 * On failure a [Result.CannotDelete] carries a diagnostic string.
 */
class OriginalDeleter(private val context: Context) {

    sealed interface Result {
        data object Deleted : Result
        data class CannotDelete(val reason: String) : Result
    }

    suspend fun attemptDelete(picked: Uri, displayName: String?, sizeBytes: Long): Result =
        withContext(Dispatchers.IO) {
            val diag = StringBuilder()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                diag.append("mgr=").append(Environment.isExternalStorageManager())
            }

            val path = resolvePath(picked, displayName, sizeBytes, diag)
                ?: return@withContext Result.CannotDelete("$diag no-path")

            val f = File(path)
            try {
                when {
                    !f.exists() -> Result.CannotDelete("$diag missing:$path")
                    f.delete() -> { rescan(path); Result.Deleted }
                    else -> Result.CannotDelete("$diag delete-false:$path")
                }
            } catch (e: Exception) {
                Result.CannotDelete("$diag ${e.javaClass.simpleName}")
            }
        }

    private fun resolvePath(picked: Uri, name: String?, size: Long, diag: StringBuilder): String? {
        val vol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.VOLUME_EXTERNAL_PRIMARY else "external"
        val images = MediaStore.Images.Media.getContentUri(vol)
        val files = MediaStore.Files.getContentUri(vol)

        // 1) Picker URIs usually end in the MediaStore row id -> query DATA by _ID.
        picked.lastPathSegment?.toLongOrNull()?.let { id ->
            dataFor(images, "${MediaStore.MediaColumns._ID}=?", arrayOf(id.toString()), diag, "idData")
                ?.let { return it }
        }

        // 2) By display name in Images, then Files.
        if (!name.isNullOrBlank()) {
            dataFor(images, "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(name), diag, "imgData")?.let { return it }
            dataFor(files, "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(name), diag, "fileData")?.let { return it }
        }

        // 3) Filesystem search by name + size (requires a known size to stay safe).
        if (!name.isNullOrBlank() && size > 0) {
            searchFileSystem(name, size, diag)?.let { return it }
        }

        return null
    }

    private fun dataFor(collection: Uri, selection: String, args: Array<String>, diag: StringBuilder, tag: String): String? =
        try {
            @Suppress("DEPRECATION")
            context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns.DATA), selection, args, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val i = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (i >= 0 && !c.isNull(i)) c.getString(i)
                        else { diag.append(" $tag=null"); null }
                    } else { diag.append(" $tag=0"); null }
                }
        } catch (e: Exception) {
            diag.append(" $tag=${e.javaClass.simpleName}")
            null
        }

    private fun searchFileSystem(name: String, size: Long, diag: StringBuilder): String? {
        val root = Environment.getExternalStorageDirectory() ?: return null
        return try {
            var scanned = 0
            val match = root.walkTopDown()
                .onEnter { it.name != "Android" } // skip app-private trees
                .firstOrNull { file ->
                    scanned++
                    file.isFile && file.name == name && file.length() == size
                }
            diag.append(" fs=").append(if (match != null) "hit" else "miss/$scanned")
            match?.absolutePath
        } catch (e: Exception) {
            diag.append(" fs=${e.javaClass.simpleName}")
            null
        }
    }

    /** Tell MediaStore the file is gone so its gallery entry disappears. */
    private fun rescan(path: String) {
        try { MediaScannerConnection.scanFile(context, arrayOf(path), null, null) } catch (_: Exception) {}
    }
}
