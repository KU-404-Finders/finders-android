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
import com.ku.lostandfound.network.AdminApiService
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import retrofit2.Response
import com.ku.lostandfound.network.ApiResponse
import com.ku.lostandfound.network.AdminPage
import kotlinx.coroutines.launch

sealed class AdminUiState {
    data object Idle : AdminUiState()
    data object Loading : AdminUiState()
    data class Error(val message: String) : AdminUiState()
}

class AdminViewModel(
    private val adminApi: AdminApiService = RetrofitClient.adminApi,
) : ViewModel() {
    private var cacheGeneration = 0L
    enum class Section { Dashboard, Reports, Report, Users, User, Posts, Audits, Delete }
    private val requests = mutableMapOf<Section, Long>()
    private val mutations = mutableSetOf<String>()
    private val states = androidx.compose.runtime.mutableStateMapOf<Section, AdminUiState>()
    private val nextPages = androidx.compose.runtime.mutableStateMapOf<Section, Int>()
    private var searchKeyword: String? = null
    private var selectedPostType: String? = null
    fun state(section: Section) = states[section] ?: AdminUiState.Idle
    fun hasNext(section: Section) = section in nextPages
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
    /** iOS AdminReportListView opens on 미처리 (RECEIVED); null lists every status. */
    var reportStatus: String? by mutableStateOf(DEFAULT_REPORT_STATUS)
        private set

    fun selectReportStatus(status: String?) {
        if (status == reportStatus) return
        reportStatus = status
        loadReports()
    }
    fun loadDashboard() = request(Section.Dashboard, { adminApi.dashboard() }) { dashboard = it }

    fun loadReport(id: Long) {
        if (selectedReport?.id != id) selectedReport = null
        request(Section.Report, { adminApi.report(id) }) { selectedReport = it }
    }

    fun loadUser(id: Long) {
        selectedUser = null
        request(Section.User, { adminApi.user(id) }) { selectedUser = it }
    }

    fun processReport(id: Long, action: String, note: String? = null, onSuccess: () -> Unit) {
        if (selectedReport?.id != id || state(Section.Report) == AdminUiState.Loading) return
        val memo = note?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NOTE)
        request(Section.Report, { adminApi.processReport(id, ProcessAdminReportRequest(action, memo)) },
            mutationKey = "report:$id", confirmed = { loadReports(); loadDashboard() }) { updated ->
            selectedReport = updated
            reports = reports.map { if (it.id == updated.id) updated else it }
            onSuccess()
        }
    }

    fun setUserSuspension(id: Long, suspended: Boolean, reason: String? = null, onSuccess: () -> Unit) {
        if (selectedUser?.id != id || state(Section.User) == AdminUiState.Loading) return
        val memo = reason?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NOTE)
        request(Section.User, { adminApi.setSuspension(id, AdminSuspensionRequest(suspended, memo)) },
            mutationKey = "user:$id", confirmed = { loadUsers(keyword = searchKeyword) }) { updated ->
            selectedUser = updated
            users = users.map { if (it.id == id) updated else it }
            onSuccess()
        }
    }

    fun loadUsers(keyword: String? = null, more: Boolean = false) {
        if (!more) {
            searchKeyword = keyword?.takeIf { it.isNotBlank() }
            users = emptyList()
        }
        val query = searchKeyword
        page(Section.Users, more, { adminApi.users(keyword = query, page = it) }) {
            users = (users + it).distinctBy { user -> user.id }
        }
    }

    fun loadReports(more: Boolean = false) {
        if (!more) reports = emptyList()
        val status = reportStatus
        page(Section.Reports, more, { adminApi.reports(status = status, page = it) }) {
            reports = (reports + it).distinctBy { report -> report.id }
        }
    }

    fun loadPosts(postType: String, more: Boolean = false) {
        if (!more) {
            selectedPostType = postType
            posts = emptyList()
        }
        page(Section.Posts, more, { adminApi.posts(postType, page = it) }) {
            posts = (posts + it).distinctBy { post -> post.postType to post.id }
        }
    }

    fun loadAudits(more: Boolean = false) {
        if (!more) audits = emptyList()
        page(Section.Audits, more, { adminApi.auditLogs(page = it) }) {
            audits = (audits + it).distinctBy { audit -> audit.id }
        }
    }

    private fun <T> page(
        section: Section,
        more: Boolean,
        fetch: suspend (Int) -> Response<ApiResponse<AdminPage<T>>>,
        append: (List<T>) -> Unit,
    ) {
        if (more && state(section) == AdminUiState.Loading) return
        val page = if (more) nextPages[section] ?: return else 0
        if (!more) nextPages.remove(section)
        request(section, { fetch(page) }) { result ->
            append(result.items)
            if (result.hasNext) nextPages[section] = result.page + 1 else nextPages.remove(section)
        }
    }

    fun deletePost(post: AdminPost, onSuccess: () -> Unit) {
        if (state(Section.Delete) == AdminUiState.Loading) return
        execute(Section.Delete, { adminApi.deletePost(post.postType, post.id) }) { _, _ ->
            // Reject list responses captured before the server confirmed deletion.
            requests[Section.Posts] = (requests[Section.Posts] ?: 0L) + 1
            posts = posts.filterNot { it.postType == post.postType && it.id == post.id }
            selectedPostType?.let { loadPosts(it, more = false) }
            onSuccess()
        }
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
        states.clear()
        requests.clear()
        mutations.clear()
        nextPages.clear()
        searchKeyword = null
        selectedPostType = null
        reportStatus = DEFAULT_REPORT_STATUS
    }

    private fun <T> request(
        section: Section, fetch: suspend () -> Response<ApiResponse<T>>,
        mutationKey: String? = null, confirmed: () -> Unit = {}, accept: (T) -> Unit,
    ) =
        execute(section, fetch, mutationKey) { body, current ->
            val data = body.data
            if (data == null) {
                if (current) states[section] = AdminUiState.Error("응답에 데이터가 없습니다.")
            } else {
                confirmed()
                if (current) accept(data)
            }
        }

    private fun <T> execute(
        section: Section, fetch: suspend () -> Response<ApiResponse<T>>,
        mutationKey: String? = null, accept: (ApiResponse<T>, Boolean) -> Unit,
    ) {
        val cache = cacheGeneration
        val session = TokenManager.sessionGeneration()
        val mutation = mutationKey?.let { "$cache:$session:$it" }
        if (mutation != null && !mutations.add(mutation)) return
        val id = (requests[section] ?: 0L) + 1
        requests[section] = id
        states[section] = AdminUiState.Loading
        fun current() = isCurrentRequest(cache, session) && requests[section] == id
        viewModelScope.launch {
            try {
                val response = fetch()
                if (!isCurrentRequest(cache, session)) return@launch
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    val visible = current()
                    if (visible) states[section] = AdminUiState.Idle
                    if (visible || mutation != null) accept(body, visible)
                } else if (current()) {
                    states[section] = AdminUiState.Error(body?.message ?: parseError(response.errorBody()?.string()))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!current()) return@launch
                NetworkLog.exception("adminApi", e)
                states[section] = AdminUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            } finally {
                mutation?.let { mutations.remove(it) }
            }
        }
    }

    private fun parseError(raw: String?): String = runCatching {
        Gson().fromJson(raw, AuthErrorResponse::class.java).message
    }.getOrNull() ?: "관리자 요청을 처리하지 못했습니다."

    private fun isCurrentRequest(requestGeneration: Long, sessionGeneration: Long): Boolean =
        cacheGeneration == requestGeneration && TokenManager.isCurrentSession(sessionGeneration)

    private companion object {
        const val DEFAULT_REPORT_STATUS = "RECEIVED"
        const val MAX_NOTE = 1000 // server @Size(max = 1000)
    }
}
