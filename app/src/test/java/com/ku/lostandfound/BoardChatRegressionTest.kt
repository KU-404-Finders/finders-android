package com.ku.lostandfound

import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.chat.viewmodel.ChatViewModel
import com.ku.lostandfound.ui.found.viewmodel.*
import com.ku.lostandfound.ui.lost.viewmodel.*
import com.ku.lostandfound.ui.post.viewmodel.PostCommentViewModel
import kotlinx.coroutines.CompletableDeferred
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import retrofit2.Response

class BoardChatRegressionTest : StabilityFixture() {
    @Test fun chatBadgeRefreshesOnPushAndClearsWithSession() {
        var calls = 0
        val api = object : ChatApiService by mock(ChatApiService::class.java) {
            override suspend fun getRooms(page: Int): Response<ApiResponse<List<ChatRoomResponse>>> {
                calls++
                return ok(listOf(
                    ChatRoomResponse(1, 3, 4, "", unreadMessageCount = if (calls == 1) 2 else 0),
                    ChatRoomResponse(2, 3, 4, "", unreadMessageCount = 5),
                    ChatRoomResponse(3, 3, 4, "", unreadMessageCount = null),
                ))
            }
        }
        val vm = ChatViewModel(api, mock(LostItemApiService::class.java), mock(FoundItemApiService::class.java))
        vm.loadRooms()
        dispatch()
        assertEquals(7, vm.unreadMessageTotal)
        com.ku.lostandfound.notification.NotificationInboxRefreshBus.notifyArrival()
        dispatch()
        assertEquals(2, calls)
        assertEquals(5, vm.unreadMessageTotal)
        vm.clearSession()
        assertEquals(0, vm.unreadMessageTotal)
        vm.viewModelScope.cancel()
    }

    @Test fun staleLostListCannotReplaceLatestResultOrError() {
        val responses = List(3) { CompletableDeferred<LostItemListResponse>() }
        var index = 0
        val api = object : LostItemApiService by mock(LostItemApiService::class.java) {
            override suspend fun getLostItems() = Response.success(responses[index++].await())
        }
        val vm = LostItemViewModel(api)
        repeat(3) { vm.loadLostItems() }
        dispatch()
        val post = LostItemSummaryData(4, "wallet", "", null, "SEARCHING", "")
        responses[2].complete(LostItemListResponse(true, "", listOf(post)))
        dispatch()
        responses[0].complete(LostItemListResponse(false, "old failure", null))
        responses[1].complete(LostItemListResponse(true, "", emptyList()))
        dispatch()
        assertEquals(listOf(post), vm.lostItems)
        assertEquals(LostItemListUiState.Idle, vm.listState)
        assertEquals(1, vm.listVersion)
    }

    @Test fun reselectBuildingAfterWholeCampusFailedWhileSwitching() {
        val calls = mutableListOf<String?>()
        val delayed = CompletableDeferred<FoundItemListResponse>()
        val api = object : FoundItemApiService by mock(FoundItemApiService::class.java) {
            override suspend fun getFoundItems(buildingName: String?): Response<FoundItemListResponse> {
                calls += buildingName
                return Response.success(when (buildingName) {
                    null -> FoundItemListResponse(false, "offline", null)
                    "B" -> delayed.await()
                    else -> FoundItemListResponse(true, "", emptyList())
                })
            }
        }
        val vm = FoundItemViewModel(api)
        vm.loadFoundItemsByBuilding("A")
        dispatch()
        vm.loadFoundItemsByBuilding("B")
        dispatch()
        vm.clearBuildingFilter()
        dispatch()
        assertTrue(vm.listState is FoundItemUiState.Error)
        vm.loadFoundItemsByBuilding("A")
        dispatch()
        delayed.complete(FoundItemListResponse(false, "old", null))
        dispatch()
        assertEquals(listOf("A", "B", null, "A"), calls)
        assertEquals("A", vm.selectedBuildingName)
        assertEquals(FoundItemUiState.Idle, vm.listState)
    }

    @Test fun concurrentLeavesRejectOldListAndAllowServerReappearance() {
        val leaves = mutableMapOf<Long, CompletableDeferred<Unit>>()
        val lists = mutableListOf<CompletableDeferred<List<ChatRoomResponse>>>()
        val api = object : ChatApiService by mock(ChatApiService::class.java) {
            override suspend fun getRooms(page: Int) = ok(CompletableDeferred<List<ChatRoomResponse>>().also(lists::add).await())
            override suspend fun leaveRoom(roomId: Long): Response<ApiResponse<Unit>> {
                check(roomId !in leaves)
                return ok(CompletableDeferred<Unit>().also { leaves[roomId] = it }.await())
            }
        }
        val vm = ChatViewModel(api, mock(LostItemApiService::class.java), mock(FoundItemApiService::class.java))
        val rooms = listOf(1L, 2L).map { ChatRoomResponse(it, 3, 4, "") }
        vm.loadRooms()
        dispatch()
        lists.single().complete(rooms)
        dispatch()
        vm.loadRooms()
        val completed = mutableListOf<Long>()
        for (id in listOf(1L, 2L, 1L)) vm.leaveRoom(id) { if (it) completed += id }
        dispatch()
        for (id in listOf(2L, 1L)) { leaves.getValue(id).complete(Unit); dispatch() }
        lists[1].complete(rooms)
        lists[2].complete(rooms)
        dispatch()
        assertTrue(vm.rooms.isEmpty())
        assertEquals(listOf(2L, 1L), completed)
        lists.last().complete(rooms.take(1))
        dispatch()
        assertEquals(rooms.take(1), vm.rooms)
    }

    @Test fun failedAuthorLookupCanRetryAndSuccessfulValueIsCached() {
        var calls = 0
        val first = CompletableDeferred<LostItemCreateResponse>()
        val detail = LostItemData(3, 5, "writer", null, "post", "", "", null, "SEARCHING", emptyList(), emptyList(), "", "")
        val lost = object : LostItemApiService by mock(LostItemApiService::class.java) {
            override suspend fun getLostItemDetail(id: Long) = Response.success(
                if (++calls == 1) first.await() else LostItemCreateResponse(true, "", detail)
            )
        }
        val vm = ChatViewModel(mock(ChatApiService::class.java), lost, mock(FoundItemApiService::class.java))
        val room = ChatRoomResponse(1, 2, 3, "", 3, "LOST")
        repeat(2) { vm.loadPostAuthorForRoom(room) }
        dispatch()
        first.complete(LostItemCreateResponse(false, "", null))
        dispatch()
        repeat(2) { vm.loadPostAuthorForRoom(room); dispatch() }
        assertEquals(2, calls)
        assertEquals(5L to "writer", vm.authorInfoByRoomId[1L])
    }

    @Test fun oldCommentCannotInvokeCallbackOrReleaseCurrentSubmission() {
        val responses = mutableListOf<CompletableDeferred<ItemCommentResponse>>()
        val lost = object : LostItemApiService by mock(LostItemApiService::class.java) {
            override suspend fun createLostItemComment(authorization: String, lostItemId: Long, request: CommentCreateRequest) =
                ok(CompletableDeferred<ItemCommentResponse>().also(responses::add).await())
        }
        val vm = PostCommentViewModel(lost, mock(FoundItemApiService::class.java))
        vm.addComment(1, PostType.LOST, "old", { fail() }, { fail() })
        dispatch()
        vm.clear()
        login("b")
        vm.addComment(1, PostType.LOST, "new")
        dispatch()
        responses.first().complete(ItemCommentResponse(1, 1, "old", "old", ""))
        dispatch()
        assertTrue(vm.isAddingComment)
        assertTrue(vm.commentsByPostId.isEmpty())
        responses.last().complete(ItemCommentResponse(2, 2, "new", "new", ""))
        dispatch()
        assertFalse(vm.isAddingComment)
        assertEquals("new", vm.commentsFor(1, PostType.LOST).single().content)
    }
}
