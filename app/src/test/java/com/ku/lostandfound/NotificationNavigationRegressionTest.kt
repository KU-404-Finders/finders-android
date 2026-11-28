package com.ku.lostandfound

import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ku.lostandfound.network.*
import com.ku.lostandfound.notification.NotificationTarget
import com.ku.lostandfound.ui.notification.NotificationViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import android.content.SharedPreferences
import retrofit2.Response

class NotificationNavigationRegressionTest : StabilityFixture() {
    private val models = mutableListOf<NotificationViewModel>()
    @After fun close() { models.forEach { it.viewModelScope.cancel() } }
    @Before fun preferences() {
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        val sets = mutableMapOf<String, Set<String>>()
        `when`(application.getSharedPreferences("finders_notifications", 0)).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getStringSet(anyString(), anySet())).thenAnswer { sets[it.getArgument<String>(0)].orEmpty() }
        `when`(editor.putStringSet(anyString(), anySet())).thenAnswer {
            sets[it.getArgument(0)] = it.getArgument<Set<String>>(1).toSet(); editor
        }
    }
    private fun row(type: String = "MATCH", postType: String? = "LOST", postId: Long? = 77) =
        InboxNotificationData("one", type, postType = postType, postId = postId, sequence = 100)
    private fun lostOk() = Response.success(LostItemCreateResponse(true, "", mock(LostItemData::class.java)))
    private inner class Inbox(var rows: List<InboxNotificationData> = listOf(row())) {
        var readOk = true
        var allReadOk = true
        var readGate: CompletableDeferred<Boolean>? = null
        var readCalls = 0
        val details = mutableListOf<Pair<String, Long>>()
        val posts = mutableListOf<Pair<String, Long>>()
        val rooms = mutableListOf<Long>()
        val messages = mutableListOf<String>()
        var readErrors = 0
        var lost: suspend () -> Response<LostItemCreateResponse> = { lostOk() }
        var found: suspend () -> Response<FoundItemDetailResponse> = {
            Response.success(FoundItemDetailResponse(true, "", mock(FoundItemDetailData::class.java)))
        }
        val notifications = object : NotificationApiService by mock(NotificationApiService::class.java) {
            override suspend fun notifications(limit: Int, snapshotSequence: Long?, beforeSequence: Long?) =
                ok(NotificationPageData(rows, 100, null))
            override suspend fun unreadCount() = ok(NotificationUnreadCountData(47))
            override suspend fun markRead(id: String): Response<ApiResponse<Unit>> {
                readCalls++
                return Response.success(ApiResponse(readGate?.await() ?: readOk, "", Unit))
            }
            override suspend fun markAllRead(request: MarkAllNotificationsReadRequest) =
                Response.success(ApiResponse(allReadOk, "", MarkAllNotificationsReadData()))
        }
        val lostApi = object : LostItemApiService by mock(LostItemApiService::class.java) {
            override suspend fun getLostItemDetail(id: Long, session: com.ku.lostandfound.network.RequestSession?): Response<LostItemCreateResponse> {
                details += "LOST" to id
                return lost()
            }
        }
        val foundApi = object : FoundItemApiService by mock(FoundItemApiService::class.java) {
            override suspend fun getFoundItemDetail(id: Long, session: com.ku.lostandfound.network.RequestSession?): Response<FoundItemDetailResponse> {
                details += "FOUND" to id
                return found()
            }
        }
        val vm = NotificationViewModel(application, notifications, lostApi, foundApi).also(models::add)
        init { vm.setInboxOpen(true); vm.attachAccount(1); dispatch() }
        fun click(index: Int = 0) {
            vm.openNotification(vm.items[index], { readErrors++ }, messages::add, rooms::add,
                { type, id -> posts += type to id })
            dispatch()
        }
    }

    @Test fun matchLostOpensRelativePostAfterOneRead() {
        val h = Inbox(); h.click()
        assertEquals(listOf("LOST" to 77L), h.posts)
        assertEquals(h.posts, h.details)
        assertEquals(1, h.readCalls)
    }
    @Test fun matchFoundOpensFoundDetail() {
        val h = Inbox(listOf(row(postType = "FOUND"))); h.click()
        assertEquals(listOf("FOUND" to 77L), h.posts)
        assertEquals(h.posts, h.details)
    }
    @Test fun commentStillOpensPost() {
        val h = Inbox(listOf(row(type = "COMMENT"))); h.click()
        assertEquals(listOf("LOST" to 77L), h.posts)
    }
    @Test fun chatStillOpensRoomWithoutPostLookup() {
        val h = Inbox(listOf(row(type = "CHAT").copy(roomId = 42))); h.click()
        assertEquals(listOf(42L), h.rooms)
        assertTrue(h.details.isEmpty())
    }
    @Test fun failedReadReportsErrorButStillOpensMatch() {
        val h = Inbox(); h.readOk = false; h.click()
        assertEquals(1, h.readErrors)
        assertEquals(listOf("LOST" to 77L), h.posts)
        assertEquals(47, h.vm.unreadCount)
    }
    @Test fun invalidDestinationsNeverCallDetailApiOrNavigate() {
        for (invalid in listOf(row(postId = null), row(postId = 0), row(postId = -1),
            row(postType = null), row(postType = "OTHER"))) {
            val h = Inbox(listOf(invalid)); h.click()
            assertTrue(h.posts.isEmpty()); assertTrue(h.details.isEmpty())
            assertEquals(1, h.messages.size)
        }
    }
    @Test fun deletedPostShowsMessageWithoutNavigation() {
        val h = Inbox(); h.lost = { Response.error(404, "".toResponseBody()) }; h.click()
        assertTrue(h.posts.isEmpty()); assertTrue(h.messages.single().contains("삭제"))
    }
    @Test fun apiErrorDoesNotNavigate() {
        val h = Inbox(); h.found = { Response.error(503, "".toResponseBody()) }
        val found = Inbox(listOf(row(postType = "FOUND")))
        found.found = h.found; found.click()
        assertTrue(found.posts.isEmpty()); assertEquals(1, found.messages.size)
    }
    @Test fun networkErrorDoesNotNavigate() {
        val h = Inbox(); h.lost = { throw java.io.IOException() }; h.click()
        assertTrue(h.posts.isEmpty()); assertEquals(1, h.messages.size)
    }
    @Test fun successfulEnvelopeWithoutPostDoesNotNavigate() {
        val h = Inbox(); h.lost = { Response.success(LostItemCreateResponse(true, "", null)) }; h.click()
        assertTrue(h.posts.isEmpty()); assertEquals(1, h.messages.size)
    }
    @Test fun oldAccountDetailResponseCannotNavigate() {
        val h = Inbox(); val result = CompletableDeferred<Response<LostItemCreateResponse>>()
        h.lost = { result.await() }; h.click()
        login("b"); h.vm.attachAccount(2); dispatch()
        result.complete(lostOk()); dispatch()
        assertTrue(h.posts.isEmpty()); assertTrue(h.messages.isEmpty())
    }
    @Test fun sessionExpirySuppressesDetailResponse() {
        val h = Inbox(); val result = CompletableDeferred<Response<LostItemCreateResponse>>()
        h.lost = { result.await() }; h.click()
        TokenManager.clear(); result.complete(lostOk()); dispatch()
        assertTrue(h.posts.isEmpty()); assertTrue(h.messages.isEmpty())
    }
    @Test fun leavingAndReenteringSuppressesOldDetailResponse() {
        val h = Inbox(); val result = CompletableDeferred<Response<LostItemCreateResponse>>()
        h.lost = { result.await() }; h.click()
        h.vm.setInboxOpen(false); h.vm.setInboxOpen(true); dispatch()
        result.complete(lostOk()); dispatch()
        assertTrue(h.posts.isEmpty()); assertTrue(h.messages.isEmpty())
    }
    @Test fun leavingDuringReadDoesNotStartPostLookup() {
        val h = Inbox(); val read = CompletableDeferred<Boolean>(); h.readGate = read; h.click()
        h.vm.setInboxOpen(false); read.complete(true); dispatch()
        assertTrue(h.details.isEmpty()); assertTrue(h.posts.isEmpty())
    }
    @Test fun laterClickSuppressesOlderDetailNavigation() {
        val h = Inbox(listOf(row(), row(postType = "FOUND").copy(id = "two", sequence = 99)))
        val result = CompletableDeferred<Response<LostItemCreateResponse>>()
        h.lost = { result.await() }; h.click(); h.click(1)
        result.complete(lostOk()); dispatch()
        assertEquals(listOf("FOUND" to 77L), h.posts)
    }
    @Test fun successfulReadKeepsServerUnreadCount() {
        val h = Inbox(); h.click()
        assertEquals(47, h.vm.unreadCount)
        assertNotNull(h.vm.items.single().readAt)
    }
    @Test fun groupedMatchStaysOneRowAndIgnoresOwnPostIds() {
        val page = Gson().fromJson("""{"snapshotSequence":100,"items":[{"id":"one","type":"MATCH","postType":"FOUND","postId":77,"ownPostIds":[1,2,3],"sequence":100}]}""", NotificationPageData::class.java)
        val h = Inbox(page.items); h.click()
        assertEquals(1, h.vm.items.size)
        assertEquals(listOf("FOUND" to 77L), h.posts)
    }
    @Test fun inboxAndPushResolveSamePostForBothTypes() {
        for (type in listOf("LOST", "FOUND")) {
            val h = Inbox(listOf(row(postType = type))); h.click()
            val target = NotificationTarget.fromData(mapOf("type" to "MATCH", "postType" to type, "postId" to "77")) as NotificationTarget.Post
            assertEquals(listOf(target.postType to target.postId), h.posts)
        }
    }
    @Test fun refreshedReadRowCanBeDeletedWithoutWeakeningStaleObjectGuard() {
        val h = Inbox(); val stale = h.vm.items.single()
        h.vm.markRead(stale, { fail() }, {}); dispatch()
        h.vm.hide(stale); dispatch()
        assertEquals(1, h.vm.items.size)
        h.vm.hide(h.vm.items.single()); dispatch()
        assertTrue(h.vm.items.isEmpty()); assertEquals(1, h.readCalls)
    }

    @Test fun failedSingleDeletionKeepsRowsAndAllowsAnotherChatToOpen() {
        val h = Inbox(listOf(row(), row(type = "CHAT").copy(id = "chat", roomId = 42, sequence = 99)))
        h.readOk = false; h.vm.hide(h.vm.items.first()); dispatch()
        assertEquals(com.ku.lostandfound.ui.notification.InboxState.Ready, h.vm.state)
        assertNotNull(h.vm.refreshError); assertEquals(2, h.vm.items.size)
        h.readOk = true; h.click(1)
        assertEquals(listOf(42L), h.rooms)
        h.vm.load(); dispatch()
        assertEquals(listOf("one", "chat"), h.vm.items.map { it.id })
    }
    @Test fun failedAllDeletionKeepsRowsAndAllowsMatchToOpen() {
        val h = Inbox(); h.allReadOk = false; h.vm.hideAll(); dispatch()
        assertEquals(com.ku.lostandfound.ui.notification.InboxState.Ready, h.vm.state)
        assertNotNull(h.vm.refreshError); assertEquals(1, h.vm.items.size)
        h.click()
        assertEquals(listOf("LOST" to 77L), h.posts)
        h.vm.load(); dispatch()
        assertEquals(1, h.vm.items.size)
    }
    @Test fun failedAllDeletionWithoutRowsUsesFullErrorState() {
        val h = Inbox(emptyList()); h.allReadOk = false; h.vm.hideAll(); dispatch()
        assertTrue(h.vm.state is com.ku.lostandfound.ui.notification.InboxState.Error)
        assertNull(h.vm.refreshError)
    }
    @Test fun failedSingleDeletionAfterListBecomesEmptyUsesFullErrorState() {
        val h = Inbox(); val gate = CompletableDeferred<Boolean>(); h.readGate = gate
        h.vm.hide(h.vm.items.first()); dispatch()
        h.rows = emptyList(); h.vm.load(); dispatch()
        gate.complete(false); dispatch()
        assertTrue(h.vm.state is com.ku.lostandfound.ui.notification.InboxState.Error)
        assertNull(h.vm.refreshError)
    }
}
