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
import android.provider.Settings
import com.ku.lostandfound.MainActivity
import com.ku.lostandfound.R

object FindersNotificationHelper {

    // Keep keys issued since the last session change, including posts the OS has not made active yet.
    private val issued = mutableMapOf<Pair<String?, Int>, PendingIntent>()

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(RemoteNotificationKind.CHAT.channelId)
        val splitChannels = RemoteNotificationKind.entries.filter { it != RemoteNotificationKind.CHAT }
        // Once registered, these channels have their own settings, even if either one is blocked.
        val alreadySplit = splitChannels.any { manager.getNotificationChannel(it.channelId) != null }
        val chat = RemoteNotificationKind.CHAT.channel()
        val split = alreadySplit || existing == null || existing.hasOriginalBehavior(chat)
        if (split) {
            manager.createNotificationChannels(
                listOf(chat) + splitChannels.map { it.channel() },
            )
        } else {
            // Preserve the shared channel on upgrades where the user configured its behavior.
            manager.createNotificationChannel(RemoteNotificationKind.CHAT.channel(shared = true))
        }
    }

    private fun NotificationChannel.hasOriginalBehavior(original: NotificationChannel): Boolean =
        importance == original.importance && sound == original.sound &&
            shouldVibrate() == original.shouldVibrate() && vibrationPattern == null &&
            shouldShowLights() == original.shouldShowLights() &&
            canShowBadge() == original.canShowBadge() && !canBypassDnd() &&
            lockscreenVisibility == original.lockscreenVisibility &&
            (Build.VERSION.SDK_INT < 29 || !hasUserSetImportance()) &&
            (Build.VERSION.SDK_INT < 30 || !hasUserSetSound())

    private fun deliveryChannel(manager: NotificationManager, kind: RemoteNotificationKind): String =
        if (manager.getNotificationChannel(kind.channelId) != null) kind.channelId
        else RemoteNotificationKind.CHAT.channelId

    internal fun systemSettingsIntent(context: Context, kind: RemoteNotificationKind): Intent {
        createChannel(context)
        return notificationChannelSettings(
            context, deliveryChannel(context.getSystemService(NotificationManager::class.java), kind),
        )
    }

    @Synchronized
    internal fun cancelRemoteNotifications(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        try {
            issued.forEach { (key, intent) ->
                intent.cancel()
                manager.cancel(key.first, key.second)
            }
            manager.activeNotifications.filter { notification ->
                RemoteNotificationKind.entries.any { it.channelId == notification.notification.channelId }
            }.forEach {
                it.notification.contentIntent?.cancel()
                manager.cancel(it.tag, it.id)
            }
        } catch (_: RuntimeException) {
            android.util.Log.w("FindersFCM", "원격 알림 정리 실패")
        } finally {
            issued.clear()
        }
    }

    @Synchronized
    internal fun showNotification(
        context: Context,
        title: String,
        body: String,
        destination: PendingNotification?,
        kind: RemoteNotificationKind,
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
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = deliveryChannel(manager, kind)
        if (!manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
        ) return

        val chat = destination?.target as? NotificationTarget.Chat
        val notificationTag = chat?.let { "CHAT_${it.roomId}" }

        val intent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

            if (chat != null) {
                chat.putInto(this)
                putExtra(PendingNotification.OWNER_EXTRA, destination.owner)
                putExtra(PendingNotification.DELIVERY_EXTRA, destination.delivery)
                destination.recipientUserId?.let { putExtra(PendingNotification.RECIPIENT_EXTRA, it) }
                action = "com.ku.lostandfound.NOTIFICATION.${destination.owner}.$notificationTag"
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
            channelId
        )
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                Notification.BigTextStyle().bigText(body)
            )
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setCategory(kind.category)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val id = if (notificationTag != null) 0 else System.currentTimeMillis().toInt()
        issued[notificationTag to id] = pendingIntent
        manager.notify(notificationTag, id, notification)
    }
}

internal enum class RemoteNotificationKind(
    val channelId: String,
    val title: String,
    private val importance: Int,
    val category: String,
) {
    CHAT("finders_messages_high", "채팅 알림", NotificationManager.IMPORTANCE_HIGH, Notification.CATEGORY_MESSAGE),
    COMMENT("finders_comments", "댓글 알림", NotificationManager.IMPORTANCE_DEFAULT, Notification.CATEGORY_SOCIAL),
    MATCH("finders_matches", "유사 게시글 알림", NotificationManager.IMPORTANCE_HIGH, Notification.CATEGORY_RECOMMENDATION);

    fun channel(shared: Boolean = false) = NotificationChannel(
        channelId, if (shared) "찾을건대 알림" else title, importance,
    ).apply {
        description = if (shared) "채팅, 댓글 및 유사 게시글 알림" else title
        enableVibration(true)
    }

    companion object {
        fun fromPayload(type: String?): RemoteNotificationKind = when (type?.uppercase()) {
            "CHAT" -> CHAT
            "MATCH" -> MATCH
            else -> COMMENT // Legacy POST uses the same general notification as COMMENT.
        }
    }
}

internal fun notificationChannelSettings(context: Context, channelId: String) =
    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
