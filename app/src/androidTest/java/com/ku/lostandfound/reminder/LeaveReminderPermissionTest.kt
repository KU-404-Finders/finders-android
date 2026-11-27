package com.ku.lostandfound.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ku.lostandfound.network.TokenManager
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LeaveReminderPermissionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun disabledAndDeniedLocationNeverShowActiveMonitoring() {
        val context = compose.activity
        assumeTrue(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED)
        lateinit var controller: LeaveReminderController
        lateinit var initial: LeaveReminderSettings
        compose.runOnUiThread {
            controller = LeaveReminderController.get(context)
            assumeFalse(TokenManager.isLoggedIn())
            initial = controller.settings.value
            controller.update(initial.copy(enabled = false))
        }
        compose.setContent { LeaveReminderScreen(onBack = {}) }
        compose.onNodeWithText("꺼짐").assertIsDisplayed()
        compose.onNodeWithText("예방 알림 사용").performClick()
        compose.onNodeWithText("분실물 예방 알림에 위치를 사용해요").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("꺼짐").assertIsDisplayed()

        try {
            compose.runOnIdle {
                check(TokenManager.saveNewSessionTokens(TokenManager.sessionGeneration(), "fixture-a", "fixture-r"))
                controller.update(initial.copy(enabled = true, buildingIds = setOf(controller.regions.first().id)))
            }
            compose.waitUntil(5_000) { controller.status.value == ReminderStatus.PRECISE_LOCATION }
            compose.onNodeWithText("정확한 위치 권한이 필요해요.").assertIsDisplayed()
            compose.onNodeWithText(ReminderStatus.MONITORING.label).assertDoesNotExist()
            compose.onNodeWithText("예방 알림 사용").performClick()
            compose.waitUntil(5_000) { controller.status.value == ReminderStatus.OFF }
            assertEquals(false, controller.settings.value.enabled)
        } finally {
            compose.runOnIdle {
                TokenManager.clear()
                controller.update(initial)
            }
        }
    }

    @Test fun groupedSettingsKeepSavedValuesAndPostPreviewOnReminderChannel() {
        val context = compose.activity
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val captureUi = InstrumentationRegistry.getArguments().getString("captureReminderUi") == "true"
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        lateinit var initial: LeaveReminderSettings
        lateinit var fixture: LeaveReminderSettings
        lateinit var controller: LeaveReminderController
        compose.runOnUiThread {
            controller = LeaveReminderController.get(context)
            assumeFalse(TokenManager.isLoggedIn())
            initial = controller.settings.value
            fixture = initial.copy(
                enabled = true,
                buildingIds = setOf(controller.regions.first().id),
                dwellMinutes = 30,
                items = listOf(ReminderItem("필통", ReminderCondition.ALWAYS)),
            )
            check(TokenManager.saveNewSessionTokens(TokenManager.sessionGeneration(), "fixture-a", "fixture-r"))
            controller.update(fixture)
        }
        try {
            compose.setContent { LeaveReminderScreen(onBack = {}) }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("방문 판단 기준"))
            compose.onNodeWithText("30분 이상").assertIsDisplayed()
            if (captureUi) saveScreenshot("reminder-settings")
            compose.onNodeWithText("알림 건물").performClick()
            compose.onNodeWithText("알림 건물").assertIsDisplayed()
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("테스트 알림 보내기"))
            compose.onNodeWithText("테스트 알림 보내기").assertIsDisplayed().performClick()
            compose.waitUntil(5_000) {
                manager.activeNotifications.any { it.notification.channelId == LeaveReminderController.CHANNEL }
            }
            val notification = manager.activeNotifications.single { it.notification.channelId == LeaveReminderController.CHANNEL }.notification
            assertEquals(Notification.CATEGORY_REMINDER, notification.category)
            assertEquals("소지품 챙기셨나요?", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals(fixture, LeaveReminderStore(context).settings())
            if (captureUi) {
                SystemClock.sleep(750)
                saveScreenshot("reminder-popup")
            }
        } finally {
            compose.runOnIdle {
                TokenManager.clear()
                controller.update(initial)
            }
        }
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(compose.activity.cacheDir, "$name.png").outputStream().use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
