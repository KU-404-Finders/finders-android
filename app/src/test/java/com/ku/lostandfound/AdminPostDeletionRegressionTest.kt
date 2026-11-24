package com.ku.lostandfound

import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.admin.viewmodel.AdminUiState
import com.ku.lostandfound.ui.admin.viewmodel.AdminViewModel
import com.ku.lostandfound.ui.admin.viewmodel.AdminViewModel.Section
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import retrofit2.Response

class AdminPostDeletionRegressionTest : StabilityFixture() {
    private val post = AdminPost("LOST", 1, "title", "", 1, "", itemStatus = "OPEN", createdAt = "")
    private val deletion = CompletableDeferred<Response<ApiResponse<Unit>>>()
    private data class ListCall(
        val type: String,
        val page: Int,
        val result: CompletableDeferred<Response<ApiResponse<AdminPage<AdminPost>>>> = CompletableDeferred(),
    )
    private val calls = mutableListOf<ListCall>()
    private val deletes = mutableListOf<Pair<String, Long>>()
    private val api = object : AdminApiService by mock(AdminApiService::class.java) {
        override suspend fun posts(postType: String, page: Int, size: Int) =
            ListCall(postType, page).also(calls::add).result.await()

        override suspend fun deletePost(postType: String, id: Long): Response<ApiResponse<Unit>> {
            deletes += postType to id
            return deletion.await()
        }
    }
    private val vm by lazy { AdminViewModel(api) }
    private var callbacks = 0

    @After fun closeModel() { vm.viewModelScope.cancel() }

    private fun complete(index: Int, items: List<AdminPost>, hasNext: Boolean = false) {
        calls[index].result.complete(ok(AdminPage(items, calls[index].page, 20, hasNext)))
        dispatch()
    }

    private fun loadInitial() {
        vm.loadPosts("LOST")
        dispatch()
        complete(0, listOf(post, post.copy(id = 2)), hasNext = true)
    }

    private fun delete() {
        vm.deletePost(post) {
            assertFalse(vm.posts.any { it.postType == post.postType && it.id == post.id })
            callbacks++
        }
        dispatch()
    }

    private fun succeed() { deletion.complete(ok(Unit)); dispatch() }

    @Test fun successRemovesPostImmediatelyAndRefreshesFirstPage() {
        loadInitial()
        delete()
        assertEquals(listOf(post, post.copy(id = 2)), vm.posts)
        succeed()
        assertEquals(listOf("LOST" to 1L), deletes)
        assertEquals(1, callbacks)
        assertTrue(vm.posts.isEmpty())
        assertEquals("LOST" to 0, calls.last().let { it.type to it.page })
        assertEquals(AdminUiState.Loading, vm.state(Section.Posts))
        complete(1, listOf(post.copy(id = 2)))
        assertEquals(listOf(2L), vm.posts.map { it.id })
    }

    @Test fun listStartedDuringDeletionCannotRestorePostAfterRefreshCompletes() {
        loadInitial()
        delete()
        vm.loadPosts("LOST")
        dispatch()
        succeed()
        complete(2, listOf(post.copy(id = 2)))
        complete(1, listOf(post), hasNext = true)
        assertEquals(listOf(2L), vm.posts.map { it.id })
        assertFalse(vm.hasNext(Section.Posts))
        assertEquals(AdminUiState.Idle, vm.state(Section.Posts))
    }

    @Test fun pendingPaginationCannotRestorePostOrCursorWhileRefreshIsLoading() {
        loadInitial()
        vm.loadPosts("LOST", more = true)
        dispatch()
        delete()
        succeed()
        complete(1, listOf(post), hasNext = true)
        assertTrue(vm.posts.isEmpty())
        assertFalse(vm.hasNext(Section.Posts))
        assertEquals(AdminUiState.Loading, vm.state(Section.Posts))
        complete(2, listOf(post.copy(id = 2)))
        assertEquals(listOf(2L), vm.posts.map { it.id })
    }

    @Test fun failurePreservesPostsAndPaginationWithoutCallbackOrRefresh() {
        loadInitial()
        delete()
        deletion.complete(Response.success(ApiResponse(false, "denied", null)))
        dispatch()
        assertEquals(0, callbacks)
        assertEquals(1, calls.size)
        assertEquals(listOf(post, post.copy(id = 2)), vm.posts)
        assertTrue(vm.hasNext(Section.Posts))
        assertTrue(vm.state(Section.Delete) is AdminUiState.Error)
    }

    @Test fun deletionRefreshesFilterSelectedDuringRequest() {
        loadInitial()
        delete()
        vm.loadPosts("FOUND")
        dispatch()
        succeed()
        assertEquals(listOf("LOST", "FOUND", "FOUND"), calls.map { it.type })
        complete(1, listOf(post.copy(postType = "FOUND")))
        assertTrue(vm.posts.isEmpty())
        complete(2, listOf(post.copy(postType = "FOUND", id = 3)), hasNext = true)
        vm.loadPosts("FOUND", more = true)
        dispatch()
        assertEquals("FOUND" to 1, calls.last().let { it.type to it.page })
        complete(3, listOf(post.copy(postType = "FOUND", id = 4)))
        assertEquals(listOf(3L, 4L), vm.posts.map { it.id })
    }

    @Test fun refreshResetsPageAndKeepsDeduplicationAndDuplicateLoadGuard() {
        loadInitial()
        vm.loadPosts("LOST", more = true)
        dispatch()
        complete(1, listOf(post.copy(id = 3)), hasNext = true)
        delete()
        succeed()
        assertEquals(0, calls[2].page)
        complete(2, listOf(post.copy(id = 2)), hasNext = true)
        repeat(2) { vm.loadPosts("LOST", more = true) }
        dispatch()
        assertEquals(listOf(0, 1, 0, 1), calls.map { it.page })
        complete(3, listOf(post.copy(id = 2), post.copy(id = 4)))
        assertEquals(listOf(2L, 4L), vm.posts.map { it.id })
        vm.loadPosts("LOST", more = true)
        dispatch()
        assertEquals(4, calls.size)
    }

    @Test fun sessionChangeRejectsDeletionSuccessAndOldList() {
        loadInitial()
        delete()
        vm.loadPosts("LOST")
        dispatch()
        login("b")
        succeed()
        complete(1, listOf(post))
        assertEquals(0, callbacks)
        assertEquals(2, calls.size)
        assertTrue(vm.posts.isEmpty())
    }

    @Test fun clearSessionRejectsDeletionAndResetsSelectedFilter() {
        loadInitial()
        delete()
        vm.clearSession()
        succeed()
        assertEquals(0, callbacks)
        assertEquals(1, calls.size)
        assertTrue(vm.posts.isEmpty())
        assertEquals(AdminUiState.Idle, vm.state(Section.Posts))
    }

    @Test fun failedRefreshKeepsDeletedPostAbsentAndSupportsRetry() {
        loadInitial()
        delete()
        succeed()
        calls[1].result.complete(Response.success(ApiResponse(false, "failed", null)))
        dispatch()
        assertEquals(1, callbacks)
        assertTrue(vm.posts.isEmpty())
        assertTrue(vm.state(Section.Posts) is AdminUiState.Error)
        vm.loadPosts("LOST")
        dispatch()
        complete(2, listOf(post.copy(id = 2)))
        assertEquals(listOf(2L), vm.posts.map { it.id })
    }
}
