package com.ku.lostandfound.ui.navigation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.ku.lostandfound.MainActivity
import com.ku.lostandfound.network.TokenManager
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class EdgeToEdgeSignupTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun signupFieldAndFooterFitAboveTheKeyboardWithoutExtraBottomInset() {
        TokenManager.init(InstrumentationRegistry.getInstrumentation().targetContext)
        TokenManager.clear()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText("회원가입").performScrollTo().performClick()
            compose.onNodeWithContentDescription("전체 동의").performScrollTo().performClick()
            compose.onNodeWithText("동의하고 계속하기").performClick()

            val field = compose.onNode(hasSetTextAction())
            val next = compose.onNodeWithText("다음")
            field.performScrollTo().assertIsDisplayed()
            val initialSafe = safeArea(scenario, imeVisible = false)
            val initialFieldBounds = field.fetchSemanticsNode().boundsInWindow
            val initialNextBounds = next.assertIsDisplayed().fetchSemanticsNode().boundsInWindow
            assertWithinSafeArea(initialFieldBounds, initialSafe)
            assertWithinSafeArea(initialNextBounds, initialSafe)

            try {
                field.performTouchInput { click() }
                val keyboardSafe = safeArea(scenario, imeVisible = true)
                assertTrue("The keyboard should shrink the safe viewport", keyboardSafe.bottom < initialSafe.bottom)
                val fieldBounds = field.assertIsDisplayed().fetchSemanticsNode().boundsInWindow
                assertWithinSafeArea(fieldBounds, keyboardSafe)
                assertTrue("The input was clipped by the reduced viewport", fieldBounds.height >= initialFieldBounds.height - 2f)
                field.performTextInput("김건국")
                next.assertIsEnabled()
                val nextBounds = next.assertIsDisplayed().fetchSemanticsNode().boundsInWindow
                assertWithinSafeArea(nextBounds, keyboardSafe)
                val initialGap = initialSafe.bottom - initialNextBounds.bottom
                val keyboardGap = keyboardSafe.bottom - nextBounds.bottom
                assertTrue("Unexpected footer gap with the keyboard: $keyboardGap px",
                    abs(keyboardGap - initialGap) <= 2f)
            } finally {
                scenario.onActivity { activity ->
                    WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                        .hide(WindowInsetsCompat.Type.ime())
                }
            }
        }
    }

    private fun safeArea(scenario: ActivityScenario<MainActivity>, imeVisible: Boolean): Rect {
        var area: Rect? = null
        compose.waitUntil(10_000) {
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                val insets = ViewCompat.getRootWindowInsets(decor) ?: return@onActivity
                if (insets.isVisible(WindowInsetsCompat.Type.ime()) != imeVisible) return@onActivity
                val system = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                val bottom = activity.windowManager.currentWindowMetrics.bounds.height() - max(system.bottom, ime.bottom)
                area = Rect(
                    system.left.toFloat(),
                    system.top.toFloat(),
                    (decor.width - system.right).toFloat(),
                    bottom.toFloat(),
                )
            }
            area != null
        }
        compose.waitForIdle()
        return checkNotNull(area)
    }

    private fun assertWithinSafeArea(bounds: Rect, area: Rect) {
        assertTrue("$bounds must fit inside $area", bounds.left >= area.left - 1f &&
            bounds.right <= area.right + 1f && bounds.top >= area.top - 1f && bounds.bottom <= area.bottom + 1f)
    }
}
