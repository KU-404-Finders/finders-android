package com.ku.lostandfound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.chat.viewmodel.ChatViewModel
import com.ku.lostandfound.ui.found.viewmodel.*
import com.ku.lostandfound.ui.lost.viewmodel.*
import com.ku.lostandfound.ui.component.thumbnailSampleSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import retrofit2.Response

class UiPerformanceRegressionTest : StabilityFixture() {
    private var clock = 0L
    private val models = mutableListOf<ViewModel>()
    private fun <T : ViewModel> keep(vm: T) = vm.also { models += it }
    @After fun close() { models.forEach { it.viewModelScope.cancel() } }

    private inner class Lost {
        val pending = mutableListOf<CompletableDeferred<LostItemListResponse>>()
        val api = object : LostItemApiService by mock(LostItemApiService::class.java) {
            override suspend fun getLostItems() = Response.success(
                CompletableDeferred<LostItemListResponse>().also(pending::add).await())
            override suspend fun deleteLostItem(authorization: String, id: Long) = Response.success(ApiMessageResponse(true, ""))
        }
        val vm = keep(LostItemViewModel(api) { clock })
        fun complete(index: Int, id: Long = 1, success: Boolean = true) {
            pending[index].complete(LostItemListResponse(success, "offline", listOf(
                LostItemSummaryData(id, "item", "", null, "SEARCHING", ""))))
            dispatch()
        }
    }
    private inner class Found {
        val filters = mutableListOf<String?>()
        val pending = mutableListOf<CompletableDeferred<FoundItemListResponse>>()
        val api = object : FoundItemApiService by mock(FoundItemApiService::class.java) {
            override suspend fun deleteFoundItem(authorization: String, id: Long) = Response.success(ApiMessageResponse(true, ""))
            override suspend fun getFoundItems(buildingName: String?): Response<FoundItemListResponse> {
                filters += buildingName
                return Response.success(CompletableDeferred<FoundItemListResponse>().also(pending::add).await())
            }
        }
        val vm = keep(FoundItemViewModel(api) { clock })
        fun complete(index: Int, id: Long = 1, success: Boolean = true) {
            pending[index].complete(FoundItemListResponse(success, "offline", listOf(
                FoundItemSummaryData(id, "item", "", null, ItemStatus.SEARCHING, FoundItemLocationType.INDOOR, emptyList(), ""))))
            dispatch()
        }
    }

    @Test fun lostTabEntriesCoalesceBeforeDispatchAndExpireAfterThirtySeconds() {
        val h = Lost()
        repeat(10) { h.vm.ensureLoaded() }
        dispatch()
        assertEquals(1, h.pending.size)
        h.complete(0)
        repeat(10) { h.vm.ensureLoaded(); dispatch() }
        assertEquals(1, h.pending.size)
        clock = 30_000
        h.vm.ensureLoaded(); dispatch()
        assertEquals(2, h.pending.size)
    }
    @Test fun foundTabEntriesCoalesceAndExpireEvenForEmptySuccessfulList() {
        val h = Found()
        repeat(10) { h.vm.loadFoundItems() }
        dispatch()
        assertEquals(1, h.pending.size)
        h.pending[0].complete(FoundItemListResponse(true, "", emptyList())); dispatch()
        repeat(10) { h.vm.loadFoundItems(); dispatch() }
        assertEquals(1, h.pending.size)
        clock = 30_000
        h.vm.loadFoundItems(); dispatch()
        assertEquals(2, h.pending.size)
    }
    @Test fun lostRepeatedRefreshRetainsRowsAndFailureCanBeRetried() {
        val h = Lost(); h.vm.ensureLoaded(); dispatch(); h.complete(0)
        repeat(10) { h.vm.refreshLostItems() }; dispatch()
        assertEquals(2, h.pending.size)
        assertEquals(1L, h.vm.lostItems.single().id)
        h.complete(1, success = false)
        assertTrue(h.vm.listState is LostItemListUiState.Error)
        assertEquals(1L, h.vm.lostItems.single().id)
        h.vm.refreshLostItems(); dispatch(); h.complete(2, 2)
        assertEquals(2L, h.vm.lostItems.single().id)
    }
    @Test fun foundRepeatedRefreshRetainsBuildingFilterAndRowsOnFailure() {
        val h = Found(); h.vm.loadFoundItemsByBuilding("공학관"); dispatch(); h.complete(0)
        repeat(10) { h.vm.refreshFoundItems() }; dispatch()
        assertEquals(listOf("공학관", "공학관"), h.filters)
        assertEquals(1L, h.vm.foundItems.single().id)
        h.complete(1, success = false)
        assertTrue(h.vm.listState is FoundItemUiState.Error)
        assertEquals(1L, h.vm.foundItems.single().id)
        h.vm.refreshFoundItems(); dispatch(); h.complete(2, 2)
        assertEquals(2L, h.vm.foundItems.single().id)
    }
    @Test fun forcedFoundInvalidationSupersedesAlreadyRunningGesture() {
        val h = Found(); h.vm.loadFoundItems(); dispatch(); h.complete(0)
        h.vm.refreshFoundItems(); dispatch()
        h.vm.retryFoundItems(); dispatch()
        assertEquals(3, h.pending.size)
        h.complete(2, 3); h.complete(1, 2)
        assertEquals(3L, h.vm.foundItems.single().id)
    }
    @Test fun forcedLostInvalidationSupersedesAlreadyRunningGesture() {
        val h = Lost(); h.vm.ensureLoaded(); dispatch(); h.complete(0)
        h.vm.refreshLostItems(); dispatch()
        h.vm.loadLostItems(); dispatch()
        assertEquals(3, h.pending.size)
        h.complete(2, 3); h.complete(1, 2)
        assertEquals(3L, h.vm.lostItems.single().id)
    }
    @Test fun boardCachesAndPendingResponsesCannotCrossAccounts() {
        val l = Lost(); val f = Found()
        l.vm.ensureLoaded(); f.vm.loadFoundItems(); dispatch()
        l.vm.clearSession(); f.vm.clearSession(); login("b")
        l.vm.ensureLoaded(); f.vm.loadFoundItems(); dispatch()
        l.complete(1, 2); f.complete(1, 2)
        l.complete(0, 1); f.complete(0, 1)
        assertEquals(2L, l.vm.lostItems.single().id)
        assertEquals(2L, f.vm.foundItems.single().id)
    }
    @Test fun accountSwitchWithoutExplicitCacheClearStillReloadsBothBoards() {
        val l = Lost(); val f = Found()
        l.vm.ensureLoaded(); f.vm.loadFoundItems(); dispatch()
        l.complete(0); f.complete(0)
        login("b")
        l.vm.ensureLoaded(); f.vm.loadFoundItems(); dispatch()
        assertTrue(l.vm.lostItems.isEmpty())
        assertTrue(f.vm.foundItems.isEmpty())
        assertEquals(2, l.pending.size)
        assertEquals(2, f.pending.size)
    }
    @Test fun refreshResponsesStartedBeforeDeletionCannotResurrectDeletedPosts() {
        val l = Lost(); val f = Found()
        l.vm.ensureLoaded(); f.vm.loadFoundItems(); dispatch()
        l.complete(0); f.complete(0)
        l.vm.refreshLostItems(); f.vm.refreshFoundItems(); dispatch()
        l.vm.deleteLostItem(1) {}; f.vm.deleteFoundItem(1) {}; dispatch()
        l.complete(1); f.complete(1)
        assertTrue(l.vm.lostItems.isEmpty())
        assertTrue(f.vm.foundItems.isEmpty())
        assertEquals(LostItemListUiState.Idle, l.vm.listState)
        assertEquals(FoundItemUiState.Idle, f.vm.listState)
    }
    @Test fun chatEntryCoalescesOverlapButAlwaysRechecksServerUnreadCounts() {
        var calls = 0
        val api = object : ChatApiService by mock(ChatApiService::class.java) {
            override suspend fun getRooms(page: Int): Response<ApiResponse<List<ChatRoomResponse>>> {
                calls++
                return ok(listOf(ChatRoomResponse(1, 2, 3, "", unreadMessageCount = calls)))
            }
        }
        val vm = keep(ChatViewModel(api, mock(LostItemApiService::class.java), mock(FoundItemApiService::class.java)))
        repeat(10) { vm.ensureRoomsLoaded() }; dispatch()
        assertEquals(1, calls)
        vm.ensureRoomsLoaded(); dispatch()
        assertEquals(2, calls)
        com.ku.lostandfound.notification.NotificationInboxRefreshBus.notifyArrival(); dispatch()
        assertEquals(3, calls)
        assertEquals(3, vm.unreadMessageTotal)
        vm.clearSession(); login("b"); vm.ensureRoomsLoaded(); dispatch()
        assertEquals(4, calls)
    }
    @Test fun thumbnailDecodeLimitsLargeSourcesWithoutUpscalingSmallImages() {
        assertEquals(1, thumbnailSampleSize(320, 240))
        assertEquals(8, thumbnailSampleSize(4000, 3000))
        assertEquals(8, thumbnailSampleSize(3000, 4000))
        assertEquals(1, thumbnailSampleSize(-1, -1))
    }
}
