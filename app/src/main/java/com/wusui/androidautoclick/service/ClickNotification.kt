package com.wusui.androidautoclick.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wusui.androidautoclick.ui.MainActivity

/**
 * FGS notifications must be PRIORITY_LOW or higher (platform enforces).
 * Channels use IMPORTANCE_LOW to stay quiet while meeting the floor.
 */
object ClickNotification {

    const val CHANNEL_ID = "autoclick"
    const val CHANNEL_OVERLAY = "overlay_keepalive"
    const val NOTIFY_ID = 1001
    const val OVERLAY_NOTIFY_ID = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "執行狀態", NotificationManager.IMPORTANCE_LOW).apply {
                description = "自動點擊執行狀態"
                setShowBadge(false)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_OVERLAY, "懸浮控制", NotificationManager.IMPORTANCE_LOW).apply {
                description = "懸浮窗保活通知"
                setShowBadge(false)
            }
        )
    }

    fun buildOverlayKeepAlive(context: Context, text: String = "懸浮控制運行中"): Notification {
        ensureChannel(context)
        val launch = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, EngineActionReceiver::class.java).setAction(EngineActionReceiver.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_OVERLAY)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("AutoClick")
            .setContentText(text)
            .setContentIntent(launch)
            .addAction(0, "停止", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun showRunning(context: Context, text: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return
        }
        try {
            ensureChannel(context)
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val launch = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val stop = PendingIntent.getBroadcast(
                context, 3,
                Intent(context, EngineActionReceiver::class.java).setAction(EngineActionReceiver.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("AutoClick 執行中")
                .setContentText(text)
                .setContentIntent(launch)
                .addAction(0, "停止", stop)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
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
