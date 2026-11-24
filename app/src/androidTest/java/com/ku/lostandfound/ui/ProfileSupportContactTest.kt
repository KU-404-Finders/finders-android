package com.ku.lostandfound.ui

import android.content.*
import android.os.Build
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ku.lostandfound.ui.profile.screen.ProfileScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileSupportContactTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val target: Context get() = compose.activity
    private fun show(context: Context = target) {
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                ProfileScreen(userName = "테스트", userEmail = "student@konkuk.ac.kr", myPosts = emptyList(),
                    onPostClick = {}, onShowAllClick = {}, onLogoutClick = {}, onWithdrawClick = {},
                    onWithdrawCompleteConfirm = {}, onFoundTabClick = {}, onLostTabClick = {}, onAddClick = {})
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("앱 버전", substring = true))
    }
    private fun openContact() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("문의하기"))
        compose.onNodeWithText("문의하기").performClick()
        compose.onNodeWithText("서비스 이용 중 불편한 사항이나 오류를 알려주세요.").assertIsDisplayed()
    }
    @Test fun contactDialogLaunchesMailtoAndVersionMatchesInstalledBuild() {
        var launched: Intent? = null
        val context = object : ContextWrapper(target) {
            override fun startActivity(intent: Intent) { launched = intent }
        }
        show(context)
        compose.onNodeWithText("michelle03212@gmail.com").assertDoesNotExist()
        val version = target.packageManager.getPackageInfo(target.packageName, 0).versionName.orEmpty()
        compose.onNodeWithText("앱 버전 $version").assertIsDisplayed()
        openContact()
        compose.onNodeWithText("michelle03212@gmail.com").assertIsDisplayed()
        compose.onNodeWithText("이메일 보내기").performClick()
        compose.runOnIdle {
            assertEquals(Intent.ACTION_SENDTO, launched?.action)
            assertEquals("mailto:michelle03212@gmail.com", launched?.dataString)
        }
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("michelle03212@gmail.com").assertDoesNotExist()
    }
    @Test fun copyButtonWritesOnlyTheOfficialAddressToClipboard() {
        val clipboard = target.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val previous = clipboard.primaryClip
        try {
            show()
            openContact()
            compose.onNodeWithText("이메일 주소 복사").performClick()
            compose.runOnIdle {
                assertEquals("michelle03212@gmail.com", clipboard.primaryClip?.getItemAt(0)?.text.toString())
            }
        } finally {
            if (previous != null) clipboard.setPrimaryClip(previous)
            else if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
            else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
    @Test fun unavailableMailAppKeepsDialogOpenAndBackDismissesIt() {
        val context = object : ContextWrapper(target) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        show(context)
        openContact()
        compose.onNodeWithText("이메일 보내기").performClick()
        compose.onNodeWithText("이메일 주소 복사").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("michelle03212@gmail.com").assertDoesNotExist()
    }
}
