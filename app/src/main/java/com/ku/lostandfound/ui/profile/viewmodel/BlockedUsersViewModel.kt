package com.ku.lostandfound.ui.profile.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.data.BlockedUserNames
import com.ku.lostandfound.network.ModerationApiService
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class BlockedUserRow(val id: Long, val name: String)

class BlockedUsersViewModel @JvmOverloads constructor(
    application: Application,
    private val api: ModerationApiService = RetrofitClient.moderationApi,
) : AndroidViewModel(application) {
    private data class Account(val id: Long, val generation: Long)
    private var account: Account? = null
    private var requestId = 0
    private val names = BlockedUserNames(application)
    var users by mutableStateOf<List<BlockedUserRow>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var unblocking by mutableStateOf<Set<Long>>(emptySet()); private set

    private fun current(owner: Account) = account === owner && TokenManager.isCurrentSession(owner.generation)

    fun attachAccount(id: Long?) {
        val next = id?.let { Account(it, TokenManager.sessionGeneration()) }
        if (account == next) return
        account = next
        requestId++
        users = emptyList()
        unblocking = emptySet()
        loading = false
        error = null
    }

    fun load() {
        val owner = account ?: return
        if (!current(owner)) return
        val request = ++requestId
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val response = api.blockedUsers()
                if (!current(owner) || request != requestId) return@launch
                val data = response.body()?.takeIf { response.isSuccessful && it.success }?.data
                if (data == null) error = "차단한 사용자를 불러오지 못했습니다."
                else users = data.distinctBy { it.blockedUserId }.map {
                    BlockedUserRow(it.blockedUserId, names.displayName(owner.id, it.blockedUserId))
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (current(owner) && request == requestId) error = "서버와 연결할 수 없습니다."
            } finally {
                if (current(owner) && request == requestId) loading = false
            }
        }
    }

    fun unblock(userId: Long, onSuccess: () -> Unit) {
        val owner = account ?: return
        if (!current(owner) || userId in unblocking || users.none { it.id == userId }) return
        unblocking = unblocking + userId
        error = null
        viewModelScope.launch {
            val success = try {
                val response = api.unblock(userId)
                response.isSuccessful && (response.code() == 204 || response.body()?.success == true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { false }
            if (!current(owner)) return@launch
            unblocking = unblocking - userId
            if (success) {
                // A list requested before this DELETE must not bring the user back.
                requestId++
                loading = false
                users = users.filterNot { it.id == userId }
                names.remove(owner.id, userId)
                onSuccess()
            } else error = "차단 해제에 실패했습니다. 다시 시도해주세요."
        }
    }
}
