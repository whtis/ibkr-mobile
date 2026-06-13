package com.tis.ibkr.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tis.ibkr.MainActivity

/** Local notification plumbing for FCM messages (e.g. the gateway-2FA reminder). */
object PushNotifications {
    const val CHANNEL_ID = "ibkr_alerts"
    private const val NOTIF_ID = 1001

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "网关与交易提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "IBKR 网关验证、交易相关推送" }
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun show(ctx: Context, title: String, body: String) {
        ensureChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        // notify() is a no-op if POST_NOTIFICATIONS isn't granted (Android 13+).
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notification) }
    }
}
