package com.ku.lostandfound

import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.found.viewmodel.FoundItemViewModel
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import retrofit2.Response

class FoundUnblockRefreshRegressionTest : StabilityFixture() {
    private val models = mutableListOf<FoundItemViewModel>()
    @After fun close() { models.forEach { it.viewModelScope.cancel() } }
    private inner class Found {
        val calls = mutableListOf<String?>()
        val api = object : FoundItemApiService by mock(FoundItemApiService::class.java) {
            override suspend fun getFoundItems(buildingName: String?): Response<FoundItemListResponse> {
                calls += buildingName
                val row = FoundItemSummaryData(calls.size.toLong(), "item", "", null, ItemStatus.SEARCHING,
                    FoundItemLocationType.INDOOR, emptyList(), "")
                return Response.success(FoundItemListResponse(true, "", listOf(row)))
            }
        }
        val vm = FoundItemViewModel(api).also(models::add)
    }
    @Test fun unblockRefreshBypassesWholeCampusCache() {
        val h = Found(); h.vm.loadFoundItems(); dispatch()
        h.vm.loadFoundItems(); dispatch()
        assertEquals(listOf<String?>(null), h.calls)
        h.vm.retryFoundItems(); dispatch()
        assertEquals(listOf<String?>(null, null), h.calls)
        assertEquals(2L, h.vm.foundItems.single().id)
    }
    @Test fun unblockRefreshBypassesCacheAndPreservesBuildingFilter() {
        val h = Found(); h.vm.loadFoundItemsByBuilding("공학관"); dispatch()
        h.vm.retryFoundItems(); dispatch()
        assertEquals(listOf("공학관", "공학관"), h.calls)
        assertEquals("공학관", h.vm.selectedBuildingName)
        assertEquals("공학관", h.vm.successfulList?.buildingName)
        assertEquals(2L, h.vm.foundItems.single().id)
    }
    @Test fun unblockRefreshAlsoLoadsWithoutCache() {
        val h = Found(); h.vm.retryFoundItems(); dispatch()
        assertEquals(listOf<String?>(null), h.calls)
        assertEquals(1L, h.vm.foundItems.single().id)
    }
}
