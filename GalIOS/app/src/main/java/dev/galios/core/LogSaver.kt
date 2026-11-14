package dev.galios.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a log to Downloads/GalIOS as a .txt file, through MediaStore (no storage permission on Android 12+), so it
 * can be sent from the Files app or any chat app without going through a share sheet that cannot carry megabytes.
 */
object LogSaver {
    const val FOLDER = "GalIOS"

    data class Saved(val path: String, val uri: Uri, val bytes: Long)

    /** The file name for log [id] written at [time]: GalIOS-session-20261114-1525.txt. */
    fun fileName(id: String, time: Date = Date()): String =
        "GalIOS-${id.replace(Regex("[^A-Za-z0-9._-]"), "_")}-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(time)}.txt"

    fun save(context: Context, id: String, text: String, time: Date = Date()): Saved {
        val name = fileName(id, time)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Android did not make a new file in Downloads")
        val bytes = text.toByteArray()
        try {
            (resolver.openOutputStream(uri) ?: throw IOException("could not open the new file")).use { it.write(bytes) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return Saved("Download/$FOLDER/$name", uri, bytes.size.toLong())
    }
}
