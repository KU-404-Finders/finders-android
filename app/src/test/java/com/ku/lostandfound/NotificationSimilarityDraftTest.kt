package com.ku.lostandfound

import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.ApiResponse
import com.ku.lostandfound.network.NotificationApiService
import com.ku.lostandfound.network.NotificationSettingsData
import com.ku.lostandfound.network.NotificationSettingsPatch
import com.ku.lostandfound.network.NotificationUnreadCountData
import com.ku.lostandfound.ui.notification.NotificationViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import retrofit2.Response

/** PR #18 third review: a similarity draft is only reset by the save that started from it. */
class NotificationSimilarityDraftTest : StabilityFixture() {
    private val saved = NotificationSettingsData(true, true, true, 0.5)
    private val patches = mutableListOf<NotificationSettingsPatch>()
    private val replies = mutableListOf<CompletableDeferred<NotificationSettingsData?>>()
    private lateinit var vm: NotificationViewModel

    @Before fun create() {
        val api = object : NotificationApiService by mock(NotificationApiService::class.java) {
            override suspend fun unreadCount() = ok(NotificationUnreadCountData(0))
            override suspend fun settings() = ok(saved)
            override suspend fun updateSettings(patch: NotificationSettingsPatch): Response<ApiResponse<NotificationSettingsData>> {
                patches += patch
                val reply = CompletableDeferred<NotificationSettingsData?>().also(replies::add).await()
                return if (reply == null) Response.success(ApiResponse(false, "fail")) else ok(reply)
            }
        }
        vm = NotificationViewModel(application, api)
        vm.attachAccount(1)
        vm.loadSettings()
        dispatch()
    }

    @After fun close() { vm.viewModelScope.cancel() }

    private fun shown() = vm.similarityDraft ?: vm.settings!!.similarityThreshold

    @Test fun otherSettingFailingOrSucceedingKeepsTheSliderEdit() {
        vm.updateSettings(NotificationSettingsPatch(chatEnabled = false))
        dispatch()
        vm.editSimilarity(0.8)
        replies.single().complete(null) // chat save fails mid-drag
        dispatch()
        assertEquals(0.8, shown(), 0.0001)
        vm.updateSettings(NotificationSettingsPatch(commentEnabled = false))
        dispatch()
        replies[1].complete(saved.copy(commentEnabled = false)) // and another succeeds
        dispatch()
        assertEquals(0.8, shown(), 0.0001)
        vm.commitSimilarity()
        dispatch()
        assertEquals(0.8, patches.last().similarityThreshold!!, 0.0001)
    }

    @Test fun failedSimilaritySaveWithoutANewEditShowsTheConfirmedValue() {
        vm.editSimilarity(0.8)
        vm.commitSimilarity()
        dispatch()
        replies.single().complete(null)
        dispatch()
        assertNull(vm.similarityDraft)
        assertEquals(0.5, shown(), 0.0001)
        assertEquals("설정 저장에 실패했습니다.", vm.settingsError)
    }

    @Test fun olderSimilarityResponsesDoNotOverwriteANewerEdit() {
        vm.editSimilarity(0.8)
        vm.commitSimilarity()
        dispatch()
        vm.editSimilarity(0.9) // new drag before the first save answers
        replies[0].complete(saved.copy(similarityThreshold = 0.8))
        dispatch()
        assertEquals(0.9, shown(), 0.0001)
        assertEquals(0.8, vm.settings!!.similarityThreshold, 0.0001)
        vm.commitSimilarity()
        dispatch()
        assertEquals(listOf(0.8, 0.9), patches.map { it.similarityThreshold })
        replies[1].complete(null) // the latest save fails: back to the last confirmed 0.8
        dispatch()
        assertEquals(0.8, shown(), 0.0001)
    }

    @Test fun consecutiveSavesEndOnTheLastConfirmedValue() {
        vm.editSimilarity(0.6)
        vm.commitSimilarity()
        dispatch()
        replies[0].complete(saved.copy(similarityThreshold = 0.6))
        dispatch()
        vm.editSimilarity(0.7)
        vm.commitSimilarity()
        dispatch()
        replies[1].complete(saved.copy(similarityThreshold = 0.7))
        dispatch()
        assertNull(vm.similarityDraft)
        assertEquals(0.7, shown(), 0.0001)
    }

    @Test fun previousAccountsSimilarityResponseIsIgnored() {
        vm.editSimilarity(0.8)
        vm.commitSimilarity()
        dispatch()
        vm.attachAccount(2)
        assertNull(vm.similarityDraft)
        replies.single().complete(saved.copy(similarityThreshold = 0.8))
        dispatch()
        assertNull(vm.settings)
        assertNull(vm.similarityDraft)
    }

    @Test fun valueChosenBackDuringAPendingSaveIsSavedWhenThatSaveSucceeds() {
        vm.editSimilarity(0.8)
        vm.commitSimilarity()
        dispatch()
        vm.editSimilarity(0.5) // back to the confirmed value before the 80% save answers
        vm.commitSimilarity()
        dispatch()
        assertEquals("the second save waits for the first", 1, patches.size)
        replies[0].complete(saved.copy(similarityThreshold = 0.8))
        dispatch()
        assertEquals(listOf(0.8, 0.5), patches.map { it.similarityThreshold })
        replies[1].complete(saved.copy(similarityThreshold = 0.5))
        dispatch()
        assertNull(vm.similarityDraft)
        assertEquals(0.5, shown(), 0.0001)
    }

    @Test fun valueChosenBackDuringAPendingSaveIsNotResentWhenThatSaveFails() {
        vm.editSimilarity(0.8)
        vm.commitSimilarity()
        dispatch()
        vm.editSimilarity(0.5)
        vm.commitSimilarity()
        dispatch()
        replies[0].complete(null)
        dispatch()
        assertEquals("50% is already the confirmed value", 1, patches.size)
        assertNull(vm.similarityDraft)
        assertEquals(0.5, shown(), 0.0001)
    }

    @Test fun rapidChangesEndOnTheLastChoice() {
        for (value in listOf(0.8, 0.6, 0.9)) {
            vm.editSimilarity(value)
            vm.commitSimilarity()
            dispatch()
        }
        for (i in 0 until 3) {
            replies[i].complete(saved.copy(similarityThreshold = patches[i].similarityThreshold!!))
            dispatch()
        }
        assertEquals(listOf(0.8, 0.6, 0.9), patches.map { it.similarityThreshold })
        assertNull(vm.similarityDraft)
        assertEquals(0.9, shown(), 0.0001)
    }

    @Test fun choosingTheConfirmedValueWithNothingPendingSendsNothing() {
        vm.editSimilarity(0.5)
        vm.commitSimilarity()
        dispatch()
        assertEquals(0, patches.size)
        assertNull(vm.similarityDraft)
    }
}
