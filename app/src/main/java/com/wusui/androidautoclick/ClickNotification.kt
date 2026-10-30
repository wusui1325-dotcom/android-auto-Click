package com.wusui.androidautoclick

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object ClickNotification {

    private const val CHANNEL_ID = "autoclick"
    private const val NOTIFY_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AutoClick",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "點擊執行狀態"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    fun showRunning(context: Context, text: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        try {
            ensureChannel(context)
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val n = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("AutoClick")
                .setContentText(text)
                .setOngoing(true)
                .build()
            mgr.notify(NOTIFY_ID, n)
        } catch (_: Exception) {}
    }

    fun cancel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.cancel(NOTIFY_ID)
    }
}
