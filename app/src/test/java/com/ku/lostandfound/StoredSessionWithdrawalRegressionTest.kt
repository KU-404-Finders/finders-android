package com.ku.lostandfound

import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.login.viewmodel.*
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import retrofit2.Response
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

class StoredSessionWithdrawalRegressionTest : StabilityFixture() {
    private val models = mutableListOf<LoginViewModel>()
    @After fun close() { models.forEach { it.viewModelScope.cancel() } }
    private fun jwt(subject: String, type: String = "ACCESS") = "e30." + java.util.Base64.getUrlEncoder()
        .withoutPadding().encodeToString(Gson().toJson(mapOf("sub" to subject, "token_type" to type, "exp" to 1)).toByteArray()) + ".signature"
    private fun install(subject: String): TokenManager.Credentials {
        assertTrue(TokenManager.saveNewSessionTokens(TokenManager.sessionGeneration(), jwt(subject), jwt(subject, "REFRESH")))
        return TokenManager.credentials()
    }
    private fun unchanged(before: TokenManager.Credentials) {
        val after = TokenManager.credentials()
        assertEquals(before.generation, after.generation)
        assertEquals(before.access, after.access); assertEquals(before.refresh, after.refresh)
    }
    private inner class Login {
        var reply: Response<LoginResponse> = Response.success(LoginResponse(false, "", null))
        var withdraw: suspend () -> Response<ApiMessageResponse> = { Response.success(ApiMessageResponse(true, "")) }
        val sent = mutableListOf<Pair<String, WithdrawalRequest>>()
        var finished = 0
        val api = object : AuthApiService by mock(AuthApiService::class.java) {
            override suspend fun login(request: LoginRequest) = reply
            override suspend fun withdraw(authorization: String, withdrawal: WithdrawalRequest): Response<ApiMessageResponse> {
                sent += authorization to withdrawal; return this@Login.withdraw()
            }
        }
        val vm = LoginViewModel(api) { _, _ -> fail("No pre-withdrawal push cleanup") }.also(models::add)
        fun suspended(emailPrefix: String, token: String?) {
            // userId is deliberately unrelated to stored identity, which is JWT sub=email.
            reply = Response.error(403, Gson().toJson(LoginResponse(false, "suspended",
                TokenData(null, null, true, token, userId = 1))).toResponseBody())
            vm.login(emailPrefix, "password"); dispatch()
        }
        fun delete() { vm.withdraw { finished++ }; dispatch() }
    }

    @Test fun differentAccountWithoutDedicatedTokenPreservesSavedSessionAndCannotWithdraw() {
        val before = install("a@konkuk.ac.kr"); val h = Login(); h.suspended("b", null)
        unchanged(before); assertTrue(h.vm.uiState is LoginUiState.Error)
        h.delete(); assertTrue(h.sent.isEmpty()); unchanged(before)
    }
    @Test fun differentAccountWithDedicatedTokenPreservesSavedSessionAtLogin() {
        val before = install("a@konkuk.ac.kr"); val h = Login(); h.suspended("b", jwt("b@konkuk.ac.kr", "WITHDRAWAL"))
        unchanged(before); assertEquals(LoginUiState.Suspended, h.vm.uiState)
        assertTrue(h.vm.hasSuspendedWithdrawal)
    }
    @Test fun sameConfirmedAccountWithDedicatedTokenEndsOnlyItsStoredSession() {
        val before = install("a@konkuk.ac.kr"); val h = Login(); val token = jwt("a@konkuk.ac.kr", "WITHDRAWAL")
        h.suspended("a", token)
        val generation = TokenManager.sessionGeneration()
        assertEquals(before.generation + 1, generation)
        assertNull(TokenManager.accessToken); assertNull(TokenManager.refreshToken)
        h.delete()
        assertEquals(listOf("Bearer $token" to WithdrawalRequest(generation, WithdrawalTokenKind.DEDICATED)), h.sent)
    }
    @Test fun sameAccountWithoutDedicatedTokenPreservesSavedSession() {
        val before = install("a@konkuk.ac.kr"); val h = Login(); h.suspended("a", null)
        unchanged(before); h.delete(); assertTrue(h.sent.isEmpty()); unchanged(before)
    }
    @Test fun unknownStoredOwnerIsNotClearedAtSuspensionOrWithdrawalSuccess() {
        val before = TokenManager.credentials(); val h = Login()
        h.suspended("b", jwt("b@konkuk.ac.kr", "WITHDRAWAL")); h.delete()
        unchanged(before); assertEquals(1, h.finished)
    }
    @Test fun unknownDedicatedOwnerCannotAuthorizeClearingKnownStoredOwner() {
        val before = install("a@konkuk.ac.kr"); val h = Login()
        h.suspended("a", "opaque-dedicated-token"); h.delete()
        unchanged(before); assertEquals(1, h.finished)
    }
    @Test fun dedicatedSubjectMismatchingActualLoginRequestIsRejected() {
        val before = install("a@konkuk.ac.kr"); val h = Login()
        h.suspended("b", jwt("a@konkuk.ac.kr", "WITHDRAWAL")); h.delete()
        unchanged(before); assertTrue(h.sent.isEmpty()); assertTrue(h.vm.uiState is LoginUiState.Error)
    }
    @Test fun deletingOtherAccountPreservesStoredSessionAndCannotFallBackOnSecondTap() {
        val before = install("a@konkuk.ac.kr"); val h = Login(); val token = jwt("b@konkuk.ac.kr", "WITHDRAWAL")
        h.suspended("b", token); h.delete()
        unchanged(before); assertEquals(LoginUiState.WithdrawalComplete, h.vm.uiState)
        assertEquals(listOf("Bearer $token" to WithdrawalRequest(before.generation, WithdrawalTokenKind.DEDICATED)), h.sent)
        h.delete(); assertEquals(1, h.sent.size); unchanged(before)
    }
    @Test fun oldDedicatedResponseCannotClearNewlyInstalledSession() {
        install("a@konkuk.ac.kr"); val h = Login(); val gate = CompletableDeferred<Unit>()
        h.suspended("b", jwt("b@konkuk.ac.kr", "WITHDRAWAL"))
        h.withdraw = { gate.await(); Response.success(ApiMessageResponse(true, "")) }; h.delete()
        val next = install("c@konkuk.ac.kr"); gate.complete(Unit); dispatch()
        unchanged(next); assertEquals(0, h.finished)
    }
    @Test fun clearingDedicatedMemoryCannotFallBackToPreservedOrdinaryToken() {
        val before = install("a@konkuk.ac.kr"); val h = Login()
        h.suspended("b", jwt("b@konkuk.ac.kr", "WITHDRAWAL")); h.vm.resetUiState(); h.delete()
        unchanged(before); assertTrue(h.sent.isEmpty()); assertFalse(h.vm.hasSuspendedWithdrawal)
    }
    @Test fun expiredDedicatedTokenNeverRetriesUsingOrdinaryToken() {
        val before = install("a@konkuk.ac.kr"); val h = Login(); val token = jwt("b@konkuk.ac.kr", "WITHDRAWAL")
        h.suspended("b", token); h.withdraw = { Response.error(401, "".toResponseBody()) }
        h.delete(); h.delete()
        unchanged(before); assertEquals(listOf("Bearer $token", "Bearer $token"), h.sent.map { it.first })
        assertTrue(h.sent.all { it.second == WithdrawalRequest(before.generation, WithdrawalTokenKind.DEDICATED) })
    }
    @Test fun redirectResponseIsFailureAndPreservesOtherAccountSession() {
        val before = install("a@konkuk.ac.kr"); val h = Login()
        h.suspended("b", jwt("b@konkuk.ac.kr", "WITHDRAWAL"))
        h.withdraw = { Response.error(302, "".toResponseBody()) }; h.delete()
        unchanged(before); assertEquals(0, h.finished)
        assertTrue(h.vm.uiState is LoginUiState.Error); assertTrue(h.vm.hasSuspendedWithdrawal)
    }
    @Test fun newerSessionOfSameOwnerCannotBeClearedByOlderGeneration() {
        val before = install("a@konkuk.ac.kr"); val next = install("a@konkuk.ac.kr")
        assertNull(TokenManager.clearIfOwnedBy(before.generation, "a@konkuk.ac.kr")); unchanged(next)
    }
    @Test fun freshOrdinaryLoginRestoresOrdinaryWithdrawalAfterDedicatedFlowWasCancelled() {
        install("a@konkuk.ac.kr"); val h = Login()
        h.suspended("b", jwt("b@konkuk.ac.kr", "WITHDRAWAL")); h.vm.resetUiState()
        val access = jwt("a@konkuk.ac.kr")
        h.reply = Response.success(LoginResponse(true, "", TokenData(access, jwt("a@konkuk.ac.kr", "REFRESH"))))
        h.vm.login("a", "password"); dispatch(); h.delete()
        assertEquals("Bearer $access", h.sent.single().first)
        assertEquals(WithdrawalTokenKind.SESSION, h.sent.single().second.tokenKind)
        assertNull(TokenManager.accessToken)
    }
    @Test fun ownerComparisonAndConditionalClearHoldSameMonitorAsSessionWrites() {
        val before = install("a@konkuk.ac.kr")
        val values = ConcurrentHashMap(mapOf("access_token" to before.access!!, "refresh_token" to before.refresh!!))
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val writerStarted = CountDownLatch(1)
        val block = AtomicBoolean(true)
        val prefs = mock(SharedPreferences::class.java); val editor = mock(SharedPreferences.Editor::class.java)
        `when`(application.getSharedPreferences("auth_prefs", 0)).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.clear()).thenAnswer { values.clear(); editor }
        `when`(editor.putString(anyString(), anyString())).thenAnswer { values[it.getArgument(0)] = it.getArgument(1); editor }
        `when`(prefs.getString(anyString(), isNull())).thenAnswer {
            if (it.getArgument<String>(0) == "access_token" && block.compareAndSet(true, false)) {
                entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
            }
            values[it.getArgument<String>(0)]
        }
        TokenManager.init(application)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val clear = pool.submit<Long?> { TokenManager.clearIfOwnedBy(before.generation, "a@konkuk.ac.kr") }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val writer = pool.submit { writerStarted.countDown(); install("b@konkuk.ac.kr") }
            assertTrue(writerStarted.await(5, TimeUnit.SECONDS))
            try { writer.get(150, TimeUnit.MILLISECONDS); fail("Writer interleaved with owner check") }
            catch (_: TimeoutException) { }
            release.countDown(); assertEquals(before.generation + 1, requireNotNull(clear.get(5, TimeUnit.SECONDS)))
            writer.get(5, TimeUnit.SECONDS)
            assertEquals("b@konkuk.ac.kr", TokenManager.tokenSubject(TokenManager.accessToken))
            assertEquals(before.generation + 2, TokenManager.sessionGeneration())
        } finally { release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)) }
    }
}
