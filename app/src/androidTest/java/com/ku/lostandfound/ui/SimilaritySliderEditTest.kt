package com.ku.lostandfound.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.percentOffset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ku.lostandfound.network.ApiResponse
import com.ku.lostandfound.network.MarkAllNotificationsReadData
import com.ku.lostandfound.network.MarkAllNotificationsReadRequest
import com.ku.lostandfound.network.NotificationApiService
import com.ku.lostandfound.network.NotificationPageData
import com.ku.lostandfound.network.NotificationSettingsData
import com.ku.lostandfound.network.NotificationSettingsPatch
import com.ku.lostandfound.network.NotificationUnreadCountData
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.ui.notification.NotificationSettingsScreen
import com.ku.lostandfound.ui.notification.NotificationViewModel
import com.ku.lostandfound.ui.theme.KUfindersTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.util.Collections

/** Drives the real settings screen: other saves must not reset a drag; a failed save restores. */
@RunWith(AndroidJUnit4::class)
class SimilaritySliderEditTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val saved = NotificationSettingsData(true, true, true, 0.5)
    private val patches = Collections.synchronizedList(mutableListOf<NotificationSettingsPatch>())
    private val replies = Collections.synchronizedList(mutableListOf<CompletableDeferred<NotificationSettingsData?>>())
    private lateinit var vm: NotificationViewModel

    private val api = object : NotificationApiService {
        override suspend fun notifications(limit: Int, snapshotSequence: Long?, beforeSequence: Long?) =
            Response.success(ApiResponse(true, "", NotificationPageData(emptyList(), 0, null)))
        override suspend fun unreadCount() = Response.success(ApiResponse(true, "", NotificationUnreadCountData(0)))
        override suspend fun markRead(id: String) = Response.success(ApiResponse(true, "", Unit))
        override suspend fun markAllRead(request: MarkAllNotificationsReadRequest) =
            Response.success(ApiResponse(true, "", MarkAllNotificationsReadData(0)))
        override suspend fun settings() = Response.success(ApiResponse(true, "", saved))
        override suspend fun updateSettings(patch: NotificationSettingsPatch): Response<ApiResponse<NotificationSettingsData>> {
            patches += patch
            val reply = CompletableDeferred<NotificationSettingsData?>().also { replies += it }.await()
            return Response.success(ApiResponse(reply != null, if (reply == null) "fail" else "", reply))
        }
    }

    @Before fun show() {
        TokenManager.init(compose.activity)
        vm = NotificationViewModel(compose.activity.application, api)
        compose.runOnUiThread { vm.attachAccount(1) }
        compose.setContent { KUfindersTheme { NotificationSettingsScreen(vm, onBack = {}) } }
        compose.onNodeWithText("50% 이상").assertIsDisplayed()
    }

    private fun label(): String = compose.onNode(hasText("% 이상", substring = true)).fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun awaitReplies(count: Int) = compose.waitUntil(5_000) { replies.size >= count }

    @Test fun failingChatSaveDuringADragKeepsTheDragAndItIsSaved() {
        compose.onNodeWithText("채팅 알림").performClick()
        awaitReplies(1)
        val slider = compose.onNodeWithContentDescription("알림 기준 유사도")
        slider.performTouchInput { down(percentOffset(0.5f, 0.5f)); moveTo(percentOffset(0.85f, 0.5f)) }
        compose.waitForIdle()
        val dragged = label()
        assertNotEquals("50% 이상", dragged)
        compose.runOnIdle { replies[0].complete(null) } // the chat save fails while the finger is down
        compose.onNodeWithText("설정 저장에 실패했습니다.").assertIsDisplayed()
        assertEquals("the drag survives another setting's failure", dragged, label())
        slider.performTouchInput { up() }
        awaitReplies(2)
        val sent = checkNotNull(patches.last().similarityThreshold)
        assertEquals("the dragged value is what gets saved", dragged, "${Math.round(sent * 100)}% 이상")
    }

    @Test fun succeedingChatSaveDuringADragKeepsTheDrag() {
        compose.onNodeWithText("채팅 알림").performClick()
        awaitReplies(1)
        val slider = compose.onNodeWithContentDescription("알림 기준 유사도")
        slider.performTouchInput { down(percentOffset(0.5f, 0.5f)); moveTo(percentOffset(0.85f, 0.5f)) }
        compose.waitForIdle()
        val dragged = label()
        compose.runOnIdle { replies[0].complete(saved.copy(chatEnabled = false)) }
        compose.waitForIdle()
        assertEquals(dragged, label())
        slider.performTouchInput { up() }
        awaitReplies(2)
        assertTrue(patches.last().similarityThreshold != null)
    }

    @Test fun failedSimilaritySaveShowsTheLastConfirmedValueAgain() {
        val slider = compose.onNodeWithContentDescription("알림 기준 유사도")
        slider.performTouchInput { down(percentOffset(0.5f, 0.5f)); moveTo(percentOffset(0.85f, 0.5f)); up() }
        awaitReplies(1)
        assertNotEquals("50% 이상", label())
        compose.runOnIdle { replies[0].complete(null) }
        compose.onNodeWithText("50% 이상").assertIsDisplayed()
        compose.onNodeWithText("설정 저장에 실패했습니다.").assertIsDisplayed()
        assertEquals(1, patches.size)
    }

    @Test fun olderSimilarityReplyDoesNotMoveANewerDrag() {
        val slider = compose.onNodeWithContentDescription("알림 기준 유사도")
        slider.performTouchInput { down(percentOffset(0.5f, 0.5f)); moveTo(percentOffset(0.7f, 0.5f)); up() }
        awaitReplies(1)
        val firstSent = checkNotNull(patches[0].similarityThreshold)
        slider.performTouchInput { down(percentOffset(0.7f, 0.5f)); moveTo(percentOffset(0.95f, 0.5f)) }
        compose.waitForIdle()
        val newer = label()
        compose.runOnIdle { replies[0].complete(saved.copy(similarityThreshold = firstSent)) }
        compose.waitForIdle()
        assertEquals("an older reply must not move the newer drag", newer, label())
        slider.performTouchInput { up() }
        awaitReplies(2)
        assertEquals(newer, "${Math.round(checkNotNull(patches[1].similarityThreshold) * 100)}% 이상")
    }

    private fun setSlider(value: Float) = compose.onNodeWithContentDescription("알림 기준 유사도")
        .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }

    @Test fun returningTo50WhileThe80SaveIsPendingEndsAt50WhenItSucceeds() {
        setSlider(0.8f)
        awaitReplies(1)
        setSlider(0.5f) // back to the confirmed value before the first reply
        compose.onNodeWithText("50% 이상").assertIsDisplayed()
        compose.runOnIdle { replies[0].complete(saved.copy(similarityThreshold = 0.8)) }
        awaitReplies(2)
        assertEquals(listOf(0.8, 0.5), patches.map { it.similarityThreshold })
        compose.runOnIdle { replies[1].complete(saved.copy(similarityThreshold = 0.5)) }
        compose.onNodeWithText("50% 이상").assertIsDisplayed()
    }

    @Test fun returningTo50WhileThe80SaveIsPendingSendsNothingMoreWhenItFails() {
        setSlider(0.8f)
        awaitReplies(1)
        setSlider(0.5f)
        compose.runOnIdle { replies[0].complete(null) }
        compose.onNodeWithText("설정 저장에 실패했습니다.").assertIsDisplayed()
        compose.onNodeWithText("50% 이상").assertIsDisplayed()
        compose.waitForIdle()
        assertEquals(1, patches.size)
    }

    @Test fun confirmedValueWithNothingPendingIsNotSent() {
        setSlider(0.5f)
        compose.onNodeWithText("50% 이상").assertIsDisplayed()
        compose.waitForIdle()
        assertEquals(0, patches.size)
    }
}
