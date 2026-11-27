package com.ku.lostandfound

import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.notification.NotificationTarget
import com.ku.lostandfound.notification.publishRemotePush
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class UnverifiedPushRegressionTest : StabilityFixture() {
    private val legacyChat = mapOf("type" to "CHAT", "roomId" to "7", "title" to "private title", "body" to "private text")
    private val comment = mapOf("type" to "COMMENT", "postId" to "5", "postType" to "FOUND",
        "title" to "private title", "body" to "private text")

    private fun loginAs(userId: Long) {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"user_id":$userId}""".toByteArray())
        login("header.$payload.signature")
    }

    @Test fun unverifiedPushCanOnlyPublishFixedAccountNeutralCopy() {
        val published = mutableListOf<Pair<String, String>>()
        for (data in listOf(comment, comment + ("type" to "MATCH"),
            comment + ("recipientUserId" to "99"))) {
            publishRemotePush(TokenManager.reminderSession(), data) { title, body, destination ->
                assertNull(destination)
                published += title to body
            }
        }
        assertEquals(List(3) { "찾을건대" to "앱에서 알림을 확인해 주세요." }, published)
    }

    @Test fun logoutAndAccountChangeBeforePublishingDiscardTheOldSignal() {
        val session = TokenManager.reminderSession()
        TokenManager.clear()
        publishRemotePush(TokenManager.reminderSession(), comment) { _, _, _ -> fail("Signed-out notification") }
        publishRemotePush(session, comment) { _, _, _ -> fail("Previous session notification") }
        login("account-b")
        publishRemotePush(session, comment) { _, _, _ -> fail("Previous session notification under account B") }
    }

    @Test fun chatRejectsMissingMalformedMismatchedAndUnresolvedRecipientsBeforePublication() {
        val matching = legacyChat + ("recipientUserId" to "42")
        publishRemotePush(TokenManager.reminderSession(), matching) { _, _, _ -> fail("Unknown authenticated ID") }
        TokenManager.clear()
        publishRemotePush(TokenManager.reminderSession(), matching) { _, _, _ -> fail("Signed out") }
        loginAs(42)
        val rejected = listOf(legacyChat, matching + ("roomId" to "0")) +
            listOf("", "abc", " 42", "+42", "0", "-42", "9223372036854775808", "43")
                .map { legacyChat + ("recipientUserId" to it) }
        for (data in rejected) {
            publishRemotePush(TokenManager.reminderSession(), data) { _, _, _ -> fail("Unexpected publication") }
        }
    }

    @Test fun delayedChatUsesCurrentRecipientAndPreviouslyCapturedSession() {
        loginAs(42)
        val sessionA = TokenManager.reminderSession()
        val forA = legacyChat + ("recipientUserId" to "42")
        val forB = legacyChat + ("recipientUserId" to "43")
        TokenManager.clear()
        loginAs(43)
        val sessionB = TokenManager.reminderSession()
        publishRemotePush(sessionB, forA) { _, _, _ -> fail("Delayed push for A") }
        publishRemotePush(sessionA, forB) { _, _, _ -> fail("Work captured before account switch") }
        var accepted = false
        publishRemotePush(sessionB, forB) { title, body, destination ->
            assertEquals("private title", title)
            assertEquals("private text", body)
            assertEquals(NotificationTarget.Chat(7), destination?.target)
            assertEquals(43L, destination?.recipientUserId)
            assertEquals(sessionB, destination?.owner)
            assertNull(destination?.forSession(sessionA, 42))
            assertNull(destination?.forSession(sessionB, 42))
            accepted = true
        }
        assertTrue(accepted)
    }

    @Test fun persistedCredentialsAndOwnerCanBeReopenedWithoutReusingAnEarlierLogin() {
        loginAs(42)
        val owner = TokenManager.reminderSession()
        TokenManager.init(application)
        assertEquals(owner, TokenManager.reminderSession())
        val data = legacyChat + ("recipientUserId" to "42")
        var accepted = false
        publishRemotePush(owner, data) { _, _, destination ->
            assertEquals(destination, destination?.forSession(owner, 42))
            accepted = true
        }
        assertTrue(accepted)
        loginAs(42)
        publishRemotePush(owner, data) { _, _, _ -> fail("Previous login of same account") }
    }
}
