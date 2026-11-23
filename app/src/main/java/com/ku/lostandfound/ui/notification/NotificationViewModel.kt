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
import com.ku.lostandfound.network.NotificationApiService
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.notification.NotificationInboxRefreshBus
import com.ku.lostandfound.network.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class InboxState {
    data object Loading : InboxState()
    data object Ready : InboxState()
    data class Error(val message: String) : InboxState()
    data class Unsupported(val message: String) : InboxState()
}

class NotificationViewModel @JvmOverloads constructor(
    application: Application,
    private val notificationApi: NotificationApiService = RetrofitClient.notificationApi,
) : AndroidViewModel(application) {
    var state by mutableStateOf<InboxState>(InboxState.Ready); private set
    var items by mutableStateOf<List<InboxNotificationData>>(emptyList()); private set
    var unreadCount by mutableIntStateOf(0); private set
    var snapshotSequence by mutableStateOf<Long?>(null); private set
    var nextBeforeSequence by mutableStateOf<Long?>(null); private set
    var loadingMore by mutableStateOf(false); private set
    var settings by mutableStateOf<NotificationSettingsData?>(null); private set
    var settingsError by mutableStateOf<String?>(null); private set
    var refreshError by mutableStateOf<String?>(null); private set
    internal data class Account(val id: Long, val generation: Long) {
        val settingsMutex = Mutex()
        val readMutex = Mutex()
        val confirmedReads = mutableMapOf<String, String>()
    }
    internal var account by mutableStateOf<Account?>(null)
        private set
    private var inboxOpen = false
    private var inboxVisit = 0L
    private var arrivalRequestId = 0
    private var latestSnapshotSequence: Long? = null

    private fun current(owner: Account?) =
        owner != null && account === owner && TokenManager.isCurrentSession(owner.generation)

    fun setInboxOpen(open: Boolean) {
        if (inboxOpen != open) inboxVisit++
        inboxOpen = open
        if (open) load()
    }
    private var requestId = 0
    private var unreadRequestId = 0

    init {
        viewModelScope.launch {
            NotificationInboxRefreshBus.events.collectLatest {
                refreshUnreadCount()
                if (inboxOpen) refreshArrivals()
            }
        }
    }

    fun attachAccount(userId: Long?) {
        val next = userId?.let { Account(it, TokenManager.sessionGeneration()) }
        if (account == next) return
        account = next
        requestId++
        arrivalRequestId++
        unreadRequestId++
        items = emptyList()
        snapshotSequence = null
        latestSnapshotSequence = null
        nextBeforeSequence = null
        unreadCount = 0
        loadingMore = false
        settings = null
        settingsError = null
        refreshError = null
        state = InboxState.Ready
        if (next != null) {
            refreshUnreadCount()
            if (inboxOpen) load()
        }
    }

    fun load(reset: Boolean = true) {
        val owner = account ?: return
        if (!current(owner)) return
        if (!reset && (state == InboxState.Loading || loadingMore || nextBeforeSequence == null || snapshotSequence == null)) return
        if (reset) loadingMore = false
        if (reset) arrivalRequestId++
        refreshError = null
        val id = ++requestId
        if (reset) state = InboxState.Loading else loadingMore = true
        viewModelScope.launch {
            try {
                val response = notificationApi.notifications(
                    snapshotSequence = if (reset) null else snapshotSequence,
                    beforeSequence = if (reset) null else nextBeforeSequence,
                )
                if (!current(owner) || id != requestId) return@launch
                if (!response.isSuccessful) {
                    val error = response.errorBody()?.string().orEmpty()
                    if (items.isNotEmpty()) showLoadError("알림을 불러오지 못했습니다.")
                    else state = if (isUnsupported(response.code(), error)) InboxState.Unsupported("현재 서버에서는 알림함을 지원하지 않습니다.") else InboxState.Error("알림을 불러오지 못했습니다.")
                    return@launch
                }
                val page = response.body()?.takeIf { it.success }?.data ?: run { showLoadError(response.body()?.message ?: "알림을 불러오지 못했습니다."); return@launch }
                val merged = if (reset) page.items else (items + page.items).distinctBy { it.id }
                items = visible(merged)
                snapshotSequence = if (reset) page.snapshotSequence else snapshotSequence
                latestSnapshotSequence = maxOf(latestSnapshotSequence ?: page.snapshotSequence, page.snapshotSequence)
                nextBeforeSequence = page.nextBeforeSequence
                state = InboxState.Ready
                refreshUnreadCount()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (current(owner) && id == requestId) showLoadError("서버와 연결할 수 없습니다.") }
            finally { if (current(owner) && id == requestId) loadingMore = false }
        }
    }

    private fun showLoadError(message: String) {
        if (items.isEmpty()) state = InboxState.Error(message)
        else { state = InboxState.Ready; refreshError = message }
    }

    /** New arrivals use their own snapshot. The existing older-page cursor stays paired with its snapshot. */
    fun refreshArrivals() {
        val owner = account ?: return
        val through = snapshotSequence ?: run { load(); return }
        if (!current(owner)) return
        val id = ++arrivalRequestId
        refreshError = null
        viewModelScope.launch {
            try {
                val incoming = mutableListOf<InboxNotificationData>()
                var snapshot: Long? = null
                var before: Long? = null
                do {
                    val response = notificationApi.notifications(snapshotSequence = snapshot, beforeSequence = before)
                    if (!current(owner) || id != arrivalRequestId) return@launch
                    val page = response.body()?.takeIf { response.isSuccessful && it.success }?.data
                        ?: throw java.io.IOException("Inbox refresh failed")
                    incoming += page.items
                    snapshot = page.snapshotSequence
                    val previous = before
                    before = page.nextBeforeSequence
                    if (page.items.any { it.sequence <= through } || before == null) break
                    if (before == previous) throw java.io.IOException("Inbox cursor did not advance")
                } while (true)
                items = visible((incoming + items).distinctBy { it.id }.sortedByDescending { it.sequence })
                latestSnapshotSequence = maxOf(latestSnapshotSequence ?: through, snapshot ?: through)
                refreshError = null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (current(owner) && id == arrivalRequestId) showLoadError("알림 새로고침에 실패했습니다.")
            }
        }
    }

    fun refreshUnreadCount() {
        val owner = account ?: return
        if (!current(owner)) return
        val id = ++unreadRequestId
        viewModelScope.launch {
            try {
                val response = notificationApi.unreadCount()
                if (id == unreadRequestId && current(owner) && response.isSuccessful && response.body()?.success == true) {
                    unreadCount = response.body()?.data?.count?.coerceAtLeast(0) ?: 0
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the last confirmed badge when the refresh fails.
            }
        }
    }

    fun markRead(item: InboxNotificationData, onError: () -> Unit, onOpen: () -> Unit) {
        val owner = account ?: return
        if (!current(owner) || items.none { it === item }) return
        val visit = inboxVisit
        viewModelScope.launch {
            val success = try {
                owner.readMutex.withLock {
                    if (!current(owner)) return@launch
                    item.readAt != null || owner.confirmedReads.containsKey(item.id) || notificationApi.markRead(item.id).let {
                        val success = it.isSuccessful && it.body()?.success == true
                        if (success && current(owner)) {
                            // The API returns ApiResponse<Void>, without a server read timestamp.
                            owner.confirmedReads[item.id] = java.time.Instant.now().toString()
                            items = visible(items)
                        }
                        success
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!current(owner)) return@launch
            if (success) refreshUnreadCount()
            if (!inboxOpen || visit != inboxVisit) return@launch
            if (success) refreshArrivals() else onError()
            onOpen()
        }
    }

    fun markAllRead() {
        val owner = account ?: return
        val through = latestSnapshotSequence ?: snapshotSequence ?: return
        if (!current(owner)) return
        viewModelScope.launch {
            val success = try {
                notificationApi.markAllRead(MarkAllNotificationsReadRequest(through)).let {
                    it.isSuccessful && it.body()?.success == true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!current(owner)) return@launch
            if (success) load() else state = InboxState.Error("모두 읽음 처리에 실패했습니다.")
        }
    }

    fun hide(item: InboxNotificationData) {
        if (!current(account) || items.none { it === item }) return
        saveHidden(item.id)
        items = items.filterNot { it.id == item.id }
    }
    fun hideAll() {
        if (!current(account)) return
        items.forEach { saveHidden(it.id) }
        items = emptyList()
    }

    fun loadSettings() = requestSettings(null)

    fun updateSettings(patch: NotificationSettingsPatch) = requestSettings(patch)

    private fun requestSettings(patch: NotificationSettingsPatch?) {
        val owner = account ?: return
        viewModelScope.launch {
            owner.settingsMutex.withLock {
                if (!current(owner)) return@withLock
                val updated = try {
                    val api = notificationApi
                    val response = if (patch == null) api.settings() else api.updateSettings(patch)
                    response.body()?.takeIf { response.isSuccessful && it.success }?.data
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (!current(owner)) return@withLock
                if (updated != null) settings = updated
                settingsError = when {
                    updated != null -> null
                    patch == null -> "알림 설정을 불러오지 못했습니다."
                    else -> "설정 저장에 실패했습니다."
                }
            }
        }
    }

    private fun visible(source: List<InboxNotificationData>) = source.filterNot { isHidden(it.id) }.map { item ->
        val confirmed = account?.confirmedReads?.get(item.id)
        if (item.readAt == null && confirmed != null) item.copy(readAt = confirmed) else item
    }
    private fun hiddenKey() = "notification_hidden_${account?.id ?: "anonymous"}"
    private fun isHidden(id: String) = getApplication<Application>().getSharedPreferences("finders_notifications", 0).getStringSet(hiddenKey(), emptySet()).orEmpty().contains(id)
    private fun saveHidden(id: String) { val prefs = getApplication<Application>().getSharedPreferences("finders_notifications", 0); prefs.edit().putStringSet(hiddenKey(), prefs.getStringSet(hiddenKey(), emptySet()).orEmpty() + id).apply() }
    private fun isUnsupported(code: Int, errorBody: String): Boolean =
        code == 404 || (code == 500 && errorBody.contains("NoResourceFoundException", ignoreCase = true))
}
