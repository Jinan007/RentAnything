package com.clippost.app.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import java.util.UUID

/** All notification text and channels for uploads, in one place. */
object Notifications {

    private const val CHANNEL_ID = "clippost_uploads"

    /** The notification shown while a worker is running (required for background work). */
    fun uploadingInfo(context: Context, workId: UUID, title: String, percent: Int): ForegroundInfo {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Uploading to YouTube: $percent%")
            .setContentText(title)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return ForegroundInfo(
            workId.hashCode(),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    fun showDone(context: Context, workId: UUID, title: String, videoId: String, warning: String? = null) {
        val url = "https://youtu.be/$videoId"
        val open = PendingIntent.getActivity(
            context,
            workId.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(if (warning == null) "Uploaded to YouTube ✓" else "Uploaded - needs your attention")
            .setContentText(warning ?: "$title - tap to open")
            .setStyle(NotificationCompat.BigTextStyle().bigText(warning ?: "$title - tap to open"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        post(context, workId.hashCode() + 1, notification)
    }

    fun showFailed(context: Context, workId: UUID, title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Upload failed: $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        post(context, workId.hashCode() + 1, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Uploads", NotificationManager.IMPORTANCE_LOW)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Posts a notification, but only if the user has allowed notifications (Android 13+). */
    private fun post(context: Context, id: Int, notification: Notification) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
