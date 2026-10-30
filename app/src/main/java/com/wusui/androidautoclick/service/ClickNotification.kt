package com.wusui.androidautoclick.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wusui.androidautoclick.R
import com.wusui.androidautoclick.i18n.LocaleHelper
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

    private fun s(context: Context, id: Int): String = LocaleHelper.str(context, id)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                s(context, R.string.notif_channel_status),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = s(context, R.string.notif_channel_status_desc)
                setShowBadge(false)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OVERLAY,
                s(context, R.string.notif_channel_overlay),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = s(context, R.string.notif_channel_overlay_desc)
                setShowBadge(false)
            }
        )
    }

    fun buildOverlayKeepAlive(context: Context, text: String? = null): Notification {
        ensureChannel(context)
        val body = text ?: s(context, R.string.notif_overlay_running)
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
            .setContentTitle(s(context, R.string.app_name))
            .setContentText(body)
            .setContentIntent(launch)
            .addAction(0, s(context, R.string.action_stop), stop)
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
                .setContentTitle(s(context, R.string.notif_running_title))
                .setContentText(text)
                .setContentIntent(launch)
                .addAction(0, s(context, R.string.action_stop), stop)
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
