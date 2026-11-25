package com.ku.lostandfound.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ku.lostandfound.ui.chat.screen.ChatListScreen
import com.ku.lostandfound.ui.chat.screen.ChatRoomScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatTabHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chatListUsesMainHeaderActionsAndHasNoBackButton() {
        val selections = mutableListOf<String>()
        compose.setContent {
            ChatListScreen(
                rooms = emptyList(),
                isLoading = false,
                errorMessage = null,
                onProfileClick = { selections += "profile" },
                onSearchClick = { selections += "search" },
                onNotificationClick = { selections += "notifications" },
                notificationCount = 0,
                onRoomClick = {},
            )
        }
        compose.onNodeWithText("채팅").assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로가기").assertDoesNotExist()
        compose.onNodeWithContentDescription("마이페이지").performClick()
        compose.onNodeWithContentDescription("알림함").performClick()
        compose.onNodeWithText("⌕").performClick()
        compose.runOnIdle { assertEquals(listOf("profile", "notifications", "search"), selections) }
    }

    @Test fun chatRoomRetainsItsBackButton() {
        var backClicks = 0
        compose.setContent {
            ChatRoomScreen(
                roomId = 1,
                roomTitle = "채팅방",
                messages = emptyList(),
                currentUserId = null,
                isLoading = false,
                errorMessage = null,
                onBackClick = { backClicks++ },
                isConnected = true,
                connectionMessage = null,
                onSendMessage = { false },
            )
        }
        compose.onNodeWithText("채팅방").assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        compose.runOnIdle { assertEquals(1, backClicks) }
    }
}
