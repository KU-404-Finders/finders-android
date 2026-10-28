package com.ku.lostandfound.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.ku.lostandfound.MainActivity
import com.ku.lostandfound.R

object FindersNotificationHelper {

    private const val CHANNEL_ID = "finders_messages_high"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(
            NotificationManager::class.java
        )

        val channel = NotificationChannel(
            CHANNEL_ID,
            "찾을건대 채팅 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "새 채팅 메시지 및 중요한 분실물 알림"
            enableVibration(true)
        }

        manager.createNotificationChannel(channel)
    }

    fun showNotification(
        context: Context,
        title: String,
        body: String,
        target: NotificationTarget? = null,
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createChannel(context)

        val intent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

            target?.putInto(this)
        }

// 서로 다른 알림이 동일한 PendingIntent를 공유하지 않도록 구분
        val requestCode = when (target) {
            is NotificationTarget.Chat ->
                "CHAT_${target.roomId}".hashCode()

            is NotificationTarget.Post ->
                "POST_${target.postType}_${target.postId}".hashCode()

            null -> 0
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                Notification.BigTextStyle().bigText(body)
            )
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(
            NotificationManager::class.java
        )

        manager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
}