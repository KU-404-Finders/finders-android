package com.ku.lostandfound.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ku.lostandfound.network.TokenManager

class FindersFirebaseMessagingService : FirebaseMessagingService() {

    override fun onRegistered(installationId: String) {
        super.onRegistered(installationId)

        PushDeviceManager.onRegistered(
            context = applicationContext,
            installationId = installationId,
        )
    }

    override fun onMessageReceived(
        remoteMessage: RemoteMessage
    ) {
        super.onMessageReceived(remoteMessage)

        handleRemotePush(applicationContext, remoteMessage.data)
    }
}

internal fun handleRemotePush(context: android.content.Context, data: Map<String, String>) {
    TokenManager.init(context)
    val session = TokenManager.reminderSession()
    publishRemotePush(session, data) { title, body, destination ->
        NotificationInboxRefreshBus.notifyArrival()
        FindersNotificationHelper.showNotification(
            context, title, body, destination, RemoteNotificationKind.fromPayload(data["type"]),
        )
    }
}

internal fun publishRemotePush(
    session: String?,
    data: Map<String, String>,
    publish: (title: String, body: String, destination: PendingNotification?) -> Unit,
) {
    if (session == null) return
    val target = NotificationTarget.fromData(data) ?: return
    val recipient = if (target is NotificationTarget.Chat) {
        data["recipientUserId"]?.takeIf { it.all { digit -> digit in '0'..'9' } }
            ?.toLongOrNull()?.takeIf { it > 0 } ?: return
    } else null

    // The credential writers share this monitor with every publication side effect.
    TokenManager.withReminderSession(session) {
        if (target is NotificationTarget.Chat) {
            if (recipient != TokenManager.tokenUserId(TokenManager.accessToken)) return@withReminderSession
            publish(
                data["title"]?.takeIf(String::isNotBlank) ?: "새 채팅 메시지",
                data["body"]?.takeIf(String::isNotBlank) ?: "앱에서 채팅을 확인해 주세요.",
                PendingNotification(target, session, java.util.UUID.randomUUID().toString(), recipient),
            )
        } else {
            publish("찾을건대", "앱에서 알림을 확인해 주세요.", null)
        }
    }
}
