package com.ku.lostandfound.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.chat.viewmodel.ChatViewModel
import com.ku.lostandfound.ui.passwordreset.viewmodel.PasswordResetViewModel
import com.ku.lostandfound.ui.signup.viewmodel.SignupViewmodel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class AuthNavigationRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: NavHostController
    private lateinit var reset: PasswordResetViewModel
    private lateinit var signup: SignupViewmodel
    private lateinit var chat: ChatViewModel
    private var isFreshLaunch = true
    private val destinations = mutableListOf<String?>()
    private val api = object : PasswordResetApiService {
        override suspend fun requestReset(request: PasswordResetRequest) = Response.success(PasswordResetResponse(true))
        override suspend fun verifyCode(request: PasswordResetVerifyRequest) =
            Response.success(PasswordResetVerifyResponse(true, data = PasswordResetTokenData("fixture-token")))
        override suspend fun confirmReset(request: PasswordResetConfirmRequest): Response<PasswordResetResponse> {
            assertEquals("fixture-token", request.resetToken)
            return Response.success(PasswordResetResponse(true))
        }
    }
    private val content: @Composable () -> Unit = {
        val controller = rememberNavController()
        DisposableEffect(controller) {
            val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                destinations += destination.route
            }
            controller.addOnDestinationChangedListener(listener)
            onDispose { controller.removeOnDestinationChangedListener(listener) }
        }
        nav = controller
        MainNavGraph(controller, padding = PaddingValues(), isFreshLaunch = isFreshLaunch, chatViewModel = chat)
    }

    @Before fun setUp() {
        TokenManager.init(compose.activity.applicationContext)
        TokenManager.clear()
        compose.activityRule.scenario.onActivity(::installModels)
        compose.setContent(content)
        assertRoute(Route.Login)
    }

    private fun installModels(activity: ComponentActivity) {
        reset = ViewModelProvider(activity, viewModelFactory {
            initializer { PasswordResetViewModel(api) }
        })[PasswordResetViewModel::class.java]
        signup = ViewModelProvider(activity)[SignupViewmodel::class.java]
        chat = ViewModelProvider(activity)[ChatViewModel::class.java]
    }

    private fun recreate(clearMemory: Boolean = false) {
        val scenario = compose.activityRule.scenario
        scenario.onActivity {
            isFreshLaunch = false
            destinations.clear()
            if (clearMemory) it.viewModelStore.clear()
        }
        scenario.recreate()
        scenario.onActivity {
            installModels(it)
            it.setContent(content = content)
        }
        compose.waitForIdle()
    }

    private fun assertRoute(route: Route) {
        compose.waitUntil(10_000) { ::nav.isInitialized && nav.currentDestination?.route == route.route }
    }

    private fun assertLoginOnly() {
        assertRoute(Route.Login)
        compose.runOnIdle {
            assertNull(nav.previousBackStackEntry)
            assertFalse(nav.popBackStack(Route.PasswordResetEmail.route, false))
            assertFalse(nav.popBackStack(Route.SignupTerms.route, false))
            assertEquals(Route.Login.route, nav.currentDestination?.route)
        }
    }

    private fun openResetCode() {
        compose.runOnIdle {
            reset.updateEmail("fixture")
            nav.navigate(Route.PasswordResetEmail.route)
            reset.requestCode { nav.navigate(Route.PasswordResetCode.route) }
        }
        assertRoute(Route.PasswordResetCode)
    }

    private fun openResetPassword() {
        openResetCode()
        compose.runOnIdle {
            reset.updateAuthCode("123456")
            reset.verifyCode { nav.navigate(Route.PasswordResetPassword.route) }
        }
        assertRoute(Route.PasswordResetPassword)
    }

    @Test fun signupCompletionButtonClearsSignupAndBackStack() {
        compose.runOnIdle {
            signup.termsAccepted = true
            signup.privacyAccepted = true
            signup.name = "fixture"
            signup.password = "Fixture1"
            listOf(Route.SignupTerms, Route.SignupName, Route.SignupPw, Route.SignupEmail, Route.SignupCode,
                Route.SignupFinish).forEach { nav.navigate(it.route) }
        }
        compose.onNodeWithText("로그인하기").assertIsDisplayed().performClick()
        assertLoginOnly()
        compose.runOnIdle {
            assertFalse(signup.hasRequiredConsent)
            assertEquals("", signup.name)
            assertEquals("", signup.password)
            assertEquals("", signup.verificationCode)
        }
    }

    @Test fun backgroundReturnAndActivityRecreationPreserveValidResetStep() {
        openResetPassword()
        compose.runOnIdle { reset.updateNewPassword("Fixture1") }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertRoute(Route.PasswordResetPassword)
        recreate()
        assertRoute(Route.PasswordResetPassword)
        compose.runOnIdle {
            assertTrue(reset.hasPasswordResetCredentials)
            assertEquals("Fixture1", reset.newPassword)
        }
    }

    @Test fun restoredCodeRouteWithoutInMemoryEmailReturnsToLogin() {
        openResetCode()
        recreate(clearMemory = true)
        assertLoginOnly()
        compose.runOnIdle {
            assertTrue(Route.PasswordResetCode.route in destinations)
            assertEquals("", reset.emailInput)
        }
    }

    @Test fun restoredPasswordRouteWithoutInMemoryTokenReturnsToLogin() {
        openResetPassword()
        recreate(clearMemory = true)
        assertLoginOnly()
        compose.runOnIdle {
            assertTrue(Route.PasswordResetPassword.route in destinations)
            assertFalse(reset.hasPasswordResetCredentials)
        }
    }

    @Test fun passwordRouteWithEmailButMissingTokenReturnsToLogin() {
        openResetCode()
        compose.runOnIdle { nav.navigate(Route.PasswordResetPassword.route) }
        assertLoginOnly()
        compose.runOnIdle { assertEquals("", reset.emailInput) }
    }

    @Test fun passwordResetCompletionButtonReturnsToLoginAndClearsInput() {
        openResetPassword()
        compose.runOnIdle {
            reset.updateNewPassword("Fixture1")
            reset.updateNewPasswordConfirm("Fixture1")
        }
        compose.onNodeWithText("비밀번호 변경").performClick()
        // iOS parity: a completion step is shown before returning to the login.
        compose.onNodeWithText("새 비밀번호로 다시 로그인해주세요.").assertIsDisplayed()
        assertRoute(Route.PasswordResetPassword)
        compose.onNodeWithText("로그인하기").performClick()
        assertLoginOnly()
        compose.runOnIdle {
            assertEquals("", reset.emailInput)
            assertEquals("", reset.authCode)
            assertEquals("", reset.newPassword)
            assertFalse(reset.hasPasswordResetCredentials)
        }
    }
}
