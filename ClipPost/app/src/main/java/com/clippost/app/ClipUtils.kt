package com.clippost.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

/**
 * Keeps read access to a picked file after the app restarts.
 * Works for URIs from OpenDocument / OpenMultipleDocuments.
 */
fun Context.keepReadAccess(uri: Uri) {
    try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (e: SecurityException) {
        // Some providers don't allow persistent access. The clip still works until the app closes.
        Log.w("ClipPost", "Could not persist access to $uri", e)
    }
}

/** The file name shown in the gallery, e.g. "VID_2026.mp4". */
fun Context.displayName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0) cursor.getString(column)?.let { return it }
        }
    }
    return uri.lastPathSegment ?: "clip"
}
