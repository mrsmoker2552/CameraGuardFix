package com.boss.cameraguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.boss.cameraguard.data.SosAlert

object SosNotifier {
    private const val CHANNEL_ID = "camera_guard_sos"
    private const val CHANNEL_NAME = "Rider SOS alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent SOS alerts from online CameraGuard riders"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun post(context: Context, alert: SosAlert) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_SOS_UID, alert.uid)
            putExtra(MainActivity.EXTRA_SOS_NAME, alert.displayName)
            putExtra(MainActivity.EXTRA_SOS_LAT, alert.latitude)
            putExtra(MainActivity.EXTRA_SOS_LON, alert.longitude)
            putExtra(MainActivity.EXTRA_SOS_CREATED_AT, alert.createdAtMillis)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.uid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") android.app.Notification.Builder(context)
        }
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SOS · ${alert.displayName}")
            .setContentText("Rider needs help. Tap to open their current location.")
            .setStyle(android.app.Notification.BigTextStyle().bigText(
                "${alert.displayName} pressed SOS. Tap to focus the map on their current location."
            ))
            .setPriority(android.app.Notification.PRIORITY_MAX)
            .setCategory(android.app.Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify((alert.uid + alert.createdAtMillis).hashCode(), notification)
    }
}
