package com.ku.lostandfound.ui.admin.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ku.lostandfound.network.AdminDashboard
import com.ku.lostandfound.network.AdminAudit
import com.ku.lostandfound.network.AdminPost
import com.ku.lostandfound.network.AdminReport
import com.ku.lostandfound.network.AdminSuspensionRequest
import com.ku.lostandfound.network.AdminUser
import com.ku.lostandfound.network.AuthErrorResponse
import com.ku.lostandfound.network.NetworkLog
import com.ku.lostandfound.network.ProcessAdminReportRequest
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import kotlinx.coroutines.launch

sealed class AdminUiState {
    data object Idle : AdminUiState()
    data object Loading : AdminUiState()
    data class Error(val message: String) : AdminUiState()
}

class AdminViewModel : ViewModel() {
    private var cacheGeneration = 0L
    private var usersRequestId = 0L
    var dashboard: AdminDashboard? by mutableStateOf(null)
        private set
    var reports: List<AdminReport> by mutableStateOf(emptyList())
        private set
    var selectedReport: AdminReport? by mutableStateOf(null)
        private set
    var users: List<AdminUser> by mutableStateOf(emptyList())
        private set
    var selectedUser: AdminUser? by mutableStateOf(null)
        private set
    var posts: List<AdminPost> by mutableStateOf(emptyList())
        private set
    var audits: List<AdminAudit> by mutableStateOf(emptyList())
        private set
    var uiState: AdminUiState by mutableStateOf(AdminUiState.Idle)
        private set

    fun loadDashboard() = viewModelScope.launch {
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()
        uiState = AdminUiState.Loading
        call(
            requestGeneration, sessionGeneration,
            request = { RetrofitClient.adminApi.dashboard() },
            onSuccess = { dashboard = it },
        )
    }

    fun loadReports() = viewModelScope.launch {
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()
        uiState = AdminUiState.Loading
        call(
            requestGeneration, sessionGeneration,
            request = { RetrofitClient.adminApi.reports() },
            onSuccess = { reports = it.items },
        )
    }

    fun loadReport(id: Long) = viewModelScope.launch {
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()
        uiState = AdminUiState.Loading
        call(
            requestGeneration, sessionGeneration,
            request = { RetrofitClient.adminApi.report(id) },
            onSuccess = { selectedReport = it },
        )
    }

    fun processReport(id: Long, action: String, onSuccess: () -> Unit) = viewModelScope.launch {
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()
        uiState = AdminUiState.Loading
        val wasProcessed = reports.firstOrNull { it.id == id }?.isProcessed
            ?: selectedReport?.takeIf { it.id == id }?.isProcessed
            ?: false
        call(
            requestGeneration, sessionGeneration,
            request = { RetrofitClient.adminApi.processReport(id, ProcessAdminReportRequest(action)) },
            onSuccess = { updated ->
                selectedReport = updated
                reports = reports.map { if (it.id == updated.id) updated else it }
                dashboard = dashboard?.copy(
                    pendingReportCount = (dashboard?.pendingReportCount ?: 0L) -
                        if (!wasProcessed && updated.isProcessed) 1 else 0
                )
                onSuccess()
            },
        )
    }

    fun loadUsers(keyword: String? = null) = viewModelScope.launch {
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()
        val requestId = ++usersRequestId
        uiState = AdminUiState.Loading
        call(
            requestGeneration, sessionGeneration,
            isLatestRequest = { requestId == usersRequestId },
            request = { RetrofitClient.adminApi.users(keyword = keyword?.takeIf { it.isNotBlank() }) },
            onSuccess = { users = it.items },
        )
    }

    fun loadUser(id: Long) = viewModelScope.launch {
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()
        uiState = AdminUiState.Loading
        call(requestGeneration, sessionGeneration, request = { RetrofitClient.adminApi.user(id) }, onSuccess = { selectedUser = it })
    }

    fun setUserSuspension(id: Long, suspended: Boolean, onSuccess: () -> Unit) = viewModelScope.launch {
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()
        uiState = AdminUiState.Loading
        call(
            requestGeneration, sessionGeneration,
            request = { RetrofitClient.adminApi.setSuspension(id, AdminSuspensionRequest(suspended)) },
            onSuccess = { updated ->
                selectedUser = updated
                users = users.map { if (it.id == updated.id) updated else it }
                onSuccess()
            },
        )
    }

    fun loadPosts(postType: String) = viewModelScope.launch {
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()
        uiState = AdminUiState.Loading
        call(requestGeneration, sessionGeneration, request = { RetrofitClient.adminApi.posts(postType) }, onSuccess = { posts = it.items })
    }

    fun deletePost(post: AdminPost, onSuccess: () -> Unit) = viewModelScope.launch {
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()
        uiState = AdminUiState.Loading
        callUnit(
            requestGeneration, sessionGeneration,
            request = { RetrofitClient.adminApi.deletePost(post.postType, post.id) },
            onSuccess = {
                posts = posts.filterNot { it.postType == post.postType && it.id == post.id }
                onSuccess()
            },
        )
    }

    fun loadAudits() = viewModelScope.launch {
        val requestGeneration = cacheGeneration
        val sessionGeneration = TokenManager.sessionGeneration()
        uiState = AdminUiState.Loading
        call(requestGeneration, sessionGeneration, request = { RetrofitClient.adminApi.auditLogs() }, onSuccess = { audits = it.items })
    }

    fun clearSession() {
        cacheGeneration++
        dashboard = null
        reports = emptyList()
        selectedReport = null
        users = emptyList()
        selectedUser = null
        posts = emptyList()
        audits = emptyList()
        usersRequestId++
        uiState = AdminUiState.Idle
    }

    private suspend fun <T> call(
        requestGeneration: Long,
        sessionGeneration: Long,
        isLatestRequest: () -> Boolean = { true },
        request: suspend () -> retrofit2.Response<com.ku.lostandfound.network.ApiResponse<T>>,
        onSuccess: (T) -> Unit,
    ) {
        try {
            val response = request()
            if (!isCurrentRequest(requestGeneration, sessionGeneration) || !isLatestRequest()) return
            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.data != null) {
                onSuccess(body.data)
                uiState = AdminUiState.Idle
            } else {
                val message = body?.message ?: parseError(response.errorBody()?.string())
                uiState = AdminUiState.Error(message)
            }
        } catch (exception: Exception) {
            if (!isCurrentRequest(requestGeneration, sessionGeneration) || !isLatestRequest()) return
            NetworkLog.exception("adminApi", exception)
            uiState = AdminUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
        }
    }

    private suspend fun callUnit(
        requestGeneration: Long,
        sessionGeneration: Long,
        request: suspend () -> retrofit2.Response<com.ku.lostandfound.network.ApiResponse<Unit>>,
        onSuccess: () -> Unit,
    ) {
        try {
            val response = request()
            if (!isCurrentRequest(requestGeneration, sessionGeneration)) return
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                onSuccess()
                uiState = AdminUiState.Idle
            } else {
                uiState = AdminUiState.Error(body?.message ?: parseError(response.errorBody()?.string()))
            }
        } catch (exception: Exception) {
            if (!isCurrentRequest(requestGeneration, sessionGeneration)) return
            NetworkLog.exception("adminApi", exception)
            uiState = AdminUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
        }
    }

    private fun parseError(raw: String?): String = runCatching {
        Gson().fromJson(raw, AuthErrorResponse::class.java).message
    }.getOrNull() ?: "관리자 요청을 처리하지 못했습니다."

    private fun isCurrentRequest(requestGeneration: Long, sessionGeneration: Long): Boolean =
        cacheGeneration == requestGeneration && TokenManager.isCurrentSession(sessionGeneration)
}
