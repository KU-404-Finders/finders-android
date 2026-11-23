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
