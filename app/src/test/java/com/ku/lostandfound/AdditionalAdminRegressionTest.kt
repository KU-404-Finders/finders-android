package com.ku.lostandfound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.admin.viewmodel.AdminViewModel
import com.ku.lostandfound.ui.admin.viewmodel.AdminViewModel.Section
import com.ku.lostandfound.ui.admin.viewmodel.AdminUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import retrofit2.Response

class AdditionalAdminRegressionTest : StabilityFixture() {
    private val models = mutableListOf<ViewModel>()
    private fun keep(vm: AdminViewModel) = vm.also { models += it }
    @After fun closeModels() { models.forEach { it.viewModelScope.cancel() } }
    private val report = AdminReport(1, reporterName = "", reportedUserName = "", postTitle = "",
        reason = "", status = "RECEIVED", isProcessed = false, createdAt = "")

    @Test fun reportMutationInvalidatesOlderListResponseAndStillUpdatesDashboardAndCallback() {
        val lists = mutableListOf<CompletableDeferred<AdminPage<AdminReport>>>()
        val mutation = CompletableDeferred<Unit>()
        val updated = report.copy(isProcessed = true)
        val api = object : AdminApiService by mock(AdminApiService::class.java) {
            override suspend fun report(id: Long) = ok(report)
            override suspend fun reports(status: String?, page: Int, size: Int) =
                ok(CompletableDeferred<AdminPage<AdminReport>>().also(lists::add).await())
            override suspend fun processReport(id: Long, request: ProcessAdminReportRequest): Response<ApiResponse<AdminReport>> {
                mutation.await(); return ok(updated)
            }
            override suspend fun dashboard() = ok(AdminDashboard(4, 3, 2, 1))
        }
        val vm = keep(AdminViewModel(api))
        vm.loadReport(1)
        dispatch()
        var callbacks = 0
        vm.processReport(1, "DISMISS_REPORT") { callbacks++ }
        vm.loadReports()
        dispatch()
        mutation.complete(Unit)
        dispatch()
        assertEquals(2, lists.size)
        lists[0].complete(AdminPage(listOf(report), 0, 20, false))
        dispatch()
        assertTrue(vm.reports.isEmpty())
        lists[1].complete(AdminPage(listOf(updated), 0, 20, false))
        dispatch()
        assertTrue(vm.reports.single().isProcessed)
        assertEquals(4L, vm.dashboard!!.pendingReportCount)
        assertEquals(1, callbacks)
    }

    @Test fun suspensionInvalidatesOlderUserListAndKeepsSubmittedKeyword() {
        val user = AdminUser(1, "", "name", "USER", false, createdAt = "")
        val lists = mutableListOf<CompletableDeferred<AdminPage<AdminUser>>>()
        val keywords = mutableListOf<String?>()
        val mutation = CompletableDeferred<Unit>()
        val api = object : AdminApiService by mock(AdminApiService::class.java) {
            override suspend fun user(id: Long) = ok(user)
            override suspend fun users(keyword: String?, page: Int, size: Int): Response<ApiResponse<AdminPage<AdminUser>>> {
                keywords += keyword
                return ok(CompletableDeferred<AdminPage<AdminUser>>().also(lists::add).await())
            }
            override suspend fun setSuspension(id: Long, request: AdminSuspensionRequest): Response<ApiResponse<AdminUser>> {
                mutation.await(); return ok(user.copy(suspended = true))
            }
        }
        val vm = keep(AdminViewModel(api))
        vm.loadUser(1)
        vm.loadUsers("name")
        dispatch()
        vm.setUserSuspension(1, true) {}
        dispatch()
        mutation.complete(Unit)
        dispatch()
        assertEquals(listOf("name", "name"), keywords)
        lists[0].complete(AdminPage(listOf(user), 0, 20, false))
        dispatch()
        assertTrue(vm.users.isEmpty())
        lists[1].complete(AdminPage(listOf(user.copy(suspended = true)), 0, 20, false))
        dispatch()
        assertTrue(vm.users.single().suspended)
    }

    @Test fun failedReportProcessingAndRetryKeepAlreadyLoadedDetail() {
        var reads = 0
        val retry = CompletableDeferred<AdminReport>()
        val api = object : AdminApiService by mock(AdminApiService::class.java) {
            override suspend fun report(id: Long) = ok(if (++reads == 1) report else retry.await())
            override suspend fun processReport(id: Long, request: ProcessAdminReportRequest) =
                Response.success(ApiResponse<AdminReport>(false, "uncertain", null))
        }
        val vm = keep(AdminViewModel(api))
        vm.loadReport(1)
        dispatch()
        vm.processReport(1, "DISMISS_REPORT") { fail() }
        dispatch()
        assertEquals(report, vm.selectedReport)
        assertTrue(vm.state(Section.Report) is AdminUiState.Error)
        vm.loadReport(1)
        dispatch()
        assertEquals(report, vm.selectedReport)
        retry.complete(report.copy(isProcessed = true))
        dispatch()
        assertTrue(vm.selectedReport!!.isProcessed)
    }

    private fun reportNavigation(success: Boolean, detailFirst: Boolean) {
        val mutation = CompletableDeferred<Unit>()
        val detail = CompletableDeferred<Unit>()
        var lists = 0
        var dashboards = 0
        var mutations = 0
        val api = object : AdminApiService by mock(AdminApiService::class.java) {
            override suspend fun report(id: Long): Response<ApiResponse<AdminReport>> {
                if (id == 2L) detail.await()
                return ok(report.copy(id = id))
            }
            override suspend fun processReport(id: Long, request: ProcessAdminReportRequest): Response<ApiResponse<AdminReport>> {
                mutations++
                mutation.await()
                return Response.success(ApiResponse(success, "failed", if (success) report.copy(isProcessed = true) else null))
            }
            override suspend fun reports(status: String?, page: Int, size: Int): Response<ApiResponse<AdminPage<AdminReport>>> {
                lists++
                return ok(AdminPage(listOf(report.copy(isProcessed = true)), 0, 20, false))
            }
            override suspend fun dashboard(): Response<ApiResponse<AdminDashboard>> {
                dashboards++
                return ok(AdminDashboard(4, 3, 2, 1))
            }
        }
        val vm = keep(AdminViewModel(api))
        vm.loadReport(1)
        dispatch()
        var callbacks = 0
        repeat(2) { vm.processReport(1, "DISMISS_REPORT") { callbacks++ } }
        dispatch()
        vm.loadReport(2)
        dispatch()
        if (detailFirst) { detail.complete(Unit); dispatch() }
        mutation.complete(Unit)
        dispatch()
        assertEquals(if (success) 1 else 0, lists)
        assertEquals(if (success) 1 else 0, dashboards)
        assertEquals(1, mutations)
        assertEquals(0, callbacks)
        if (!detailFirst) {
            assertEquals(AdminUiState.Loading, vm.state(Section.Report))
            assertNull(vm.selectedReport)
            detail.complete(Unit)
            dispatch()
        }
        assertEquals(2L, vm.selectedReport!!.id)
        assertFalse(vm.selectedReport!!.isProcessed)
        assertEquals(AdminUiState.Idle, vm.state(Section.Report))
    }

    @Test fun reportSuccessAfterNavigationRefreshesWhileNextDetailIsLoading() = reportNavigation(true, false)
    @Test fun reportSuccessAfterNextDetailCannotOverwriteIt() = reportNavigation(true, true)
    @Test fun reportFailureAfterNavigationDoesNotRefreshOrShowSuccess() = reportNavigation(false, true)

    @Test fun suspensionSuccessAfterNavigationRefreshesCurrentSearchWithoutOverwritingDetail() {
        val user = AdminUser(1, "", "name", "USER", false, createdAt = "")
        val mutation = CompletableDeferred<Unit>()
        val keywords = mutableListOf<String?>()
        val api = object : AdminApiService by mock(AdminApiService::class.java) {
            override suspend fun user(id: Long) = ok(user.copy(id = id))
            override suspend fun users(keyword: String?, page: Int, size: Int): Response<ApiResponse<AdminPage<AdminUser>>> {
                keywords += keyword
                return ok(AdminPage(listOf(user.copy(suspended = true)), 0, 20, false))
            }
            override suspend fun setSuspension(id: Long, request: AdminSuspensionRequest): Response<ApiResponse<AdminUser>> {
                mutation.await()
                return ok(user.copy(suspended = true))
            }
        }
        val vm = keep(AdminViewModel(api))
        vm.loadUser(1)
        vm.loadUsers("name")
        dispatch()
        vm.setUserSuspension(1, true) { fail("Stale detail callback") }
        dispatch()
        vm.loadUser(2)
        dispatch()
        mutation.complete(Unit)
        dispatch()
        assertEquals(listOf("name", "name"), keywords)
        assertEquals(2L, vm.selectedUser!!.id)
        assertFalse(vm.selectedUser!!.suspended)
        assertTrue(vm.users.single().suspended)
    }

    @Test fun previousSessionMutationDoesNotRefreshNewAccount() {
        val mutation = CompletableDeferred<Unit>()
        var lists = 0
        val api = object : AdminApiService by mock(AdminApiService::class.java) {
            override suspend fun report(id: Long) = ok(report)
            override suspend fun processReport(id: Long, request: ProcessAdminReportRequest): Response<ApiResponse<AdminReport>> {
                mutation.await()
                return ok(report.copy(isProcessed = true))
            }
            override suspend fun reports(status: String?, page: Int, size: Int): Response<ApiResponse<AdminPage<AdminReport>>> {
                lists++
                return ok(AdminPage(emptyList(), 0, 20, false))
            }
        }
        val vm = keep(AdminViewModel(api))
        vm.loadReport(1)
        dispatch()
        vm.processReport(1, "DISMISS_REPORT") { fail() }
        dispatch()
        vm.clearSession()
        login("b")
        mutation.complete(Unit)
        dispatch()
        assertEquals(0, lists)
        assertNull(vm.selectedReport)
        assertNull(vm.dashboard)
    }
}
