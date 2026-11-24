package com.ku.lostandfound.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.notification.SwipeToHideNotification
import com.ku.lostandfound.ui.signup.screen.*
import com.ku.lostandfound.ui.signup.viewmodel.SignupViewmodel
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class ParityInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var sends = 0
    private var joins = 0
    private val vm = SignupViewmodel(object : SignupApiService {
        override suspend fun sendVerificationCode(email: String): Response<SignupResponse> {
            sends++; return Response.success(SignupResponse(true, ""))
        }
        override suspend fun register(request: SignupRequest): Response<SignupResponse> {
            joins++; return Response.success(SignupResponse(true, ""))
        }
    })
    @After fun close() { vm.viewModelScope.cancel() }

    @Test fun consentCheckboxNamesStatesAndViewActionAreIndependent() {
        val opened = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) { SignupTermsScreen(vm, {}, {}) }
        }
        val terms = compose.onNodeWithContentDescription("[필수] 이용약관에 동의합니다")
        val privacy = compose.onNodeWithContentDescription("[필수] 개인정보 처리방침에 동의합니다")
        terms.assertIsOff(); privacy.assertIsOff()
        compose.onAllNodesWithText("보기")[0].performClick()
        terms.assertIsOff()
        assertEquals(listOf(LegalLinks.TERMS), opened)
        terms.performClick().assertIsOn(); privacy.assertIsOff()
        privacy.performClick().assertIsOn()
        compose.onNodeWithText("동의하고 계속하기").assertIsEnabled()
    }

    @Test fun readThenRecomposeThenDeleteUsesLatestRowAndCallback() {
        val row = mutableStateOf(InboxNotificationData("one", "MATCH", sequence = 1))
        val version = mutableStateOf(1)
        var reads = 0
        var deletedVersion = 0
        compose.setContent {
            val renderedVersion = version.value
            SwipeToHideNotification(row.value, { deletedVersion = renderedVersion }, { reads++ }) {
                Box(Modifier.fillMaxWidth().height(80.dp).testTag("row")) { Text("알림") }
            }
        }
        compose.onNodeWithTag("row").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, reads); row.value = row.value.copy(readAt = "confirmed"); version.value = 2 }
        compose.onNodeWithTag("row").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(2, deletedVersion); assertEquals(1, reads) }
    }

    @Test fun restoredRouteWithoutConsentDoesNotComposeProtectedContent() {
        var redirects = 0
        compose.setContent { SignupConsentGuard(vm, { redirects++ }) { Text("protected") } }
        compose.onNodeWithText("protected").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, redirects); vm.termsAccepted = true; vm.privacyAccepted = true }
        compose.onNodeWithText("protected").assertIsDisplayed()
    }

    @Test fun emailButtonAndImeRequireConsent() {
        vm.emailPrefix = "student"
        compose.setContent { SignupEmailScreen(vm) }
        compose.onNodeWithText("인증 메일 받기").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(0, sends) }
    }

    @Test fun codeButtonAndImeRequireConsent() {
        vm.verificationCode = "123456"
        compose.setContent { SignupCodeScreen(vm) }
        compose.onNodeWithText("인증하기").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(0, joins) }
    }

    @Test fun nameButtonAndImeRequireConsent() {
        vm.name = "홍길동"
        var navigation = 0
        compose.setContent { SignupNameScreen(vm, { navigation++ }) }
        compose.onNodeWithText("다음").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(0, navigation) }
    }

    @Test fun passwordButtonAndImeRequireConsent() {
        vm.password = "abc123"
        var navigation = 0
        compose.setContent { SignupPwScreen(vm, { navigation++ }) }
        compose.onNodeWithText("다음").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(0, navigation) }
    }

    @Test fun profileKeepsVersionWithoutDeveloperEmailPlaceholder() {
        compose.setContent {
            com.ku.lostandfound.ui.profile.screen.ProfileScreen(
                userName = "테스트", userEmail = "student@konkuk.ac.kr", myPosts = emptyList(),
                onPostClick = {}, onShowAllClick = {}, onLogoutClick = {}, onWithdrawClick = {},
                onWithdrawCompleteConfirm = {}, onFoundTabClick = {}, onLostTabClick = {}, onAddClick = {})
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("앱 버전", substring = true))
        compose.onNodeWithText("앱 버전", substring = true).assertIsDisplayed()
        compose.onNodeWithText("michelle03212@gmail.com").assertDoesNotExist()
        compose.onNodeWithText("작성 필요", substring = true).assertDoesNotExist()
        compose.onNodeWithText("이메일:", substring = true).assertDoesNotExist()
    }
}
