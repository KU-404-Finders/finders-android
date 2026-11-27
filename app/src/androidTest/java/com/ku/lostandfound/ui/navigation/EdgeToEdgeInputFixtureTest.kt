package com.ku.lostandfound.ui.navigation

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.ku.lostandfound.MainActivity
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.ui.chat.screen.ChatRoomScreen
import com.ku.lostandfound.ui.post.screen.PostWriteScreen
import com.ku.lostandfound.ui.theme.KUfindersTheme
import com.ku.lostandfound.viewmodel.PostWriteViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class EdgeToEdgeInputFixtureTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Before fun clearSession() {
        TokenManager.init(InstrumentationRegistry.getInstrumentation().targetContext)
        TokenManager.clear()
    }

    @Test fun chatInputAndSendButtonStayAboveTheKeyboard() {
        var sent = ""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.showScreen {
                ChatRoomScreen(
                    roomId = 1,
                    roomTitle = "fixture",
                    messages = emptyList(),
                    currentUserId = 1,
                    isLoading = false,
                    errorMessage = null,
                    onBackClick = {},
                    isConnected = true,
                    connectionMessage = null,
                    onSendMessage = {
                        sent = it
                        true
                    },
                )
            }
            val field = compose.onNode(hasSetTextAction())
            field.performTouchInput { click() }
            val keyboardTop = awaitKeyboardTop(scenario)
            field.performTextInput("hello")
            assertAboveKeyboard(field, keyboardTop)
            val send = compose.onNodeWithText("➤")
            assertAboveKeyboard(send, keyboardTop)
            send.performClick()
            compose.runOnIdle { assertEquals("hello", sent) }
        }
    }

    @Test fun postWriteAndEditAcceptContentAboveTheKeyboard() {
        for (editing in listOf(false, true)) {
            val viewModel = PostWriteViewModel()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.showScreen {
                    PostWriteScreen(
                        viewModel = viewModel,
                        onBackClick = {},
                        onAddLocationClick = {},
                        onSubmitClick = {},
                        title = if (editing) "게시글 수정" else "새 글 쓰기",
                        allowTypeChange = !editing,
                    )
                }
                val body = compose.onAllNodes(hasSetTextAction())[1]
                body.performScrollTo().performTouchInput { click() }
                val keyboardTop = awaitKeyboardTop(scenario)
                body.performTextInput("sample")
                assertAboveKeyboard(body, keyboardTop)
                compose.runOnIdle { assertEquals("sample", viewModel.content) }
            }
        }
    }

    private fun ActivityScenario<MainActivity>.showScreen(content: @Composable () -> Unit) {
        onActivity { activity ->
            activity.setContent {
                KUfindersTheme {
                    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                        Box(Modifier.padding(padding).consumeWindowInsets(padding)) { content() }
                    }
                }
            }
        }
    }

    private fun awaitKeyboardTop(scenario: ActivityScenario<MainActivity>): Float {
        var top: Float? = null
        compose.waitUntil(10_000) {
            scenario.onActivity { activity ->
                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView) ?: return@onActivity
                val type = WindowInsetsCompat.Type.ime()
                if (insets.isVisible(type)) {
                    top = (activity.windowManager.currentWindowMetrics.bounds.height() - insets.getInsets(type).bottom)
                        .toFloat()
                }
            }
            top != null
        }
        compose.waitForIdle()
        return checkNotNull(top)
    }

    private fun assertAboveKeyboard(node: SemanticsNodeInteraction, keyboardTop: Float) {
        val bounds = node.assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertTrue("$bounds must fit above the IME at $keyboardTop", bounds.height > 0f && bounds.bottom <= keyboardTop + 1f)
    }
}
