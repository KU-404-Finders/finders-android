package com.ku.lostandfound.ui.navigation

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.notification.handleRemotePush
import com.ku.lostandfound.notification.RemoteNotificationKind
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatPushAccountSwitchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.applicationContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val room = "732"
    private val channel = "finders_messages_high"

    @Test fun delayedPushArrivalAndPendingIntentAcrossLogout() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        TokenManager.init(context)
        TokenManager.clear()
        try {
            login(42)
            handleRemotePush(context, chat("42", "Fixture A"))
            val previousTap = awaitNotification("Fixture A").contentIntent

            TokenManager.clear()
            login(43)
            handleRemotePush(context, chat("42", "Stale A") + ("roomId" to "733"))
            // A valid B notification is queued behind the rejected A input.
            handleRemotePush(context, chat("43", "Fixture B"))
            awaitNotification("Fixture B")
            assertEquals(listOf("Fixture B"), remoteTitles())
            assertThrows(PendingIntent.CanceledException::class.java) { previousTap.send() }
            TokenManager.clear()
            await { remoteTitles().isEmpty() }
        } finally {
            TokenManager.clear()
        }
    }

    @Test fun commentAndMatchUseSeparateChannelsWithoutUnverifiedContent() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        TokenManager.init(context)
        TokenManager.clear()
        try {
            login(42)
            listOf(
                RemoteNotificationKind.COMMENT to NotificationManager.IMPORTANCE_DEFAULT,
                RemoteNotificationKind.MATCH to NotificationManager.IMPORTANCE_HIGH,
            ).forEach { (kind, importance) ->
                handleRemotePush(context, mapOf(
                    "type" to kind.name, "postId" to "7", "postType" to "LOST",
                    "title" to "Unverified title", "body" to "Unverified body",
                ))
                await { manager.activeNotifications.any { it.notification.channelId == kind.channelId } }
                assertEquals(importance, manager.getNotificationChannel(kind.channelId).importance)
                val notification = manager.activeNotifications.single { it.notification.channelId == kind.channelId }.notification
                assertEquals("찾을건대", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                assertEquals("앱에서 알림을 확인해 주세요.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            }
            TokenManager.clear()
            await { manager.activeNotifications.none { status ->
                RemoteNotificationKind.entries.any { status.notification.channelId == it.channelId }
            } }
        } finally {
            TokenManager.clear()
        }
    }

    private fun login(userId: Long) {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"user_id":$userId}""".toByteArray())
        assertTrue(TokenManager.saveNewSessionTokens(
            TokenManager.sessionGeneration(), "header.$payload.signature", "fixture-refresh",
        ))
    }

    private fun chat(recipient: String, title: String) = mapOf(
        "type" to "CHAT", "roomId" to room, "recipientUserId" to recipient,
        "title" to title, "body" to "Synthetic payload",
    )

    private fun remoteTitles() = manager.activeNotifications
        .filter { it.notification.channelId == channel }
        .map { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString() }

    private fun awaitNotification(title: String): Notification {
        await { remoteTitles() == listOf(title) }
        return manager.activeNotifications.single { it.tag == "CHAT_$room" }.notification
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
        assertTrue("NotificationManager state did not settle", predicate())
    }
}
