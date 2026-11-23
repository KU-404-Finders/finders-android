package com.ku.lostandfound

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.admin.viewmodel.AdminViewModel
import com.ku.lostandfound.ui.admin.viewmodel.AdminViewModel.Section
import com.ku.lostandfound.ui.admin.viewmodel.AdminUiState
import com.ku.lostandfound.ui.navigation.popIfCurrent
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class NavigationAdminRegressionTest : StabilityFixture() {
    @Test fun completionOnlyPopsTheEntryThatStartedTheAction() {
        val nav = mock(NavController::class.java)
        val roomA = mock(NavBackStackEntry::class.java)
        val roomB = mock(NavBackStackEntry::class.java)
        `when`(nav.currentBackStackEntry).thenReturn(roomB)
        nav.popIfCurrent(roomA)
        verify(nav, never()).popBackStack()
        nav.popIfCurrent(roomB)
        verify(nav).popBackStack()
    }

    @Test fun userPaginationKeepsSubmittedKeywordAndRejectsStaleFirstPage() {
        val calls = mutableListOf<Pair<String?, Int>>()
        val pending = CompletableDeferred<AdminPage<AdminUser>>()
        val api = object : AdminApiService by mock(AdminApiService::class.java) {
            override suspend fun users(keyword: String?, page: Int, size: Int): retrofit2.Response<ApiResponse<AdminPage<AdminUser>>> {
                calls += keyword to page
                val user = AdminUser(page.toLong(), "", "new", "USER", false, createdAt = "")
                return ok(if (keyword == "old") pending.await()
                    else AdminPage(listOf(user), page, size, page == 0))
            }
        }
        val vm = AdminViewModel(api)
        vm.loadUsers("old")
        vm.loadUsers("new")
        dispatch()
        repeat(2) { vm.loadUsers(more = true) }
        dispatch()
        pending.complete(AdminPage(emptyList(), 0, 20, true))
        dispatch()
        assertEquals(listOf("old" to 0, "new" to 0, "new" to 1), calls)
        assertEquals(listOf(0L, 1L), vm.users.map { it.id })
        assertFalse(vm.hasNext(Section.Users))
        assertEquals(AdminUiState.Idle, vm.state(Section.Users))
    }

    @Test fun duplicateReportActionUsesAuthoritativeDashboardCount() {
        val report = AdminReport(1, reporterName = "", reportedUserName = "", postTitle = "",
            reason = "", status = "RECEIVED", isProcessed = false, createdAt = "")
        val processed = report.copy(isProcessed = true)
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val api = object : AdminApiService by mock(AdminApiService::class.java) {
            override suspend fun report(id: Long) = ok(report)
            override suspend fun dashboard() = ok(AdminDashboard(4, 9, 3, 2))
            override suspend fun processReport(id: Long, request: ProcessAdminReportRequest): retrofit2.Response<ApiResponse<AdminReport>> {
                calls++
                release.await()
                return ok(processed)
            }
        }
        val vm = AdminViewModel(api)
        vm.loadReport(1)
        dispatch()
        repeat(2) { vm.processReport(1, "DISMISS_REPORT") {} }
        release.complete(Unit)
        dispatch()
        assertEquals(1, calls)
        assertTrue(vm.selectedReport!!.isProcessed)
        assertEquals(4L, vm.dashboard!!.pendingReportCount)
    }
}
