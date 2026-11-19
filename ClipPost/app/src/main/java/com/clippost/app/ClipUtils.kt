package com.clippost.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import java.security.MessageDigest

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

/**
 * The SHA-1 of the key this installed APK is signed with, e.g. "09:07:01:...".
 * Google matches your Android OAuth client against this value and the package name.
 */
fun Context.signingSha1(): String = try {
    val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
        packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
    }
    val first = signatures?.firstOrNull()
    if (first == null) {
        "unknown"
    } else {
        MessageDigest.getInstance("SHA-1").digest(first.toByteArray())
            .joinToString(":") { "%02X".format(it) }
    }
} catch (e: Exception) {
    "unknown"
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
