package com.wusui.androidautoclick

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object ClickNotification {

    const val CHANNEL_ID = "autoclick"
    const val CHANNEL_OVERLAY = "overlay_keepalive"
    const val NOTIFY_ID = 1001
    const val OVERLAY_NOTIFY_ID = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "執行狀態",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "自動點擊執行狀態"
                setShowBadge(false)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OVERLAY,
                "懸浮控制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "懸浮窗保活通知"
                setShowBadge(false)
            }
        )
    }

    fun buildOverlayKeepAlive(context: Context, text: String = "懸浮控制運行中"): Notification {
        ensureChannel(context)
        val launch = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("AutoClick")
            .setContentText(text)
            .setContentIntent(launch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun showRunning(context: Context, text: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
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
                .setOnlyAlertOnce(true)
                .build()
            mgr.notify(NOTIFY_ID, n)
        } catch (_: Exception) {
        }
    }

    fun cancel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.cancel(NOTIFY_ID)
    }
}
