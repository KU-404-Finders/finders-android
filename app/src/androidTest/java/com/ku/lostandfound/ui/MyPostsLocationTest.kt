package com.ku.lostandfound.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.ku.lostandfound.network.MyFoundItemData
import com.ku.lostandfound.network.MyLostItemData
import com.ku.lostandfound.ui.profile.screen.MyPostsScreen
import com.ku.lostandfound.ui.profile.screen.MyPostsType
import com.ku.lostandfound.ui.profile.viewmodel.MyPostsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyPostsLocationTest {
    @get:Rule val compose = createComposeRule()

    private fun show(type: MyPostsType = MyPostsType.FOUND) {
        compose.setContent {
            MyPostsScreen(type = type, uiState = MyPostsUiState.Idle,
                lostItems = listOf(MyLostItemData(10, "분실물", "기타", null, "SEARCHING", "2026-11-25")),
                foundItems = listOf(
                    MyFoundItemData(1, "실외 습득물", "기타", null, "SEARCHING", "OUTDOOR", listOf("학생회관", "인문학관"), "2026-11-25"),
                    MyFoundItemData(2, "실내 습득물", "기타", null, "SEARCHING", "INDOOR", emptyList(), "2026-11-25"),
                    MyFoundItemData(3, "미지원 유형", "기타", null, "SEARCHING", "UNKNOWN", listOf("도서관"), "2026-11-25"),
                    MyFoundItemData(4, "위치 없음", "기타", null, "SEARCHING", "", emptyList(), "2026-11-25"),
                    Gson().fromJson("""{"id":5,"title":"누락 응답","kind":"기타","itemStatus":"SEARCHING","createdAt":"2026-11-25","locationType":null,"associatedBuildingNames":null}""", MyFoundItemData::class.java)),
                onBackClick = {}, onLostItemClick = {}, onFoundItemClick = {})
        }
    }

    @Test fun foundHistoryShowsKoreanLabelsAndHandlesMissingLocation() {
        show()
        compose.onNodeWithText("실외 · 학생회관, 인문학관").assertIsDisplayed()
        compose.onNodeWithText("실내").assertIsDisplayed()
        compose.onNodeWithText("도서관").assertIsDisplayed()
        compose.onNodeWithText("위치 없음").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("누락 응답"))
        compose.onNodeWithText("누락 응답").assertIsDisplayed()
        for (raw in listOf("INDOOR", "OUTDOOR", "UNKNOWN", "null")) {
            compose.onNodeWithText(raw, substring = true).assertDoesNotExist()
        }
    }

    @Test fun lostHistoryRetainsContentWithoutInventingLocation() {
        show(MyPostsType.LOST)
        compose.onNodeWithText("나의 분실물 등록 내역").assertIsDisplayed()
        compose.onNodeWithText("분실물").assertIsDisplayed()
        compose.onNodeWithText(" · ", substring = true).assertDoesNotExist()
    }
}
