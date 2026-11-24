package com.ku.lostandfound

import com.ku.lostandfound.network.*
import com.ku.lostandfound.notification.NotificationInboxRefreshBus
import com.ku.lostandfound.ui.notification.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.*
import android.content.SharedPreferences
import retrofit2.Response

class InboxRegressionTest : StabilityFixture() {
    private var pages = 0
    private var badges = 0
    private var patches = 0
    private val olderPage = CompletableDeferred<NotificationPageData>()
    private val fetched = CompletableDeferred<NotificationSettingsData>()
    private val read = CompletableDeferred<Boolean>()
    private val saved = CompletableDeferred<NotificationSettingsData>()
    private val allRead = CompletableDeferred<Boolean>()
    private var deletedThrough: Long? = null
    private val item = InboxNotificationData("one", "CHAT", roomId = 42, sequence = 1)
    private lateinit var vm: NotificationViewModel

    @Before fun createInbox() {
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        val sequences = mutableMapOf<String, Set<String>>()
        val cutoffs = mutableMapOf<String, Long>()
        `when`(application.getSharedPreferences("finders_notifications", 0)).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getStringSet(anyString(), anySet())).thenAnswer { sequences[it.getArgument<String>(0)] ?: emptySet<String>() }
        `when`(prefs.getLong(anyString(), anyLong())).thenAnswer { cutoffs[it.getArgument<String>(0)] ?: it.getArgument<Long>(1) }
        `when`(editor.putStringSet(anyString(), anySet())).thenAnswer {
            sequences[it.getArgument(0)] = it.getArgument<Set<String>>(1).toSet(); editor
        }
        `when`(editor.putLong(anyString(), anyLong())).thenAnswer {
            cutoffs[it.getArgument(0)] = it.getArgument(1); editor
        }
        val api = object : NotificationApiService by mock(NotificationApiService::class.java) {
            override suspend fun notifications(limit: Int, snapshotSequence: Long?, beforeSequence: Long?): Response<ApiResponse<NotificationPageData>> {
                pages++
                return if (beforeSequence == null) ok(NotificationPageData(listOf(item), pages.toLong(), 1))
                else ok(olderPage.await())
            }
            override suspend fun unreadCount() = ok(NotificationUnreadCountData(++badges))
            override suspend fun markRead(id: String) = Response.success(ApiResponse(read.await(), "", Unit))
            override suspend fun markAllRead(request: MarkAllNotificationsReadRequest): Response<ApiResponse<MarkAllNotificationsReadData>> {
                deletedThrough = request.throughSequence
                return Response.success(ApiResponse(allRead.await(), "", MarkAllNotificationsReadData()))
            }
            override suspend fun settings() = ok(fetched.await())
            override suspend fun updateSettings(patch: NotificationSettingsPatch): Response<ApiResponse<NotificationSettingsData>> {
                patches++
                return ok(saved.await())
            }
        }
        vm = NotificationViewModel(application, api)
        vm.setInboxOpen(true)
        dispatch()
    }

    @After fun closeInbox() { vm.viewModelScope.cancel() }

    @Test fun enteringBeforeAccountAttachmentLoadsAfterAttachment() {
        assertEquals(0, pages)
        assertEquals(InboxState.Ready, vm.state)
        vm.attachAccount(1)
        dispatch()
        assertEquals(listOf(item), vm.items)
        assertEquals(1, pages)
    }

    @Test fun openInboxRefreshesBothPageAndBadgeOnArrival() {
        vm.attachAccount(1)
        dispatch()
        val before = badges
        NotificationInboxRefreshBus.notifyArrival()
        dispatch()
        assertEquals(2, pages)
        assertTrue(vm.unreadCount > before)
    }

    @Test fun latePaginationCannotUndoRefresh() {
        vm.attachAccount(1)
        dispatch()
        vm.load(false)
        dispatch()
        vm.load(true)
        dispatch()
        olderPage.complete(NotificationPageData(listOf(item.copy(id = "stale")), 1, null))
        dispatch()
        assertEquals(listOf(item), vm.items)
        assertEquals(3L, vm.snapshotSequence)
        assertEquals(1L, vm.nextBeforeSequence)
        assertFalse(vm.loadingMore)
    }

    @Test fun lateSettingsGetCannotPopulateNextAccount() {
        vm.attachAccount(1)
        vm.loadSettings()
        dispatch()
        vm.attachAccount(2)
        fetched.complete(NotificationSettingsData(true, true, true, 0.5))
        dispatch()
        assertNull(vm.settings)
        assertNull(vm.settingsError)
    }

    @Test fun previousAccountCannotPublishSettingsOrSendQueuedPatch() {
        vm.attachAccount(1)
        repeat(2) { vm.updateSettings(NotificationSettingsPatch(chatEnabled = false)) }
        dispatch()
        vm.attachAccount(2)
        saved.complete(NotificationSettingsData(false, false, false, 0.5))
        dispatch()
        assertNull(vm.settings)
        assertNull(vm.settingsError)
        assertEquals(1, patches)
    }

    @Test fun readFailureReportsErrorAndOpensRoom() {
        vm.attachAccount(1)
        dispatch()
        val events = mutableListOf<String>()
        vm.markRead(vm.items.single(), { events += "error" }, { events += "room" })
        read.complete(false)
        dispatch()
        assertEquals(listOf("error", "room"), events)
    }

    @Test fun previousAccountClickCannotOpenRoom() {
        vm.attachAccount(1)
        dispatch()
        var callbacks = 0
        vm.markRead(vm.items.single(), { callbacks++ }, { callbacks++ })
        dispatch()
        vm.attachAccount(2)
        read.complete(true)
        dispatch()
        assertEquals(0, callbacks)
    }

    @Test fun deletionWaitsForReadAndStaysDeletedAfterRefresh() {
        vm.attachAccount(1)
        dispatch()
        vm.hide(vm.items.single())
        dispatch()
        assertEquals(listOf(item), vm.items)
        read.complete(true)
        dispatch()
        assertTrue(vm.items.isEmpty())
        vm.load()
        dispatch()
        assertTrue(vm.items.isEmpty())
        vm.attachAccount(2)
        dispatch()
        assertEquals(listOf(item), vm.items)
    }

    @Test fun readFailureDoesNotDeleteNotification() {
        vm.attachAccount(1)
        dispatch()
        vm.hide(vm.items.single())
        read.complete(false)
        dispatch()
        assertEquals(listOf(item), vm.items)
        assertTrue(vm.state is InboxState.Error)
    }

    @Test fun deleteAllFiltersOlderPagesButKeepsNewArrivals() {
        vm.attachAccount(1)
        dispatch()
        vm.hideAll()
        allRead.complete(true)
        dispatch()
        assertTrue(vm.items.isEmpty())
        vm.load(false)
        olderPage.complete(NotificationPageData(listOf(item, item.copy(id = "new", sequence = 3)), 2, null))
        dispatch()
        assertEquals(listOf("new"), vm.items.map { it.id })
    }

    @Test fun deletionFromPreviousAccountCannotHideNextAccountNotification() {
        vm.attachAccount(1)
        dispatch()
        vm.hide(vm.items.single())
        dispatch()
        vm.attachAccount(2)
        read.complete(true)
        dispatch()
        assertEquals(listOf(item), vm.items)
    }

    @Test fun deleteAllKeepsOriginalSnapshotBoundaryAfterArrivalRefresh() {
        vm.attachAccount(1); dispatch()
        assertEquals(1L, vm.snapshotSequence)
        vm.refreshArrivals(); dispatch()
        assertEquals(2, pages)
        assertEquals(1L, vm.snapshotSequence)
        vm.hideAll(); allRead.complete(true); dispatch()
        assertEquals(1L, deletedThrough)
        assertTrue(vm.items.isEmpty())
    }
}
