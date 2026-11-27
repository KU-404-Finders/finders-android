package com.ku.lostandfound.ui.navigation

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ku.lostandfound.notification.NotificationTarget
import com.ku.lostandfound.notification.PendingNotification
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDeliveryTest {
    @Test fun launcherIntentPreservesQueuedPostThroughAuthAndLogoutDiscardsIt() {
        val post = Intent().apply { NotificationTarget.Post(12, "FOUND").putInto(this) }
        val waiting = PendingNotification.updatedFrom(null, post, null, emptySet())!!
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        assertEquals(waiting, PendingNotification.updatedFrom(waiting, launcher, null, emptySet()))
        val bound = waiting.forSession("account-a")!!
        assertEquals(NotificationTarget.Post(12, "FOUND"), bound.target)
        assertNull(bound.forSession(null))
        assertNull(bound.forSession("account-b"))
    }

    @Test fun consecutiveTapsUseLatestValidMatchAndIgnoreConsumedOrForeignDeliveries() {
        val room = Intent().apply { NotificationTarget.Chat(3).putInto(this) }
            .putExtra(PendingNotification.OWNER_EXTRA, "account-a")
            .putExtra(PendingNotification.RECIPIENT_EXTRA, 42L)
            .putExtra(PendingNotification.DELIVERY_EXTRA, "chat-delivery")
        val pendingChat = PendingNotification.updatedFrom(null, room, "account-a", emptySet(), 42)
        assertNotNull(pendingChat)
        val match = Intent().putExtra("type", "MATCH").putExtra("postType", "LOST").putExtra("postId", "4")
            .putExtra(PendingNotification.OWNER_EXTRA, "account-a")
            .putExtra(PendingNotification.DELIVERY_EXTRA, "delivery")
        val pendingMatch = PendingNotification.updatedFrom(pendingChat, match, "account-a", emptySet())!!
        assertEquals(NotificationTarget.Post(4, "LOST"), pendingMatch.target)
        assertEquals(pendingChat, PendingNotification.updatedFrom(pendingChat, match, "account-b", emptySet()))
        assertNull(PendingNotification.updatedFrom(null, match, "account-a", setOf("delivery")))
        val invalid = Intent(match).putExtra("postId", "-1")
        assertEquals(pendingMatch, PendingNotification.updatedFrom(pendingMatch, invalid, "account-a", emptySet()))
    }

    @Test fun chatTapRequiresRecipientAndOriginalLoginIncludingAfterRestore() {
        val chat = Intent().apply { NotificationTarget.Chat(12).putInto(this) }
        assertNull(PendingNotification.updatedFrom(null, chat, "login-a", emptySet(), 42))
        chat.putExtra(PendingNotification.OWNER_EXTRA, "login-a")
            .putExtra(PendingNotification.RECIPIENT_EXTRA, 42L)
            .putExtra(PendingNotification.DELIVERY_EXTRA, "saved-chat")
        assertNull(PendingNotification.updatedFrom(null, chat, "login-b", emptySet(), 43))
        assertNull(PendingNotification.updatedFrom(null, chat, null, emptySet()))
        assertNull(PendingNotification.updatedFrom(null, chat, "login-a", emptySet(), 43))
        val accepted = PendingNotification.updatedFrom(null, chat, "login-a", emptySet(), 42)!!
        assertEquals(accepted, accepted.forSession("login-a", 42))
        assertNull(accepted.forSession("new-login-a", 42))
        assertNull(accepted.forSession("login-b", 43))
    }
}
