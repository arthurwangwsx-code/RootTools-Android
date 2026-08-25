package com.arthur.roottools.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import com.arthur.roottools.app.rootToolsContainer
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Read-only ADB/root bridge for the current shadow-display preview.
 *
 * The provider intentionally exposes no input or lifecycle actions. Android's DUMP permission
 * filters callers first, and the UID policy below additionally restricts reads to shell/root/the
 * app itself. Each read requests a fresh preview through the typed ShadowDisplayController.
 */
class ShadowPreviewProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? =
        if (uri.path == LATEST_PATH) MIME_JPEG else null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val appContext = context?.applicationContext ?: throw FileNotFoundException("Provider context unavailable")
        val callerUid = Binder.getCallingUid()
        if (!ShadowPreviewAccessPolicy.canRead(callerUid, appContext.applicationInfo.uid, uri.path, mode)) {
            throw SecurityException("Shadow preview access denied")
        }

        val bytes = synchronized(PREVIEW_LOCK) {
            runBlocking(Dispatchers.IO) {
                appContext.rootToolsContainer
                    .createShadowDisplayController(AUDIT_SOURCE)
                    .capturePreview()
                    .getOrElse { error ->
                        throw FileNotFoundException(error.message ?: "Shadow preview unavailable")
                    }
            }
        }

        val previewDir = File(appContext.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
        val preview = File(previewDir, CACHE_FILE)
        val temporary = File(previewDir, "$CACHE_FILE.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(preview)) {
            temporary.copyTo(preview, overwrite = true)
            temporary.delete()
        }
        return ParcelFileDescriptor.open(preview, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Shadow preview is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Shadow preview is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Shadow preview is read-only")

    private companion object {
        const val LATEST_PATH = "/latest"
        const val MIME_JPEG = "image/jpeg"
        const val AUDIT_SOURCE = "ADB Preview"
        const val CACHE_DIRECTORY = "shadow-preview"
        const val CACHE_FILE = "latest.jpg"
        val PREVIEW_LOCK = Any()
    }
}

internal object ShadowPreviewAccessPolicy {
    private const val ROOT_UID = 0
    private const val SHELL_UID = 2000

    fun canRead(callerUid: Int, appUid: Int, path: String?, mode: String): Boolean =
        path == "/latest" &&
            mode == "r" &&
            (callerUid == ROOT_UID || callerUid == SHELL_UID || callerUid == appUid)
}
