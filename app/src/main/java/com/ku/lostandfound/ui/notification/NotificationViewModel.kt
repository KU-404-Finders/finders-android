package com.ku.lostandfound.ui.notification

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.InboxNotificationData
import com.ku.lostandfound.network.MarkAllNotificationsReadRequest
import com.ku.lostandfound.network.NotificationSettingsData
import com.ku.lostandfound.network.NotificationSettingsPatch
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.notification.NotificationInboxRefreshBus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed class InboxState {
    data object Loading : InboxState()
    data object Ready : InboxState()
    data class Error(val message: String) : InboxState()
    data class Unsupported(val message: String) : InboxState()
}

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    init {
        viewModelScope.launch { NotificationInboxRefreshBus.events.collectLatest { refreshUnreadCount() } }
    }
    var state by mutableStateOf<InboxState>(InboxState.Loading); private set
    var items by mutableStateOf<List<InboxNotificationData>>(emptyList()); private set
    var unreadCount by mutableIntStateOf(0); private set
    var snapshotSequence by mutableStateOf<Long?>(null); private set
    var nextBeforeSequence by mutableStateOf<Long?>(null); private set
    var loadingMore by mutableStateOf(false); private set
    var settings by mutableStateOf<NotificationSettingsData?>(null); private set
    var settingsError by mutableStateOf<String?>(null); private set
    private var accountId: Long? = null
    private var requestId = 0
    private var unreadRequestId = 0
    private var settingRequestId = 0

    fun attachAccount(userId: Long?) {
        if (accountId == userId) return
        requestId++; unreadRequestId++; settingRequestId++
        accountId = userId; items = emptyList(); snapshotSequence = null; nextBeforeSequence = null; unreadCount = 0
        state = InboxState.Loading
    }

    fun load(reset: Boolean = true) {
        if (!reset && (loadingMore || nextBeforeSequence == null || snapshotSequence == null)) return
        val id = ++requestId
        if (reset) state = InboxState.Loading else loadingMore = true
        viewModelScope.launch {
            try {
                val response = RetrofitClient.notificationApi.notifications(
                    snapshotSequence = if (reset) null else snapshotSequence,
                    beforeSequence = if (reset) null else nextBeforeSequence,
                )
                if (id != requestId) return@launch
                if (!response.isSuccessful) {
                    val error = response.errorBody()?.string().orEmpty()
                    state = if (isUnsupported(response.code(), error)) InboxState.Unsupported("현재 서버에서는 알림함을 지원하지 않습니다.") else InboxState.Error("알림을 불러오지 못했습니다.")
                    return@launch
                }
                val page = response.body()?.takeIf { it.success }?.data ?: run { state = InboxState.Error(response.body()?.message ?: "알림을 불러오지 못했습니다."); return@launch }
                val merged = if (reset) page.items else (items + page.items).distinctBy { it.id }
                items = visible(merged)
                snapshotSequence = if (reset) page.snapshotSequence else snapshotSequence
                nextBeforeSequence = page.nextBeforeSequence
                state = InboxState.Ready
                refreshUnreadCount()
            } catch (_: Exception) { if (id == requestId) state = InboxState.Error("서버와 연결할 수 없습니다.") }
            finally { if (id == requestId) loadingMore = false }
        }
    }

    fun refreshUnreadCount() {
        val id = ++unreadRequestId
        val requestedAccount = accountId
        viewModelScope.launch {
        try {
            val response = RetrofitClient.notificationApi.unreadCount()
            if (id == unreadRequestId && requestedAccount == accountId && response.isSuccessful && response.body()?.success == true) unreadCount = response.body()?.data?.count?.coerceAtLeast(0) ?: 0
        } catch (_: Exception) { }
        }
    }

    fun markRead(item: InboxNotificationData, onSuccess: (() -> Unit)? = null) = viewModelScope.launch {
        try {
            val response = if (item.readAt == null) RetrofitClient.notificationApi.markRead(item.id) else null
            if (response == null || (response.isSuccessful && response.body()?.success == true)) { load(true); onSuccess?.invoke() }
        } catch (_: Exception) { state = InboxState.Error("알림 읽음 처리에 실패했습니다.") }
    }

    fun markAllRead() {
        val through = snapshotSequence ?: return
        viewModelScope.launch { try { val r = RetrofitClient.notificationApi.markAllRead(MarkAllNotificationsReadRequest(through)); if (r.isSuccessful && r.body()?.success == true) load(true) else state = InboxState.Error("모두 읽음 처리에 실패했습니다.") } catch (_: Exception) { state = InboxState.Error("서버와 연결할 수 없습니다.") } }
    }

    fun hide(item: InboxNotificationData) { saveHidden(item.id); items = items.filterNot { it.id == item.id } }
    fun hideAll() { items.forEach { saveHidden(it.id) }; items = emptyList() }

    fun loadSettings() = viewModelScope.launch {
        try { val r = RetrofitClient.notificationApi.settings(); settings = r.body()?.takeIf { r.isSuccessful && it.success }?.data; settingsError = if (settings == null) "알림 설정을 불러오지 못했습니다." else null }
        catch (_: Exception) { settingsError = "서버와 연결할 수 없습니다." }
    }

    fun updateSettings(patch: NotificationSettingsPatch) {
        val previous = settings ?: return; val id = ++settingRequestId
        viewModelScope.launch {
            try { val r = RetrofitClient.notificationApi.updateSettings(patch); val updated = r.body()?.takeIf { r.isSuccessful && it.success }?.data
                if (id == settingRequestId) { settings = updated ?: previous; settingsError = if (updated == null) "설정 저장에 실패했습니다." else null } }
            catch (_: Exception) { if (id == settingRequestId) { settings = previous; settingsError = "설정 저장에 실패했습니다." } }
        }
    }

    private fun visible(source: List<InboxNotificationData>) = source.filterNot { isHidden(it.id) }
    private fun hiddenKey() = "notification_hidden_${accountId ?: "anonymous"}"
    private fun isHidden(id: String) = getApplication<Application>().getSharedPreferences("finders_notifications", 0).getStringSet(hiddenKey(), emptySet()).orEmpty().contains(id)
    private fun saveHidden(id: String) { val prefs = getApplication<Application>().getSharedPreferences("finders_notifications", 0); prefs.edit().putStringSet(hiddenKey(), prefs.getStringSet(hiddenKey(), emptySet()).orEmpty() + id).apply() }
    private fun isUnsupported(code: Int, errorBody: String): Boolean =
        code == 404 || (code == 500 && errorBody.contains("NoResourceFoundException", ignoreCase = true))
}
