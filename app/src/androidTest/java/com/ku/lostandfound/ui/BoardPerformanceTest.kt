package com.ku.lostandfound.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.ui.lost.screen.LostBoardScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Counts actual list work; this is not a frame-rate benchmark. */
@RunWith(AndroidJUnit4::class)
class BoardPerformanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingCategoryMenuDoesNotRescanPosts() {
        val reads = AtomicInteger()
        val rows = List(1000) { BoardPost(id = "$it", type = PostType.LOST, title = "글 $it", category = "우산", content = "") }
        val counted = object : AbstractList<BoardPost>() {
            override val size get() = rows.size
            override fun get(index: Int): BoardPost { reads.incrementAndGet(); return rows[index] }
        }
        compose.setContent {
            LostBoardScreen(posts = counted, onPostClick = {}, onAddClick = {},
                onFoundTabClick = {}, onLostTabClick = {}, onProfileClick = {})
        }
        compose.waitForIdle()
        val before = reads.get()
        compose.onNodeWithContentDescription("카테고리 필터").performClick()
        compose.onNodeWithText("전자기기").assertIsDisplayed()
        compose.runOnIdle {
            val extra = reads.get() - before
            println("PERF category_menu extra_source_reads=$extra rows=${rows.size}")
            assertEquals(0, extra)
        }
    }

    @Test fun refreshAndInsertionKeepVisiblePostAndScrollAnchor() {
        val rows = mutableStateOf(List(80) {
            BoardPost(id = "$it", type = PostType.LOST, title = "글 $it", category = "우산", content = "")
        })
        val loading = mutableStateOf(false)
        val error = mutableStateOf<String?>(null)
        compose.setContent {
            LostBoardScreen(posts = rows.value, isLoading = loading.value, errorMessage = error.value,
                onPostClick = {}, onAddClick = {}, onFoundTabClick = {}, onLostTabClick = {}, onProfileClick = {})
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("글 40"))
        compose.onNodeWithText("글 40").assertIsDisplayed()
        compose.runOnIdle { loading.value = true }
        compose.onNodeWithText("글 40").assertIsDisplayed()
        compose.runOnIdle {
            rows.value = listOf(BoardPost(id = "new", type = PostType.LOST, title = "새 글", category = "우산", content = "")) + rows.value
            loading.value = false
            error.value = "offline"
        }
        compose.onNodeWithText("글 40").assertIsDisplayed()
    }
}
