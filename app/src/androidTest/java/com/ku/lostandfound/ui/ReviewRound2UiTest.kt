package com.ku.lostandfound.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ku.lostandfound.network.AdminUser
import com.ku.lostandfound.network.ChatRoomResponse
import com.ku.lostandfound.ui.admin.screen.AdminPostListScreen
import com.ku.lostandfound.ui.admin.screen.AdminReportListScreen
import com.ku.lostandfound.ui.admin.screen.AdminUserListScreen
import com.ku.lostandfound.ui.chat.screen.ChatListScreen
import com.ku.lostandfound.ui.component.FinderConfirmDialog
import com.ku.lostandfound.ui.theme.KUfindersTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** PR #18 second CodeRabbit round: paging retry, post retry, selection semantics, large fonts. */
@RunWith(AndroidJUnit4::class)
class ReviewRound2UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun failedNextPageIsNotRequestedAgainUntilRetryIsTapped() {
        var requests = 0
        val user = AdminUser(1, "a@konkuk.ac.kr", "김건국", "USER", false, createdAt = "2026-03-02T10:00:00")
        compose.setContent {
            KUfindersTheme {
                AdminUserListScreen(users = listOf(user), hasNext = true, onMore = { requests++ }, isLoading = false,
                    errorMessage = "서버와 연결할 수 없습니다.", onBackClick = {}, onSearch = {}, onUserClick = {})
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertEquals("no automatic retry after a failed page", 0, requests) }
        compose.onNodeWithText("김건국").assertIsDisplayed() // loaded rows stay
        compose.onNodeWithText("다시 시도").performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun nextPageLoadsAutomaticallyWithoutAnError() {
        var requests = 0
        val user = AdminUser(1, "a@konkuk.ac.kr", "김건국", "USER", false, createdAt = "2026-03-02T10:00:00")
        compose.setContent {
            KUfindersTheme {
                AdminUserListScreen(users = listOf(user), hasNext = true, onMore = { requests++ }, isLoading = false,
                    errorMessage = null, onBackClick = {}, onSearch = {}, onUserClick = {})
            }
        }
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun firstPostPageRetryUsesItsOwnCallback() {
        var retries = 0
        var typeChanges = 0
        compose.setContent {
            KUfindersTheme {
                AdminPostListScreen(posts = emptyList(), hasNext = false, onMore = {}, postType = "FOUND", isLoading = false,
                    errorMessage = "게시글을 불러오지 못했습니다.", onBackClick = {}, onTypeChange = { typeChanges++ },
                    onDelete = {}, onRetry = { retries++ })
            }
        }
        compose.onNodeWithText("다시 시도").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(0, typeChanges)
        }
        compose.onNodeWithText("습득물").assertIsSelected()
        compose.onNodeWithText("분실물").assertIsNotSelected()
    }

    @Test fun reportFiltersExposeTheSelectedState() {
        compose.setContent {
            KUfindersTheme {
                AdminReportListScreen(onRetry = {}, reports = emptyList(), hasNext = false, onMore = {},
                    onBackClick = {}, onReportClick = {}, status = "RECEIVED")
            }
        }
        compose.onNodeWithText("미처리").assertIsSelected()
        compose.onNodeWithText("전체").assertIsNotSelected()
    }

    @Test fun dialogButtonsStayReachableAtDoubleFontScale() {
        var confirmed = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                KUfindersTheme {
                    FinderConfirmDialog(
                        title = "알림을 모두 삭제할까요?",
                        message = "목록의 기준 시점까지 받은 알림을 모두 읽음 처리하고 삭제합니다. ".repeat(40),
                        confirmText = "전체 삭제",
                        onDismiss = {},
                        onConfirm = { confirmed = true },
                    )
                }
            }
        }
        compose.onNodeWithText("전체 삭제").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }

    @Test fun longSenderNameLeavesRoomForThePreviewOnANarrowLargeFontRow() {
        val room = ChatRoomResponse(1, 1, 2, "2026-11-27T10:00:00", postType = "LOST", postTitle = "검은 지갑",
            lastMessage = "혹시 공학관에서 보셨나요?", lastMessageAt = "2026-11-27T10:00:00", lastMessageSenderId = 2)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                KUfindersTheme {
                    Box(Modifier.width(320.dp)) {
                        ChatListScreen(rooms = listOf(room), isLoading = false, errorMessage = null, onProfileClick = {},
                            onSearchClick = {}, onNotificationClick = {}, notificationCount = 0, onRoomClick = {},
                            authorInfoByRoomId = mapOf(1L to (2L to "홍길동홍길동홍길동홍길동")), currentUserId = 1)
                    }
                }
            }
        }
        val preview = compose.onNodeWithText("혹시 공학관에서 보셨나요?").assertIsDisplayed().fetchSemanticsNode()
        val minWidth = with(compose.density) { 40.dp.toPx() }
        assertTrue("preview width ${preview.size.width}px", preview.size.width >= minWidth)
    }
}
