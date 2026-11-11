package com.ku.lostandfound.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

data class InboxNotificationData(
    val id: String,
    val type: String,
    val roomId: Long? = null,
    val messageId: Long? = null,
    val title: String? = null,
    val body: String? = null,
    val sequence: Long,
    val createdAt: String? = null,
    val readAt: String? = null,
    val unreadMessages: Long? = null,
)

data class NotificationPageData(
    val items: List<InboxNotificationData> = emptyList(),
    val snapshotSequence: Long,
    val nextBeforeSequence: Long? = null,
)

data class NotificationUnreadCountData(val count: Int = 0)
data class MarkAllNotificationsReadRequest(val throughSequence: Long)
data class MarkAllNotificationsReadData(val updated: Int = 0)

data class NotificationSettingsData(
    val chatEnabled: Boolean,
    val commentEnabled: Boolean,
    val similarItemEnabled: Boolean,
    val similarityThreshold: Double,
    val quietHours: QuietHoursData? = null,
)

data class QuietHoursData(
    val enabled: Boolean? = null,
    val start: String? = null,
    val end: String? = null,
    val timeZone: String? = null,
)

/** Nullable fields are omitted by Gson when unchanged. Quiet hours are intentionally not exposed in Android UI. */
data class NotificationSettingsPatch(
    val chatEnabled: Boolean? = null,
    val commentEnabled: Boolean? = null,
    val similarItemEnabled: Boolean? = null,
    val similarityThreshold: Double? = null,
)

interface NotificationApiService {
    @GET("api/v1/notifications")
    suspend fun notifications(
        @Query("limit") limit: Int = 30,
        @Query("snapshotSequence") snapshotSequence: Long? = null,
        @Query("beforeSequence") beforeSequence: Long? = null,
    ): Response<ApiResponse<NotificationPageData>>

    @GET("api/v1/notifications/unread-count")
    suspend fun unreadCount(): Response<ApiResponse<NotificationUnreadCountData>>

    @PATCH("api/v1/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): Response<ApiResponse<Unit>>

    @PATCH("api/v1/notifications/read-all")
    suspend fun markAllRead(
        @Body request: MarkAllNotificationsReadRequest,
    ): Response<ApiResponse<MarkAllNotificationsReadData>>

    @GET("api/v1/users/me/notification-settings")
    suspend fun settings(): Response<ApiResponse<NotificationSettingsData>>

    @PATCH("api/v1/users/me/notification-settings")
    suspend fun updateSettings(
        @Body patch: NotificationSettingsPatch,
    ): Response<ApiResponse<NotificationSettingsData>>
}
