package dev.kyouzo.imageconverter.engine

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deletes the originally-selected image after a successful save.
 *
 * Deletion of media the app does not own requires the user's consent. On
 * Android 11+ (R) we use [MediaStore.createDeleteRequest], which shows a system
 * dialog and performs the delete on approval WITHOUT any broad storage
 * permission — matching the privacy-preserving Photo Picker selection flow. On
 * Android 10 (Q) a delete attempt raises [RecoverableSecurityException] whose
 * consent intent we surface. On API <= 28 the legacy WRITE_EXTERNAL_STORAGE
 * permission (declared maxSdk=28) allows a direct delete.
 *
 * Nothing is ever deleted silently on Android 11+, and if the specific image
 * cannot be located/queued the caller reports it instead of failing.
 */
class OriginalDeleter(private val context: Context) {

    sealed interface Result {
        /** Deletion already completed (API <= 28 direct delete). */
        data object Deleted : Result

        /**
         * The UI must launch [request]. If [secondStepDelete] is non-null (Android 10),
         * the caller must delete that URI again after consent is granted; on Android 11+
         * the system performs the delete itself and this is null.
         */
        data class NeedsConsent(val request: IntentSenderRequest, val secondStepDelete: Uri?) : Result

        /** The image could not be identified/queued for deletion (not fatal). */
        data object NotPossible : Result

        data class Failed(val reason: String) : Result
    }

    suspend fun attemptDelete(picked: Uri, displayName: String?, sizeBytes: Long): Result =
        withContext(Dispatchers.IO) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val uri = resolveDeletableUri(displayName, sizeBytes) ?: picked
                    try {
                        val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                        Result.NeedsConsent(IntentSenderRequest.Builder(pi.intentSender).build(), null)
                    } catch (_: Exception) {
                        Result.NotPossible
                    }
                }

                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                    val uri = resolveDeletableUri(displayName, sizeBytes) ?: return@withContext Result.NotPossible
                    try {
                        val deleted = context.contentResolver.delete(uri, null, null)
                        if (deleted > 0) Result.Deleted else Result.NotPossible
                    } catch (e: RecoverableSecurityException) {
                        val sender = e.userAction.actionIntent.intentSender
                        Result.NeedsConsent(IntentSenderRequest.Builder(sender).build(), uri)
                    } catch (e: Exception) {
                        Result.Failed(e.message ?: "The original could not be deleted")
                    }
                }

                else -> { // API 24..28, legacy WRITE_EXTERNAL_STORAGE
                    val uri = resolveDeletableUri(displayName, sizeBytes) ?: picked
                    try {
                        if (context.contentResolver.delete(uri, null, null) > 0) Result.Deleted
                        else Result.NotPossible
                    } catch (e: Exception) {
                        Result.Failed(e.message ?: "The original could not be deleted")
                    }
                }
            }
        }

    /** Deletes [uri] directly (used as the second step after consent on Android 10). */
    suspend fun directDelete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Find the shared-store image URI for the picked item by matching display
     * name (and size when known). Returns null when there is no single
     * unambiguous match, so we never queue the wrong photo for deletion.
     */
    private fun resolveDeletableUri(displayName: String?, sizeBytes: Long): Uri? {
        if (displayName.isNullOrBlank()) return null
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val useSize = sizeBytes > 0
        val selection = if (useSize) {
            "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.SIZE}=?"
        } else {
            "${MediaStore.Images.Media.DISPLAY_NAME}=?"
        }
        val args = if (useSize) arrayOf(displayName, sizeBytes.toString()) else arrayOf(displayName)
        return try {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                selection, args, null,
            )?.use { c ->
                if (c.count != 1) return null
                c.moveToFirst()
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                ContentUris.withAppendedId(collection, id)
            }
        } catch (_: Exception) {
            null
        }
    }
}
