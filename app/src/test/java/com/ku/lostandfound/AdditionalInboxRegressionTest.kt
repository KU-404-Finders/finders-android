package com.ku.lostandfound

import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.notification.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class AdditionalInboxRegressionTest : StabilityFixture() {
    private data class PageCall(val snapshot: Long?, val before: Long?, val result: CompletableDeferred<Response<ApiResponse<NotificationPageData>>>)
    private val calls = mutableListOf<PageCall>()
    private val read = CompletableDeferred<Boolean>()
    private val patch = CompletableDeferred<NotificationSettingsData>()
    private var badges = 0
    private var gets = 0
    private var markedThrough: Long? = null
    private var readCalls = 0
    private val settings = NotificationSettingsData(true, true, true, 0.5)
    private lateinit var vm: NotificationViewModel
    private fun item(sequence: Long) = InboxNotificationData("$sequence", "CHAT", roomId = sequence, sequence = sequence)

    @Before fun create() {
        val api = object : NotificationApiService by mock(NotificationApiService::class.java) {
            override suspend fun notifications(limit: Int, snapshotSequence: Long?, beforeSequence: Long?) =
                CompletableDeferred<Response<ApiResponse<NotificationPageData>>>().also {
                    calls += PageCall(snapshotSequence, beforeSequence, it)
                }.await()
            override suspend fun unreadCount() = ok(NotificationUnreadCountData(++badges))
            override suspend fun markRead(id: String): Response<ApiResponse<Unit>> {
                readCalls++
                return Response.success(ApiResponse(read.await(), "", Unit))
            }
            override suspend fun markAllRead(request: MarkAllNotificationsReadRequest): Response<ApiResponse<MarkAllNotificationsReadData>> {
                markedThrough = request.throughSequence
                return ok(MarkAllNotificationsReadData(1))
            }
            override suspend fun settings(): Response<ApiResponse<NotificationSettingsData>> { gets++; return ok(settings) }
            override suspend fun updateSettings(patch: NotificationSettingsPatch) = ok(this@AdditionalInboxRegressionTest.patch.await())
        }
        vm = NotificationViewModel(application, api)
        vm.setInboxOpen(true)
        vm.attachAccount(1)
        dispatch()
    }
    @After fun close() { vm.viewModelScope.cancel() }
    private fun finish(snapshot: Long = 100, next: Long? = 80, rows: List<InboxNotificationData> = listOf(item(100))) {
        calls.last().result.complete(ok(NotificationPageData(rows, snapshot, next)))
        dispatch()
    }

    @Test fun arrivalPreservesLoadedOlderRowsAndTheirSnapshotCursorPair() {
        finish()
        vm.load(false)
        dispatch()
        finish(next = 60, rows = listOf(item(79)))
        vm.refreshArrivals()
        dispatch()
        finish(snapshot = 120, next = 90, rows = listOf(item(120), item(100)))
        assertEquals(listOf(120L, 100L, 79L), vm.items.map { it.sequence })
        assertEquals(100L, vm.snapshotSequence)
        assertEquals(60L, vm.nextBeforeSequence)
        vm.load(false)
        dispatch()
        assertEquals(100L, calls.last().snapshot)
        assertEquals(60L, calls.last().before)
    }

    @Test fun arrivalTraversesAllNewPagesBeforeMerging() {
        finish()
        vm.refreshArrivals()
        dispatch()
        finish(snapshot = 200, next = 171, rows = (200L downTo 171L).map(::item))
        assertEquals(200L, calls.last().snapshot)
        assertEquals(171L, calls.last().before)
        finish(snapshot = 200, next = 90, rows = listOf(item(170), item(100)))
        assertEquals(32, vm.items.size)
        assertEquals(100L, vm.snapshotSequence)
        assertEquals(80L, vm.nextBeforeSequence)
    }

    @Test fun markAllIncludesNewArrivalsWithoutChangingOlderPageCursor() {
        finish()
        vm.refreshArrivals()
        dispatch()
        finish(snapshot = 120, rows = listOf(item(120), item(100)))
        assertEquals(100L, vm.snapshotSequence)
        vm.markAllRead()
        dispatch()
        assertEquals(120L, markedThrough)
    }

    @Test fun failedArrivalKeepsRowsAndCursorWithSeparateError() {
        finish()
        vm.refreshArrivals()
        dispatch()
        calls.last().result.complete(Response.error(503, "".toResponseBody()))
        dispatch()
        assertEquals(listOf(100L), vm.items.map { it.sequence })
        assertEquals(100L, vm.snapshotSequence)
        assertEquals(80L, vm.nextBeforeSequence)
        assertEquals(InboxState.Ready, vm.state)
        assertNotNull(vm.refreshError)
    }

    @Test fun initialFailureStillUsesFullErrorState() {
        calls.last().result.complete(Response.error(503, "".toResponseBody()))
        dispatch()
        assertTrue(vm.state is InboxState.Error)
        assertTrue(vm.items.isEmpty())
    }

    @Test fun readFromEarlierVisitCannotNavigateAfterReturningButStillUpdatesBadge() {
        finish()
        var callbacks = 0
        vm.markRead(vm.items.single(), { callbacks++ }, { callbacks++ })
        dispatch()
        vm.setInboxOpen(false)
        vm.setInboxOpen(true)
        dispatch()
        finish()
        val before = badges
        read.complete(true)
        dispatch()
        assertEquals(0, callbacks)
        assertTrue(badges > before)
    }

    @Test fun readSuccessAfterClosingRefreshesBadgeWithoutUiCallbacks() {
        finish()
        vm.markRead(vm.items.single(), { fail() }, { fail() })
        dispatch()
        vm.setInboxOpen(false)
        val before = badges
        read.complete(true)
        dispatch()
        assertTrue(badges > before)
    }

    @Test fun changingOnlyTokenGenerationSuppressesOldReadCallbacks() {
        finish()
        vm.markRead(vm.items.single(), { fail() }, { fail() })
        dispatch()
        login("b")
        read.complete(false)
        dispatch()
    }

    @Test fun newAccountsSettingsGetDoesNotWaitForPreviousAccountsPatch() {
        vm.updateSettings(NotificationSettingsPatch(chatEnabled = false))
        dispatch()
        vm.attachAccount(2)
        vm.loadSettings()
        dispatch()
        assertEquals(1, gets)
        assertEquals(settings, vm.settings)
        patch.complete(settings.copy(chatEnabled = false))
        dispatch()
        assertEquals(settings, vm.settings)
    }

    @Test fun firstPageReadIsImmediateAndSurvivesStaleArrivalWithoutDuplicatePatch() {
        finish()
        vm.markRead(vm.items.single(), { fail() }, {})
        dispatch()
        assertNull(vm.items.single().readAt)
        read.complete(true)
        dispatch()
        assertNotNull(vm.items.single().readAt)
        finish() // The arrival response still contains the old unread row.
        assertNotNull(vm.items.single().readAt)
        vm.markRead(vm.items.single(), { fail() }, {})
        dispatch()
        assertEquals(1, readCalls)
    }

    @Test fun oldPageReadSurvivesNewArrivalsAndPreservesCursor() {
        finish()
        vm.load(false)
        dispatch()
        finish(next = 60, rows = listOf(item(79)))
        vm.markRead(vm.items.last(), { fail() }, {})
        dispatch()
        read.complete(true)
        dispatch()
        assertNotNull(vm.items.last().readAt)
        finish(snapshot = 120, rows = listOf(item(120), item(100)))
        assertEquals(listOf(120L, 100L, 79L), vm.items.map { it.sequence })
        assertNotNull(vm.items.last().readAt)
        assertEquals(100L, vm.snapshotSequence)
        assertEquals(60L, vm.nextBeforeSequence)
        vm.markRead(vm.items.last(), { fail() }, {})
        dispatch()
        assertEquals(1, readCalls)
    }

    @Test fun failedReadLeavesUnreadAndAllowsRetry() {
        finish()
        var errors = 0
        var opens = 0
        vm.markRead(vm.items.single(), { errors++ }, { opens++ })
        dispatch()
        read.complete(false)
        dispatch()
        assertNull(vm.items.single().readAt)
        assertEquals(1, errors)
        assertEquals(1, opens)
        vm.markRead(vm.items.single(), {}, {})
        dispatch()
        assertEquals(2, readCalls)
    }

    @Test fun concurrentReadClicksShareConfirmedReadWithoutDuplicatePatch() {
        finish()
        val row = vm.items.single()
        repeat(2) { vm.markRead(row, { fail() }, {}) }
        dispatch()
        assertEquals(1, readCalls)
        read.complete(true)
        dispatch()
        assertEquals(1, readCalls)
        assertNotNull(vm.items.single().readAt)
    }

    @Test fun previousAccountsReadSuccessCannotMarkNewAccountsSameId() {
        finish()
        vm.markRead(vm.items.single(), { fail() }, { fail() })
        dispatch()
        login("b")
        vm.attachAccount(2)
        dispatch()
        finish()
        read.complete(true)
        dispatch()
        assertNull(vm.items.single().readAt)
    }

    @Test fun failedSimilaritySaveKeepsTheSavedValueAndSignalsTheDraftToReset() {
        vm.loadSettings()
        dispatch()
        val saved = checkNotNull(vm.settings)
        val before = vm.settingsRevision
        vm.updateSettings(NotificationSettingsPatch(similarityThreshold = 0.8))
        dispatch()
        patch.completeExceptionally(java.io.IOException("offline"))
        dispatch()
        assertEquals(saved, vm.settings)
        assertEquals("설정 저장에 실패했습니다.", vm.settingsError)
        assertEquals(before + 1, vm.settingsRevision)
    }

    @Test fun previousAccountsSettingsResponseDoesNotTouchTheNewAccountsRevision() {
        vm.updateSettings(NotificationSettingsPatch(similarityThreshold = 0.8))
        dispatch()
        vm.attachAccount(2)
        val before = vm.settingsRevision
        patch.complete(settings.copy(similarityThreshold = 0.8))
        dispatch()
        assertEquals(before, vm.settingsRevision)
        assertNotEquals(0.8, vm.settings?.similarityThreshold ?: 0.0, 0.0001)
    }
}
