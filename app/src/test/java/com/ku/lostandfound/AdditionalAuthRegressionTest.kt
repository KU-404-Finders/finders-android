package com.ku.lostandfound

import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.login.viewmodel.LoginViewModel
import com.ku.lostandfound.notification.notificationAccountKey
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import retrofit2.Response

class AdditionalAuthRegressionTest : StabilityFixture() {
    @Test fun invalidatedLoginWaitingForLogoutDoesNotSendRequest() {
        val release = CompletableDeferred<Unit>()
        val cleanup = CoroutineScope(main).launch {
            AuthRequestOrder.shared.logout({}, { release.await() })
        }
        dispatch()
        var calls = 0
        val api = object : AuthApiService by mock(AuthApiService::class.java) {
            override suspend fun login(request: LoginRequest): Response<LoginResponse> {
                calls++
                return Response.success(LoginResponse(false, "rejected", null))
            }
        }
        val vm = LoginViewModel(api)
        try {
            vm.login("a", "password")
            dispatch()
            vm.resetUiState()
            release.complete(Unit)
            dispatch()
            assertEquals(0, calls)
            vm.login("b", "password")
            dispatch()
            assertEquals(1, calls)
        } finally { release.complete(Unit); cleanup.cancel(); vm.viewModelScope.cancel() }
    }

    @Test fun withdrawalUsesRefreshedTokenFromSameSessionWithoutPushCleanup() {
        val headers = mutableListOf<String>()
        val api = object : AuthApiService by mock(AuthApiService::class.java) {
            override suspend fun withdraw(authorization: String, withdrawal: WithdrawalRequest): Response<ApiMessageResponse> {
                headers += authorization
                return Response.success(ApiMessageResponse(false, "rejected"))
            }
        }
        val vm = LoginViewModel(api) { _, _ -> fail("Withdrawal must not pre-delete push devices") }
        try {
            vm.withdraw { fail() }
            val old = TokenManager.credentials()
            assertTrue(TokenManager.saveRefreshedTokens(old, "new-access", "new-refresh"))
            dispatch()
            assertEquals(listOf("Bearer new-access"), headers)
        } finally { vm.viewModelScope.cancel() }
    }

    @Test fun accountSwitchBeforeWithdrawalDispatchSuppressesWithdrawal() {
        var calls = 0
        val api = object : AuthApiService by mock(AuthApiService::class.java) {
            override suspend fun withdraw(authorization: String, withdrawal: WithdrawalRequest): Response<ApiMessageResponse> {
                calls++
                return Response.success(ApiMessageResponse(false, "rejected"))
            }
        }
        val vm = LoginViewModel(api) { _, _ -> fail("Withdrawal must not pre-delete push devices") }
        try {
            vm.withdraw { fail() }
            login("b")
            dispatch()
            assertEquals(0, calls)
        } finally { vm.viewModelScope.cancel() }
    }

    @Test fun queuedNotificationOwnerSurvivesRefreshButDistinguishesAccounts() {
        fun jwt(subject: String, expiry: Long): String = "e30." + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"sub\":\"$subject\",\"exp\":$expiry}".toByteArray()) + ".signature"
        assertEquals(notificationAccountKey(jwt("a", 1), 1), notificationAccountKey(jwt("a", 2), 2))
        assertNotEquals(notificationAccountKey(jwt("a", 1), 1), notificationAccountKey(jwt("b", 1), 1))
        assertNull(notificationAccountKey(null, 1))
        assertNotEquals(notificationAccountKey("invalid", 1), notificationAccountKey("invalid", 2))
    }
}
