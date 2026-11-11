package com.autoposter.whatsapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.autoposter.whatsapp.ui.MainActivity

class ScheduleAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_SCHEDULE = "com.autoposter.whatsapp.TRIGGER_SCHEDULE"
        const val CHANNEL_ID = "autorelay_scheduled_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoRelay:ScheduleAlarmWakeLock"
        )
        wakeLock?.acquire(30_000L) // 30 seconds max

        try {
            createNotificationChannel(context)

            // Intent to open MainActivity and auto-start broadcast
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_EXECUTE_SCHEDULED_BROADCAST
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val scheduledCat = AutomationManager.scheduledTargetCategory.value
            val targetCount = AutomationManager.getActiveQueue().size

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("⏰ AutoRelay Scheduled Broadcast")
                .setContentText("Timer reached! Ready to send to $targetCount chats ($scheduledCat). Tap to launch.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, notification)

            // Mark schedule as triggered
            AutomationManager.onScheduleTriggered(context)

            // Attempt to launch activity directly if permitted
            try {
                context.startActivity(launchIntent)
            } catch (_: Exception) {}

        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AutoRelay Broadcast Schedule"
            val descriptionText = "Notifications for scheduled automated WhatsApp broadcasts"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }
}
