package com.ku.lostandfound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.found.viewmodel.*
import com.ku.lostandfound.ui.lost.viewmodel.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import retrofit2.Response

class StatusChangeRefreshRegressionTest : StabilityFixture() {
    private val models = mutableListOf<ViewModel>()
    @After fun close() { models.forEach { it.viewModelScope.cancel() } }

    private fun foundRow(id: Long, status: ItemStatus = ItemStatus.SEARCHING, building: String = "A") = FoundItemSummaryData(
        id, "item", "", null, status, FoundItemLocationType.INDOOR, listOf(building), "")
    private fun lostRow(id: Long, status: String = "SEARCHING") = LostItemSummaryData(id, "item", "", null, status, "")
    private fun foundDetail() = FoundItemDetailData(1, 2, title = "item", kind = "", content = "", imageUrl = null,
        itemStatus = ItemStatus.RETURNED, locationType = FoundItemLocationType.INDOOR, buildingName = "A", floor = 1,
        latitude = null, longitude = null, associatedBuildingNames = listOf("A"), createdAt = "", updatedAt = "")
    private fun lostDetail() = LostItemData(1, 2, title = "item", kind = "", content = "", imageUrl = null,
        itemStatus = "RETURNED", indoorSpots = emptyList(), outdoorPoints = emptyList(), createdAt = "", updatedAt = "")

    private inner class Found {
        val filters = mutableListOf<String?>()
        val lists = mutableListOf<CompletableDeferred<FoundItemListResponse>>()
        val status = CompletableDeferred<FoundItemDetailResponse>()
        val api = object : FoundItemApiService by mock(FoundItemApiService::class.java) {
            override suspend fun getFoundItems(buildingName: String?): Response<FoundItemListResponse> {
                filters += buildingName
                return Response.success(CompletableDeferred<FoundItemListResponse>().also(lists::add).await())
            }
            override suspend fun updateFoundItemStatus(authorization: String, id: Long) = Response.success(status.await())
        }
        val vm = FoundItemViewModel(api) { 0L }.also(models::add)
        var callbacks = 0
        fun changeStatus() {
            vm.updateFoundItemStatus(1) { callbacks++; vm.retryFoundItems() }
            dispatch()
        }
        fun finishStatus(success: Boolean = true) {
            status.complete(FoundItemDetailResponse(success, "status", if (success) foundDetail() else null)); dispatch()
        }
        fun finishList(index: Int, vararg rows: FoundItemSummaryData) {
            lists[index].complete(FoundItemListResponse(true, "", rows.toList())); dispatch()
        }
    }
    private inner class Lost {
        val lists = mutableListOf<CompletableDeferred<LostItemListResponse>>()
        val status = CompletableDeferred<LostItemCreateResponse>()
        val api = object : LostItemApiService by mock(LostItemApiService::class.java) {
            override suspend fun getLostItems() = Response.success(CompletableDeferred<LostItemListResponse>().also(lists::add).await())
            override suspend fun markLostItemReturned(authorization: String, id: Long) = Response.success(status.await())
        }
        val vm = LostItemViewModel(api) { 0L }.also(models::add)
        var callbacks = 0
        fun changeStatus() { vm.markReturned(1) { callbacks++; vm.loadLostItems() }; dispatch() }
        fun finishStatus(success: Boolean = true) {
            status.complete(LostItemCreateResponse(success, "status", if (success) lostDetail() else null)); dispatch()
        }
        fun finishList(index: Int, vararg rows: LostItemSummaryData) {
            lists[index].complete(LostItemListResponse(true, "", rows.toList())); dispatch()
        }
    }

    @Test fun foundStatusDuringBuildingRequestReloadsSameBuildingAndRejectsOldRows() {
        val h = Found(); h.vm.loadFoundItemsByBuilding("A"); dispatch()
        h.changeStatus(); h.finishStatus()
        assertEquals(listOf("A", "A"), h.filters)
        assertEquals("A", h.vm.selectedBuildingName)
        assertEquals(FoundItemUiState.Loading, h.vm.listState)
        h.vm.loadFoundItemsByBuilding("A"); dispatch() // Board re-entry shares the forced request.
        assertEquals(2, h.lists.size)
        h.finishList(1, foundRow(2), foundRow(1, ItemStatus.RETURNED))
        h.finishList(0, foundRow(1))
        assertEquals(listOf(2L), h.vm.foundItems.map { it.id })
        assertEquals(FoundItemUiState.Idle, h.vm.listState)
        assertEquals(1, h.callbacks)
    }
    @Test fun foundWholeCampusStatusRefreshBypassesFreshThirtySecondCache() {
        val h = Found(); h.vm.loadFoundItems(); dispatch(); h.finishList(0, foundRow(1))
        h.changeStatus(); h.finishStatus()
        assertEquals(listOf<String?>(null, null), h.filters)
        assertNull(h.vm.selectedBuildingName)
        h.finishList(1, foundRow(2), foundRow(1, ItemStatus.RETURNED))
        assertEquals(listOf(2L), h.vm.foundItems.map { it.id })
        assertEquals(1, h.callbacks)
    }
    @Test fun foundStatusRefreshUsesBuildingSelectedWhileStatusRequestWasRunning() {
        val h = Found(); h.vm.loadFoundItemsByBuilding("A"); dispatch(); h.finishList(0, foundRow(1))
        h.changeStatus()
        h.vm.loadFoundItemsByBuilding("B"); dispatch()
        h.finishStatus()
        assertEquals(listOf("A", "B", "B"), h.filters)
        h.finishList(2, foundRow(2, building = "B")); h.finishList(1, foundRow(1))
        assertEquals("B", h.vm.selectedBuildingName)
        assertEquals("B", h.vm.successfulList?.buildingName)
        assertEquals("B", h.vm.asBoardPosts().single().foundLocation?.indoorPlace?.buildingName)
        assertEquals(listOf(2L), h.vm.foundItems.map { it.id })
    }
    @Test fun lostStatusDuringListRequestReloadsAndRejectsInvalidatedResponse() {
        val h = Lost(); h.vm.ensureLoaded(); dispatch()
        h.changeStatus(); h.finishStatus()
        assertEquals(2, h.lists.size)
        assertEquals(LostItemListUiState.Loading, h.vm.listState)
        h.vm.ensureLoaded(); dispatch()
        assertEquals(2, h.lists.size)
        h.finishList(1, lostRow(2), lostRow(1, "RETURNED")); h.finishList(0, lostRow(1))
        assertEquals(listOf(2L), h.vm.lostItems.map { it.id })
        assertEquals(LostItemListUiState.Idle, h.vm.listState)
        assertEquals(1, h.callbacks)
    }
    @Test fun lostStatusRefreshBypassesFreshCache() {
        val h = Lost(); h.vm.ensureLoaded(); dispatch(); h.finishList(0, lostRow(1))
        h.changeStatus(); h.finishStatus()
        assertEquals(2, h.lists.size)
        h.finishList(1, lostRow(2))
        assertEquals(listOf(2L), h.vm.lostItems.map { it.id })
    }
    @Test fun failedStatusChangesDoNotInvalidateOrStartExtraListRequests() {
        val f = Found(); val l = Lost()
        f.vm.loadFoundItemsByBuilding("A"); l.vm.ensureLoaded(); dispatch()
        f.changeStatus(); l.changeStatus(); f.finishStatus(false); l.finishStatus(false)
        assertEquals(0, f.callbacks); assertEquals(0, l.callbacks)
        assertEquals(1, f.lists.size); assertEquals(1, l.lists.size)
        f.finishList(0, foundRow(1)); l.finishList(0, lostRow(1))
        assertEquals(1L, f.vm.foundItems.single().id)
        assertEquals(1L, l.vm.lostItems.single().id)
    }
}
