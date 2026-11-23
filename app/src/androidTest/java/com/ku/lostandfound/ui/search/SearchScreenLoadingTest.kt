package com.ku.lostandfound.ui.search

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.ui.search.screen.SearchScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenLoadingTest {
    @get:Rule val compose = createComposeRule()
    private val loading = mutableStateOf(true)
    private val error = mutableStateOf<String?>(null)
    private val posts = (1..30).map {
        BoardPost("$it", PostType.LOST, title = "지갑 $it", category = "기타", content = "")
    }
    private val progress = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
    private var clicks = 0
    private var retries = 0
    private var backs = 0

    private fun show(items: List<BoardPost> = posts) {
        compose.setContent {
            SearchScreen(items, loading.value, error.value,
                onRetry = { retries++ }, onBackClick = { backs++ }, onPostClick = { clicks++ })
        }
    }

    private fun search() { compose.onNode(hasSetTextAction()).performTextInput("지갑") }

    @Test fun blankQueryKeepsGuideAndBackAction() {
        show()
        compose.onNodeWithText("검색어를 입력해보세요").assertIsDisplayed()
        compose.onNodeWithText("지갑 1").assertDoesNotExist()
        compose.onNodeWithText("‹").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun emptyLoadingKeepsExistingMessageAndShowsEmptyResultAfterCompletion() {
        show(emptyList())
        search()
        compose.onNodeWithText("게시글을 불러오는 중입니다.").assertIsDisplayed()
        compose.runOnIdle { loading.value = false }
        compose.onNodeWithText("게시글을 불러오는 중입니다.").assertDoesNotExist()
        compose.onNodeWithText("검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test fun partialResultsStayVisibleAndClickableUntilLoadingCompletes() {
        show()
        search()
        compose.onNode(progress).assertIsDisplayed()
        compose.onNodeWithText("지갑 1").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, clicks); loading.value = false }
        compose.onNode(progress).assertDoesNotExist()
        compose.onNodeWithText("지갑 1").assertIsDisplayed()
    }

    @Test fun partialFailureKeepsResultsRetryAndScrollWorking() {
        show()
        search()
        compose.runOnIdle { loading.value = false; error.value = "일부 게시글 조회 실패" }
        compose.onNode(progress).assertDoesNotExist()
        compose.onNodeWithText("일부 게시글 조회 실패").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("지갑 1").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(29)
        compose.onNodeWithText("지갑 30").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun scrollingDuringLoadingStillAllowsCardClick() {
        show()
        search()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(29)
        compose.onNode(progress).assertIsDisplayed()
        compose.onNodeWithText("지갑 30").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.runOnIdle { loading.value = false }
        compose.onNode(progress).assertDoesNotExist()
        compose.onNodeWithText("지갑 30").assertIsDisplayed()
    }
}
