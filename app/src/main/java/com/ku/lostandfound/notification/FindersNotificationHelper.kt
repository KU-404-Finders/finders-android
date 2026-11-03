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
            "찾을건대 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "채팅, 댓글 및 유사 게시글 알림"
            enableVibration(true)
        }

        manager.createNotificationChannel(channel)
    }

    fun showNotification(
        context: Context,
        title: String,
        body: String,
        target: NotificationTarget? = null,
        notificationType: String? = null,
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

        // 채팅은 방별로, 댓글과 매칭은 서로 섞이지 않도록 종류/게시글별로 갱신한다.
        val postKind = when (notificationType?.uppercase()) {
            "COMMENT" -> "COMMENT"
            "MATCH" -> "MATCH"
            else -> "POST"
        }
        val notificationTag = when (target) {
            is NotificationTarget.Chat -> "CHAT_${target.roomId}"
            is NotificationTarget.Post -> "${postKind}_${target.postType}_${target.postId}"
            null -> null
        }

        val intent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

            target?.putInto(this)
            if (notificationTag != null) {
                action = "com.ku.lostandfound.NOTIFICATION.$notificationTag"
            }
        }

        // Android가 서로 다른 목적지의 PendingIntent를 재사용하지 않도록 구분한다.
        val requestCode = notificationTag?.hashCode() ?: 0

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

        if (notificationTag != null) {
            // 동일한 tag와 id로 호출하면 기존 알림을 갱신한다.
            manager.notify(notificationTag, 0, notification)
        } else {
            // 이동 대상이 없는 일반 알림은 서로 독립적으로 표시한다.
            manager.notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}