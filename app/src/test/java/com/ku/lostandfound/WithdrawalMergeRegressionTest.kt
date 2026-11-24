package com.ku.lostandfound

import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.login.viewmodel.*
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class WithdrawalMergeRegressionTest : StabilityFixture() {
    private val models = mutableListOf<LoginViewModel>()
    @After fun close() { models.forEach { it.viewModelScope.cancel() } }
    private fun success() = Response.success(ApiMessageResponse(true, ""))
    private fun suspended(token: String = "withdraw-a", code: Int = 403): Response<LoginResponse> {
        val data = TokenData(null, null, suspended = true, withdrawalToken = token, userId = 10)
        return if (code == 200) Response.success(LoginResponse(false, "suspended", data)) else Response.error(code,
            com.google.gson.Gson().toJson(LoginResponse(false, "suspended", data)).toResponseBody("application/json".toMediaType()))
    }
    private inner class Account {
        var loginReply = Response.success(LoginResponse(true, "", TokenData("normal", "normal-refresh")))
        val withdrawals = mutableListOf<Pair<String, WithdrawalRequest>>()
        val logouts = mutableListOf<String>()
        var pushes = 0
        var finished = 0
        var withdrawal: suspend () -> Response<ApiMessageResponse> = { success() }
        var push: suspend (String, Long) -> Unit = { _, _ -> }
        val api = object : AuthApiService by mock(AuthApiService::class.java) {
            override suspend fun login(request: LoginRequest) = loginReply
            override suspend fun withdraw(authorization: String, withdrawal: WithdrawalRequest): Response<ApiMessageResponse> {
                withdrawals += authorization to withdrawal
                return this@Account.withdrawal()
            }
            override suspend fun logout(authorization: String): Response<ApiMessageResponse> {
                logouts += authorization; return success()
            }
        }
        val vm = LoginViewModel(api) { header, generation -> pushes++; push(header, generation) }.also(models::add)
        fun suspendLogin(token: String = "withdraw-a", code: Int = 403) {
            loginReply = suspended(token, code); vm.login("suspended", "password"); dispatch()
            assertEquals(LoginUiState.Suspended, vm.uiState)
        }
        fun withdraw() { vm.withdraw { finished++ }; dispatch() }
    }

    @Test fun normalLoginStillStoresOrdinarySession() {
        val h = Account(); h.vm.login("student", "password"); dispatch()
        assertEquals(LoginUiState.Success, h.vm.uiState)
        assertEquals("normal", TokenManager.accessToken)
        assertEquals("normal-refresh", TokenManager.refreshToken)
        assertFalse(h.vm.hasSuspendedWithdrawal)
    }
    @Test fun suspendedFailureEnvelopeOverHttpSuccessIsAlsoHandled() {
        val h = Account(); h.suspendLogin(code = 200)
        assertTrue(h.vm.hasSuspendedWithdrawal); assertEquals("a", TokenManager.accessToken)
    }
    @Test fun suspendedLoginKeepsDedicatedCredentialOutOfOrdinaryStore() {
        TokenManager.clear()
        val h = Account(); h.suspendLogin()
        val generation = TokenManager.sessionGeneration()
        assertNull(TokenManager.accessToken); assertNull(TokenManager.refreshToken)
        assertTrue(h.vm.hasSuspendedWithdrawal)
        h.withdraw()
        assertEquals("Bearer withdraw-a", h.withdrawals.single().first)
        assertEquals(WithdrawalRequest(generation, WithdrawalTokenKind.DEDICATED), h.withdrawals.single().second)
    }
    @Test fun suspendedSuccessEnvelopeCannotInstallOrdinaryTokens() {
        val h = Account()
        h.loginReply = Response.success(LoginResponse(true, "", TokenData("must-not-store", "must-not-store",
            suspended = true, withdrawalToken = "dedicated")))
        h.vm.login("suspended", "password"); dispatch()
        assertEquals(LoginUiState.Suspended, h.vm.uiState)
        assertEquals("a", TokenManager.accessToken); assertEquals("refresh-a", TokenManager.refreshToken)
    }
    @Test fun suspendedResponseWithoutDedicatedTokenCannotFallBackToNormalLogin() {
        val h = Account()
        h.loginReply = Response.success(LoginResponse(true, "", TokenData("must-not-store", "must-not-store", suspended = true)))
        h.vm.login("suspended", "password"); dispatch(); h.withdraw()
        assertTrue(h.vm.uiState is LoginUiState.Error)
        assertEquals("a", TokenManager.accessToken); assertTrue(h.withdrawals.isEmpty())
    }
    @Test fun incompleteNormalLoginDoesNotInstallPartialTokens() {
        val h = Account(); h.loginReply = Response.success(LoginResponse(true, "", TokenData("partial", null)))
        h.vm.login("student", "password"); dispatch()
        assertTrue(h.vm.uiState is LoginUiState.Error)
        assertEquals("a", TokenManager.accessToken)
    }
    @Test fun normalWithdrawalCapturesOwnerAndDoesNotPreUnregisterPush() {
        val h = Account(); val generation = TokenManager.sessionGeneration(); h.withdraw()
        assertEquals("Bearer a", h.withdrawals.single().first)
        assertEquals(WithdrawalRequest(generation, WithdrawalTokenKind.SESSION), h.withdrawals.single().second)
        assertEquals(0, h.pushes); assertEquals(1, h.finished)
        assertNull(TokenManager.accessToken); assertFalse(h.vm.isWithdrawing)
        assertEquals(LoginUiState.Idle, h.vm.uiState)
    }
    @Test fun suspendedWithdrawalCompletesAndDropsMemoryCredential() {
        TokenManager.clear()
        val h = Account(); h.suspendLogin(); h.withdraw()
        assertEquals(LoginUiState.WithdrawalComplete, h.vm.uiState)
        assertFalse(h.vm.hasSuspendedWithdrawal); assertFalse(h.vm.isWithdrawing)
        assertNull(TokenManager.accessToken); assertEquals(0, h.pushes); assertEquals(1, h.finished)
    }
    @Test fun dedicatedHttpFailureRetainsCredentialForExplicitRetry() {
        val h = Account(); h.suspendLogin()
        h.withdrawal = { Response.error(503, "".toResponseBody()) }; h.withdraw()
        assertTrue(h.vm.uiState is LoginUiState.Error)
        assertTrue(h.vm.hasSuspendedWithdrawal); assertFalse(h.vm.isWithdrawing)
        h.withdrawal = { success() }; h.withdraw()
        assertEquals(listOf("Bearer withdraw-a", "Bearer withdraw-a"), h.withdrawals.map { it.first })
        assertEquals(1, h.finished)
    }
    @Test fun dedicatedNetworkFailureCanRetryWithoutOrdinarySession() {
        TokenManager.clear()
        val h = Account(); h.suspendLogin(); h.withdrawal = { throw java.io.IOException() }; h.withdraw()
        assertFalse(h.vm.isWithdrawing); assertTrue(h.vm.hasSuspendedWithdrawal)
        assertNull(TokenManager.accessToken)
        h.withdrawal = { success() }; h.withdraw(); assertEquals(1, h.finished)
    }
    @Test fun cancellationReleasesProgressWithoutSwallowingItAndAllowsRetry() {
        val h = Account(); h.suspendLogin(); h.withdrawal = { throw CancellationException("cancel") }; h.withdraw()
        assertFalse(h.vm.isWithdrawing); assertEquals(LoginUiState.Suspended, h.vm.uiState)
        assertTrue(h.vm.hasSuspendedWithdrawal); assertEquals(0, h.finished)
        h.withdrawal = { success() }; h.withdraw(); assertEquals(1, h.finished)
    }
    @Test fun duplicateWithdrawalIsSentOnlyOnce() {
        val h = Account(); val release = CompletableDeferred<Unit>()
        h.withdrawal = { release.await(); success() }
        h.vm.withdraw { h.finished++ }; h.vm.withdraw { fail() }; dispatch()
        assertEquals(1, h.withdrawals.size); assertTrue(h.vm.isWithdrawing)
        release.complete(Unit); dispatch(); assertEquals(1, h.finished)
    }
    @Test fun oldSuccessCannotClearExternallyInstalledAccount() {
        val h = Account(); val release = CompletableDeferred<Unit>()
        h.withdrawal = { release.await(); success() }; h.withdraw()
        login("b"); release.complete(Unit); dispatch()
        assertEquals("b", TokenManager.accessToken); assertEquals(0, h.finished)
        assertFalse(h.vm.isWithdrawing)
    }
    @Test fun oldFailureCannotPublishErrorIntoNewSession() {
        val h = Account(); val release = CompletableDeferred<Unit>()
        h.withdrawal = { release.await(); throw java.io.IOException() }; h.withdraw()
        login("b"); release.complete(Unit); dispatch()
        assertEquals("b", TokenManager.accessToken)
        assertFalse(h.vm.uiState is LoginUiState.Error); assertEquals(0, h.finished)
    }
    @Test fun newLoginDuringOldWithdrawalKeepsNewTokensAndUi() {
        val h = Account(); val release = CompletableDeferred<Unit>()
        h.withdrawal = { withContext(NonCancellable) { release.await() }; success() }; h.withdraw()
        h.vm.login("new", "password"); dispatch()
        release.complete(Unit); dispatch()
        assertEquals("normal", TokenManager.accessToken); assertEquals(LoginUiState.Success, h.vm.uiState)
        assertEquals(0, h.finished)
    }
    @Test fun previousFinallyCannotEraseNewDedicatedCredential() {
        val h = Account(); h.suspendLogin("withdraw-a"); val old = CompletableDeferred<Unit>()
        h.withdrawal = { withContext(NonCancellable) { old.await() }; success() }; h.withdraw()
        h.suspendLogin("withdraw-b"); old.complete(Unit); dispatch()
        assertTrue(h.vm.hasSuspendedWithdrawal); assertEquals(LoginUiState.Suspended, h.vm.uiState)
        h.withdrawal = { success() }; h.withdraw()
        assertEquals(listOf("Bearer withdraw-a", "Bearer withdraw-b"), h.withdrawals.map { it.first })
        assertEquals(1, h.finished)
    }
    @Test fun previousCatchAndFinallyCannotReleaseNewWithdrawalProgress() {
        val h = Account(); h.suspendLogin("withdraw-a"); val old = CompletableDeferred<Unit>()
        h.withdrawal = { withContext(NonCancellable) { old.await(); throw java.io.IOException() } }; h.withdraw()
        h.suspendLogin("withdraw-b"); val next = CompletableDeferred<Unit>()
        h.withdrawal = { next.await(); success() }; h.withdraw()
        old.complete(Unit); dispatch()
        assertTrue(h.vm.isWithdrawing); assertEquals(LoginUiState.Loading, h.vm.uiState)
        assertTrue(h.vm.hasSuspendedWithdrawal)
        next.complete(Unit); dispatch(); assertEquals(1, h.finished)
    }
    @Test fun logoutDuringWithdrawalInvalidatesOldWorkAndKeepsBoundedPushCleanup() {
        val h = Account(); val old = CompletableDeferred<Unit>()
        h.withdrawal = { withContext(NonCancellable) { old.await() }; success() }; h.withdraw()
        var logoutFinished = 0
        h.vm.logout { logoutFinished++ }; dispatch(); old.complete(Unit); dispatch()
        assertEquals(1, logoutFinished); assertEquals(1, h.pushes)
        assertEquals(listOf("Bearer a"), h.logouts); assertNull(TokenManager.accessToken)
        assertEquals(0, h.finished)
    }
    @Test fun logoutStillLimitsPushCleanupToFourSeconds() {
        val h = Account(); var finished = 0
        h.push = { header, _ -> assertEquals("Bearer a", header); assertNull(TokenManager.accessToken); awaitCancellation() }
        h.vm.logout { finished++ }; dispatch()
        assertEquals(1, finished); assertTrue(h.logouts.isEmpty())
        main.scheduler.advanceTimeBy(4_000); dispatch()
        assertEquals(listOf("Bearer a"), h.logouts)
    }
    @Test fun accountSwitchBeforeQueuedWithdrawalSendsNoRequest() {
        val h = Account(); h.vm.withdraw { fail() }; login("b"); dispatch()
        assertTrue(h.withdrawals.isEmpty()); assertEquals("b", TokenManager.accessToken)
    }
    @Test fun resetDropsDedicatedCredentialAndRequiresNewLogin() {
        val h = Account(); h.suspendLogin(); h.vm.resetUiState(); h.withdraw()
        assertFalse(h.vm.hasSuspendedWithdrawal); assertTrue(h.withdrawals.isEmpty())
        assertTrue(h.vm.uiState is LoginUiState.Error)
    }
    @Test fun staleQueuedLogoutCannotClearNewSession() {
        val h = Account(); h.vm.logout { fail() }; login("b"); dispatch()
        assertEquals("b", TokenManager.accessToken); assertTrue(h.logouts.isEmpty()); assertEquals(0, h.pushes)
    }
}
