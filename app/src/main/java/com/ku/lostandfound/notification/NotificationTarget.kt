package com.ku.lostandfound.notification

import android.content.Intent

/** Used only to discard a queued destination after an account switch, never for authorization. */
internal fun notificationAccountKey(token: String?, generation: Long): String? {
    if (token.isNullOrBlank()) return null
    return runCatching {
        val payload = String(java.util.Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8)
        val subject = com.google.gson.JsonParser.parseString(payload).asJsonObject.get("sub")
        subject?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?.takeIf { it.isNotBlank() }?.let { "subject:$it" }
    }.getOrNull() ?: "session:$generation"
}

internal data class PendingNotification(
    val target: NotificationTarget,
    val owner: String?,
    val delivery: String?,
    val recipientUserId: Long? = null,
) {
    fun forSession(session: String?, userId: Long? = null): PendingNotification? = when {
        target is NotificationTarget.Chat -> takeIf {
            owner != null && owner == session && recipientUserId != null && recipientUserId > 0 &&
                recipientUserId == userId && !delivery.isNullOrBlank()
        }
        owner != null && owner != session -> null
        owner == null && session != null -> copy(owner = session)
        else -> this
    }

    companion object {
        const val OWNER_EXTRA = "notificationSession"
        const val DELIVERY_EXTRA = "notificationDelivery"
        const val RECIPIENT_EXTRA = "notificationRecipient"

        fun updatedFrom(
            current: PendingNotification?,
            intent: Intent?,
            session: String?,
            consumed: Set<String>,
            userId: Long? = null,
        ): PendingNotification? {
            val target = NotificationTarget.fromIntent(intent) ?: return current
            val owner = intent?.getStringExtra(OWNER_EXTRA)
            val delivery = intent?.getStringExtra(DELIVERY_EXTRA) ?: intent?.getStringExtra("google.message_id")
            if ((owner != null && owner != session) || delivery in consumed) return current
            val recipient = intent?.getLongExtra(RECIPIENT_EXTRA, 0)?.takeIf { it > 0 }
            return PendingNotification(target, owner, delivery, recipient).forSession(session, userId) ?: current
        }
    }
}

sealed interface NotificationTarget {

    data class Chat(
        val roomId: Long
    ) : NotificationTarget

    data class Post(
        val postId: Long,
        val postType: String
    ) : NotificationTarget

    fun putInto(intent: Intent) {
        when (this) {
            is Chat -> {
                intent.putExtra("type", "CHAT")
                intent.putExtra("roomId", roomId.toString())
            }

            is Post -> {
                intent.putExtra("type", "POST")
                intent.putExtra("postId", postId.toString())
                intent.putExtra("postType", postType)
            }
        }
    }

    companion object {

        fun fromData(
            data: Map<String, String>
        ): NotificationTarget? {
            return parse(
                type = data["type"],
                roomId = data["roomId"],
                postId = data["postId"],
                postType = data["postType"]
            )
        }

        fun fromIntent(
            intent: Intent?
        ): NotificationTarget? {
            return parse(
                type = intent?.getStringExtra("type"),
                roomId = intent?.getStringExtra("roomId"),
                postId = intent?.getStringExtra("postId"),
                postType = intent?.getStringExtra("postType")
            )
        }

        private fun parse(
            type: String?,
            roomId: String?,
            postId: String?,
            postType: String?
        ): NotificationTarget? {

            return when (type?.uppercase()) {

                "CHAT" -> {
                    val id = roomId?.toLongOrNull()
                        ?.takeIf { it > 0L }

                    id?.let { Chat(it) }
                }

                "POST", "COMMENT", "MATCH" -> {
                    val id = postId?.toLongOrNull()
                        ?.takeIf { it > 0L }

                    val validType = postType
                        ?.uppercase()
                        ?.takeIf {
                            it == "LOST" || it == "FOUND"
                        }

                    if (id != null && validType != null) {
                        Post(id, validType)
                    } else {
                        null
                    }
                }

                else -> null
            }
        }
    }
}
