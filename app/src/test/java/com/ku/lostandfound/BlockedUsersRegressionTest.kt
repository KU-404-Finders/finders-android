package com.ku.lostandfound

import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.data.BlockedUserNames
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.profile.viewmodel.BlockedUsersViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import retrofit2.Response

class BlockedUsersRegressionTest : StabilityFixture() {
    private lateinit var vm: BlockedUsersViewModel
    private lateinit var names: BlockedUserNames
    private val lists = mutableListOf<CompletableDeferred<List<BlockResponse>>>()
    private val deletion = CompletableDeferred<Boolean>()
    private var deletes = 0
    private val pages = mutableListOf<Int>()

    @Before fun createBlockedUsers() {
        val values = mutableMapOf<String, String>()
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(application.getSharedPreferences("blocked_user_names", 0)).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getString(anyString(), isNull())).thenAnswer { values[it.getArgument<String>(0)] }
        `when`(editor.putString(anyString(), anyString())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument(1); editor
        }
        `when`(editor.remove(anyString())).thenAnswer { values.remove(it.getArgument<String>(0)); editor }
        names = BlockedUserNames(application)
        val api = object : ModerationApiService by mock(ModerationApiService::class.java) {
            override suspend fun blockedUsers(page: Int): Response<ApiResponse<List<BlockResponse>>> {
                pages += page
                return ok(CompletableDeferred<List<BlockResponse>>().also(lists::add).await())
            }
            override suspend fun unblock(userId: Long): Response<ApiResponse<Unit>> {
                deletes++
                return if (deletion.await()) Response.success<ApiResponse<Unit>>(204, null)
                else Response.success(ApiResponse(false, "failed", Unit))
            }
        }
        vm = BlockedUsersViewModel(application, api)
    }

    @After fun closeBlockedUsers() { vm.viewModelScope.cancel() }

    private fun load(account: Long = 1) {
        vm.attachAccount(account)
        vm.load()
        dispatch()
        lists.last().complete(listOf(BlockResponse(42), BlockResponse(43)))
        dispatch()
    }

    @Test fun namesAreDeviceLocalAndAccountScopedWithIdFallback() {
        names.save(1, 42, "홍길동")
        names.save(2, 42, "다른 이름")
        names.save(1, 43, " ")
        load()
        assertEquals(listOf("홍길동", "사용자 #43"), vm.users.map { it.name })
        load(2)
        assertEquals(listOf("다른 이름", "사용자 #43"), vm.users.map { it.name })
    }

    @Test fun unblockOnlyRemovesUserAfterSuccessAndRejectsDuplicateTaps() {
        names.save(1, 42, "홍길동")
        load()
        var callbacks = 0
        repeat(2) { vm.unblock(42) { callbacks++ } }
        dispatch()
        assertEquals(1, deletes)
        assertEquals(setOf(42L), vm.unblocking)
        assertEquals(2, vm.users.size)
        deletion.complete(true)
        dispatch()
        assertEquals(listOf(43L), vm.users.map { it.id })
        assertEquals(1, callbacks)
        assertEquals("사용자 #42", names.displayName(1, 42))
        assertTrue(vm.unblocking.isEmpty())
    }

    @Test fun failedUnblockKeepsUserAndSavedNameForRetry() {
        names.save(1, 42, "홍길동")
        load()
        vm.unblock(42) { fail("Failed deletion must not report success") }
        deletion.complete(false)
        dispatch()
        assertEquals(2, vm.users.size)
        assertEquals("홍길동", names.displayName(1, 42))
        assertNotNull(vm.error)
        assertTrue(vm.unblocking.isEmpty())
    }

    @Test fun oldListCannotRestoreSuccessfullyUnblockedUser() {
        load()
        vm.load()
        dispatch()
        val oldList = lists.last()
        vm.unblock(42) {}
        deletion.complete(true)
        dispatch()
        oldList.complete(listOf(BlockResponse(42), BlockResponse(43)))
        dispatch()
        assertEquals(listOf(43L), vm.users.map { it.id })
        lists.last().complete(listOf(BlockResponse(43)))
        dispatch()
        assertEquals(listOf(43L), vm.users.map { it.id })
        assertFalse(vm.loading)
    }

    @Test fun previousAccountDeletionCannotChangeCurrentListOrSavedName() {
        names.save(1, 42, "홍길동")
        load()
        vm.unblock(42) { fail("Old account must not report success") }
        dispatch()
        load(2)
        deletion.complete(true)
        dispatch()
        assertEquals(listOf(42L, 43L), vm.users.map { it.id })
        assertEquals("홍길동", names.displayName(1, 42))
        assertTrue(vm.unblocking.isEmpty())
    }

    @Test fun previousAccountListCannotPopulateNewAccount() {
        vm.attachAccount(1)
        vm.load()
        dispatch()
        val old = lists.last()
        load(2)
        old.complete(listOf(BlockResponse(99)))
        dispatch()
        assertEquals(listOf(42L, 43L), vm.users.map { it.id })
    }

    @Test fun loadsFiftyAtATimeAndStopsAfterShortPage() {
        vm.attachAccount(1)
        vm.load()
        dispatch()
        lists.last().complete((1L..50L).map { BlockResponse(it, "2026-11-23T10:00:00") })
        dispatch()
        assertTrue(vm.hasMore)
        assertEquals("2026-11-23T10:00:00", vm.users.first().createdAt)
        repeat(2) { vm.load(reset = false) }
        dispatch()
        assertEquals(listOf(0, 1), pages)
        lists.last().complete(listOf(BlockResponse(51)))
        dispatch()
        assertEquals((1L..51L).toList(), vm.users.map { it.id })
        assertFalse(vm.hasMore)
        vm.load(reset = false)
        dispatch()
        assertEquals(listOf(0, 1), pages)
    }

    @Test fun failedNextPageRetainsRowsAndRetriesTheSamePage() {
        vm.attachAccount(1)
        vm.load()
        dispatch()
        lists.last().complete((1L..50L).map { BlockResponse(it) })
        dispatch()
        vm.load(reset = false)
        dispatch()
        lists.last().completeExceptionally(java.io.IOException("offline"))
        dispatch()
        assertEquals(50, vm.users.size)
        assertNotNull(vm.error)
        vm.retry()
        dispatch()
        assertEquals(listOf(0, 1, 1), pages)
        lists.last().complete(listOf(BlockResponse(51)))
        dispatch()
        assertEquals(51, vm.users.size)
        assertNull(vm.error)
    }

    @Test fun unblockingRestartsPaginationToAvoidSkippingShiftedRows() {
        vm.attachAccount(1)
        vm.load()
        dispatch()
        lists.last().complete((1L..50L).map { BlockResponse(it) })
        dispatch()
        vm.load(reset = false)
        dispatch()
        lists.last().complete(listOf(BlockResponse(51), BlockResponse(52)))
        dispatch()
        vm.unblock(42) {}
        deletion.complete(true)
        dispatch()
        assertEquals(listOf(0, 1, 0), pages)
        lists.last().complete((1L..51L).filterNot { it == 42L }.map { BlockResponse(it) })
        dispatch()
        vm.load(reset = false)
        dispatch()
        lists.last().complete(listOf(BlockResponse(52)))
        dispatch()
        assertEquals((1L..52L).filterNot { it == 42L }, vm.users.map { it.id })
        assertFalse(vm.hasMore)
    }

    @Test fun failedResetAfterUnblockCannotReuseOldOffsetAndRetriesPageZero() {
        vm.attachAccount(1); vm.load(); dispatch()
        lists.last().complete((1L..50L).map { BlockResponse(it) }); dispatch()
        vm.load(false); dispatch()
        lists.last().complete((51L..100L).map { BlockResponse(it) }); dispatch()
        assertTrue(vm.hasMore)
        vm.unblock(42) {}; deletion.complete(true); dispatch()
        assertFalse(vm.hasMore)
        lists.last().completeExceptionally(java.io.IOException()); dispatch()
        vm.load(false); dispatch()
        assertEquals(listOf(0, 1, 0), pages)
        vm.retry(); dispatch()
        assertEquals(listOf(0, 1, 0, 0), pages)
        lists.last().complete((1L..51L).filterNot { it == 42L }.map { BlockResponse(it) }); dispatch()
        vm.load(false); dispatch()
        assertEquals(listOf(0, 1, 0, 0, 1), pages)
    }
}
