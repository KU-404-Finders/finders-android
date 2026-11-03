package com.ku.lostandfound.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTargetTest {

    @Test
    fun chatPayloadNavigatesToRoom() {
        assertEquals(
            NotificationTarget.Chat(2L),
            NotificationTarget.fromData(mapOf("type" to "CHAT", "roomId" to "2"))
        )
    }

    @Test
    fun postAndCommentPayloadsNavigateToPost() {
        val expected = NotificationTarget.Post(3L, "FOUND")
        for (type in listOf("POST", "COMMENT", "MATCH")) {
            assertEquals(
                expected,
                NotificationTarget.fromData(
                    mapOf("type" to type, "postType" to "FOUND", "postId" to "3")
                )
            )
        }
    }

    @Test
    fun invalidPayloadDoesNotOpenAnotherUsersScreen() {
        assertNull(NotificationTarget.fromData(mapOf("type" to "CHAT", "roomId" to "-1")))
        assertNull(NotificationTarget.fromData(mapOf("type" to "COMMENT", "postId" to "2")))
        assertNull(NotificationTarget.fromData(mapOf("type" to "MATCH", "postType" to "OTHER", "postId" to "2")))
    }
}
