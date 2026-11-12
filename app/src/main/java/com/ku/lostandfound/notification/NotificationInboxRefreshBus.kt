package com.ku.lostandfound.notification

import kotlinx.coroutines.flow.MutableSharedFlow

/** Process-local signal only; FCM payloads never carry unread counts. */
object NotificationInboxRefreshBus {
    val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun notifyArrival() { events.tryEmit(Unit) }
}
