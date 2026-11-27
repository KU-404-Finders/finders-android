package com.ku.lostandfound

import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.*
import com.ku.lostandfound.notification.NotificationInboxRefreshBus
import com.ku.lostandfound.ui.chat.viewmodel.ChatViewModel
import com.ku.lostandfound.ui.notification.NotificationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class EventSubscriptionLifecycleRegressionTest : StabilityFixture() {
    @Test fun oldSchedulerSubscriberBlocksArrivalsUntilCancellationIsDrained() {
        val bus = NotificationInboxRefreshBus.events
        val owner = checkNotNull(TokenManager.reminderSession())
        assertEquals(0, bus.subscriptionCount.value)
        val oldScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(oldScheduler))
        val oldChat = ChatViewModel(mock(ChatApiService::class.java),
            mock(LostItemApiService::class.java), mock(FoundItemApiService::class.java))
        var inbox: NotificationViewModel? = null
        try {
            oldScheduler.runCurrent()
            assertEquals(1, bus.subscriptionCount.value)
            // Queue an arrival on the old scheduler, then leave it undrained at teardown.
            assertTrue(bus.tryEmit(owner))
            Dispatchers.setMain(main)
            var pages = 0
            var badges = 0
            val api = object : NotificationApiService by mock(NotificationApiService::class.java) {
                override suspend fun notifications(limit: Int, snapshotSequence: Long?, beforeSequence: Long?) =
                    ok(NotificationPageData(listOf(InboxNotificationData("one", "CHAT", roomId = 42, sequence = 1)),
                        (++pages).toLong(), null))
                override suspend fun unreadCount() = ok(NotificationUnreadCountData(++badges))
            }
            val activeInbox = NotificationViewModel(application, api).also { inbox = it }
            activeInbox.setInboxOpen(true)
            activeInbox.attachAccount(1)
            dispatch()
            assertEquals(2, bus.subscriptionCount.value)
            assertEquals(1, pages)

            val confirmedBadge = activeInbox.unreadCount
            // A new subscriber does not replay the old event, but the old subscriber
            // still occupies the shared buffer until its queued continuation runs.
            assertFalse(bus.tryEmit(owner))
            dispatch()
            assertEquals(1, pages)
            assertEquals(confirmedBadge, activeInbox.unreadCount)

            oldChat.viewModelScope.cancel()
            assertEquals(2, bus.subscriptionCount.value)
            oldScheduler.runCurrent()
            assertEquals(1, bus.subscriptionCount.value)
            assertTrue(bus.tryEmit(owner))
            dispatch()
            assertEquals(2, pages)
            assertTrue(activeInbox.unreadCount > confirmedBadge)
        } finally {
            Dispatchers.setMain(main)
            oldChat.viewModelScope.cancel()
            oldScheduler.runCurrent()
            inbox?.viewModelScope?.cancel()
            dispatch()
        }
    }
}
