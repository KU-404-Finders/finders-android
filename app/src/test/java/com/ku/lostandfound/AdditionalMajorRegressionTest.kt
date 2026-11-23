package com.ku.lostandfound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.chat.viewmodel.ChatViewModel
import com.ku.lostandfound.ui.found.viewmodel.*
import com.ku.lostandfound.ui.navigation.popIfCurrent
import com.ku.lostandfound.ui.post.viewmodel.PostCommentViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import retrofit2.Response

class AdditionalMajorRegressionTest : StabilityFixture() {
    private val models = mutableListOf<ViewModel>()
    private fun <T : ViewModel> keep(vm: T): T = vm.also { models += it }
    @After fun closeModels() { models.forEach { it.viewModelScope.cancel() } }

    @Test fun duplicateLeaveNotifiesListAndDetailButOnlyPopsTheCurrentEntry() {
        val result = CompletableDeferred<Boolean>()
        var calls = 0
        val api = object : ChatApiService by mock(ChatApiService::class.java) {
            override suspend fun leaveRoom(roomId: Long): Response<ApiResponse<Unit>> {
                calls++
                return Response.success(ApiResponse(result.await(), "", Unit))
            }
            override suspend fun getRooms(page: Int) = ok(emptyList<ChatRoomResponse>())
        }
        val vm = keep(ChatViewModel(api, mock(LostItemApiService::class.java), mock(FoundItemApiService::class.java)))
        val nav = mock(NavController::class.java)
        val list = mock(NavBackStackEntry::class.java)
        val detail = mock(NavBackStackEntry::class.java)
        `when`(nav.currentBackStackEntry).thenReturn(detail)
        val completed = mutableListOf<Boolean>()
        vm.leaveRoom(1) { completed += it; if (it) nav.popIfCurrent(list) }
        dispatch()
        vm.leaveRoom(1) { completed += it; if (it) nav.popIfCurrent(detail) }
        dispatch()
        assertEquals(1, calls)
        result.complete(true)
        dispatch()
        assertEquals(listOf(true, true), completed)
        verify(nav, times(1)).popBackStack()
    }

    @Test fun duplicateLeaveFailureReachesEveryCallerAndCanRetry() {
        var calls = 0
        val result = CompletableDeferred<Boolean>()
        val api = object : ChatApiService by mock(ChatApiService::class.java) {
            override suspend fun leaveRoom(roomId: Long): Response<ApiResponse<Unit>> {
                calls++
                return Response.success(ApiResponse(result.await(), "", Unit))
            }
        }
        val vm = keep(ChatViewModel(api, mock(LostItemApiService::class.java), mock(FoundItemApiService::class.java)))
        val completed = mutableListOf<Boolean>()
        repeat(2) { vm.leaveRoom(1) { completed += it } }
        dispatch()
        result.complete(false)
        dispatch()
        assertEquals(1, calls)
        assertEquals(listOf(false, false), completed)
        vm.leaveRoom(1) { completed += it }
        dispatch()
        assertEquals(2, calls)
    }

    @Test fun previousSessionLeaveCannotCompleteNewSessionsSameRoomRequest() {
        val results = mutableListOf<CompletableDeferred<Boolean>>()
        val api = object : ChatApiService by mock(ChatApiService::class.java) {
            override suspend fun leaveRoom(roomId: Long) = Response.success(ApiResponse(
                CompletableDeferred<Boolean>().also(results::add).await(), "", Unit))
        }
        val vm = keep(ChatViewModel(api, mock(LostItemApiService::class.java), mock(FoundItemApiService::class.java)))
        repeat(2) { vm.leaveRoom(1) { fail("Old callback") } }
        dispatch()
        vm.clearSession()
        login("b")
        val completed = mutableListOf<Boolean>()
        repeat(2) { vm.leaveRoom(1) { completed += it } }
        dispatch()
        results[0].complete(true)
        dispatch()
        assertTrue(completed.isEmpty())
        results[1].complete(false)
        dispatch()
        assertEquals(listOf(false, false), completed)
    }

    private fun found(id: Long) = FoundItemSummaryData(id, "item", "", null, ItemStatus.SEARCHING,
        FoundItemLocationType.INDOOR, listOf("A"), "")

    @Test fun filterTransitionAndFailedWholeCampusLoadKeepSuccessfulScopeDataAndVersionTogether() {
        val pending = mutableListOf<Pair<String?, CompletableDeferred<FoundItemListResponse>>>()
        val api = object : FoundItemApiService by mock(FoundItemApiService::class.java) {
            override suspend fun getFoundItems(buildingName: String?) = Response.success(
                CompletableDeferred<FoundItemListResponse>().also { pending += buildingName to it }.await())
        }
        val vm = keep(FoundItemViewModel(api))
        vm.loadFoundItemsByBuilding("A")
        dispatch()
        pending.last().second.complete(FoundItemListResponse(true, "", listOf(found(1))))
        dispatch()
        val snapshot = vm.successfulList!!
        vm.clearBuildingFilter()
        dispatch()
        assertTrue(vm.foundItems.isEmpty())
        assertSame(snapshot, vm.successfulList)
        assertEquals("A", snapshot.buildingName)
        assertEquals(listOf(1L), snapshot.items.map { it.id })
        pending.last().second.complete(FoundItemListResponse(false, "offline", null))
        dispatch()
        assertSame(snapshot, vm.successfulList)
        assertEquals(1, vm.listVersion)
        vm.retryFoundItems()
        dispatch()
        pending.last().second.complete(FoundItemListResponse(true, "", listOf(found(2))))
        dispatch()
        assertNull(vm.successfulList!!.buildingName)
        assertEquals(listOf(2L), vm.successfulList!!.items.map { it.id })
        assertEquals(2, vm.listVersion)
        vm.loadFoundItemsByBuilding("B")
        dispatch()
        val old = pending.last().second
        vm.clearSession()
        login("b")
        old.complete(FoundItemListResponse(true, "", listOf(found(3))))
        dispatch()
        assertNull(vm.successfulList)
        assertEquals(0, vm.listVersion)
    }

    private fun comment(id: Long, text: String) = ItemCommentResponse(id, 1, "writer", text, "")
    private inner class Comments {
        val loads = mutableListOf<CompletableDeferred<List<ItemCommentResponse>>>()
        val adds = mutableListOf<CompletableDeferred<ItemCommentResponse>>()
        val edits = mutableListOf<Pair<String, CompletableDeferred<ApiResponse<ItemCommentResponse>>>>()
        val api = object : LostItemApiService by mock(LostItemApiService::class.java) {
            override suspend fun getLostItemComments(lostItemId: Long) = ok(CompletableDeferred<List<ItemCommentResponse>>().also(loads::add).await())
            override suspend fun createLostItemComment(authorization: String, lostItemId: Long, request: CommentCreateRequest) =
                ok(CompletableDeferred<ItemCommentResponse>().also(adds::add).await())
            override suspend fun updateLostItemComment(authorization: String, lostItemId: Long, commentId: Long, request: CommentUpdateRequest) =
                Response.success(CompletableDeferred<ApiResponse<ItemCommentResponse>>().also { edits += request.content to it }.await())
        }
        val vm = keep(PostCommentViewModel(api, mock(FoundItemApiService::class.java)))
        fun completeInitial() {
            vm.loadComments(1, PostType.LOST)
            dispatch()
            loads.last().complete(listOf(comment(1, "original")))
            dispatch()
        }
    }

    @Test fun addDuringInitialLoadRefetchesAndPreservesA_B_AndCDespiteLateOldResponse() {
        val h = Comments()
        h.vm.loadComments(1, PostType.LOST)
        dispatch()
        h.vm.addComment(1, PostType.LOST, "C")
        dispatch()
        h.adds.single().complete(comment(3, "C"))
        dispatch()
        assertEquals(2, h.loads.size)
        assertEquals(listOf("C"), h.vm.commentsFor(1, PostType.LOST).map { it.content })
        h.loads[0].complete(listOf(comment(1, "stale A"), comment(2, "stale B")))
        dispatch()
        assertEquals(listOf("C"), h.vm.commentsFor(1, PostType.LOST).map { it.content })
        h.loads[1].complete(listOf(comment(1, "A"), comment(2, "B")))
        dispatch()
        assertEquals(listOf("A", "B", "C"), h.vm.commentsFor(1, PostType.LOST).map { it.content })
    }

    @Test fun previousSessionRefetchCannotPopulateNewAccountAfterAdd() {
        val h = Comments()
        h.vm.addComment(1, PostType.LOST, "C")
        dispatch()
        h.adds.single().complete(comment(3, "C"))
        dispatch()
        h.vm.clear()
        login("b")
        h.loads.single().complete(listOf(comment(1, "A"), comment(3, "C")))
        dispatch()
        assertTrue(h.vm.commentsByPostId.isEmpty())
    }

    private fun editsInOrder(secondSucceeds: Boolean) {
        val h = Comments()
        h.completeInitial()
        h.vm.updateComment(1, PostType.LOST, 1, "A")
        h.vm.updateComment(1, PostType.LOST, 1, "B")
        dispatch()
        assertEquals(listOf("A"), h.edits.map { it.first })
        h.edits[0].second.complete(ApiResponse(true, "", comment(1, "A")))
        dispatch()
        assertEquals(listOf("A", "B"), h.edits.map { it.first })
        assertEquals("A", h.vm.commentsFor(1, PostType.LOST).single().content)
        h.edits[1].second.complete(ApiResponse(secondSucceeds, "failure", if (secondSucceeds) comment(1, "B") else null))
        dispatch()
        assertEquals(if (secondSucceeds) "B" else "A", h.vm.commentsFor(1, PostType.LOST).single().content)
    }

    @Test fun editASuccessThenBFailureKeepsA() = editsInOrder(false)
    @Test fun editASuccessThenBSuccessShowsB() = editsInOrder(true)

    @Test fun oldEditAndQueuedEditCannotTouchNewAccount() {
        val h = Comments()
        h.completeInitial()
        h.vm.updateComment(1, PostType.LOST, 1, "A")
        h.vm.updateComment(1, PostType.LOST, 1, "B")
        dispatch()
        h.vm.clear()
        login("b")
        h.completeInitial()
        h.edits.single().second.complete(ApiResponse(true, "", comment(1, "A")))
        dispatch()
        assertEquals(listOf("A"), h.edits.map { it.first })
        assertEquals("original", h.vm.commentsFor(1, PostType.LOST).single().content)
    }
}
