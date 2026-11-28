package com.ku.lostandfound.notification

import com.ku.lostandfound.network.TokenManager
import kotlinx.coroutines.flow.MutableSharedFlow

/** Process-local signal only; FCM payloads never carry unread counts. */
object NotificationInboxRefreshBus {
    val events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    fun notifyArrival() {
        TokenManager.reminderSession()?.let { events.tryEmit(it) }
    }
}
