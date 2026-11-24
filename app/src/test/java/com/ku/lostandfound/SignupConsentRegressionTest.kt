package com.ku.lostandfound

import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.signup.viewmodel.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class SignupConsentRegressionTest : StabilityFixture() {
    private val models = mutableListOf<SignupViewmodel>()
    @After fun close() { models.forEach { it.viewModelScope.cancel() } }
    private inner class Signup {
        var sends = 0
        var joins = 0
        var callbacks = 0
        var result: CompletableDeferred<Unit>? = null
        var success = true
        var status = 200
        private fun response(): Response<SignupResponse> = if (status == 200)
            Response.success(SignupResponse(success, "rejected")) else Response.error(status, "".toResponseBody())
        val api = object : SignupApiService {
            override suspend fun sendVerificationCode(email: String): Response<SignupResponse> {
                sends++; result?.await(); return response()
            }
            override suspend fun register(request: SignupRequest): Response<SignupResponse> {
                joins++; result?.await(); return response()
            }
        }
        val vm = SignupViewmodel(api).also(models::add)
        fun consent() { vm.termsAccepted = true; vm.privacyAccepted = true }
        fun request() { vm.sendVerificationCode { callbacks++ }; vm.register() }
        fun assertNoRequests() { assertEquals(0, sends); assertEquals(0, joins); assertEquals(0, callbacks) }
    }
    @Test fun restoredViewModelCannotSendOrRegisterWithoutConsent() {
        val h = Signup(); h.request(); dispatch()
        h.assertNoRequests(); assertTrue(h.vm.uiState is SignupUiState.Error)
    }
    @Test fun eitherMissingConsentPreventsBothRequests() {
        for (terms in listOf(true, false)) {
            val h = Signup(); h.vm.termsAccepted = terms; h.vm.privacyAccepted = !terms
            h.request(); dispatch(); h.assertNoRequests()
        }
    }
    @Test fun consentAllowsNormalVerificationAndSignup() {
        val h = Signup(); h.consent(); h.request(); dispatch()
        assertEquals(1, h.sends); assertEquals(1, h.joins); assertEquals(1, h.callbacks)
        assertEquals(SignupUiState.Success, h.vm.uiState)
    }
    @Test fun consentRevokedBeforeCoroutineRunsPreventsApiCalls() {
        val h = Signup(); h.consent(); h.request(); h.vm.privacyAccepted = false; dispatch()
        h.assertNoRequests()
    }
    @Test fun resetBeforeCoroutineRunsPreventsOldRequestsEvenAfterNewConsent() {
        val h = Signup(); h.consent(); h.request(); h.vm.reset(); h.consent(); dispatch()
        h.assertNoRequests()
    }
    @Test fun resetDuringRequestsSuppressesOldSuccessAndNavigation() {
        val h = Signup(); h.consent(); val result = CompletableDeferred<Unit>(); h.result = result
        h.request(); dispatch(); h.vm.reset(); result.complete(Unit); dispatch()
        assertEquals(0, h.callbacks); assertEquals(SignupUiState.Idle, h.vm.uiState)
        assertFalse(h.vm.hasRequiredConsent)
    }
    @Test fun consentRevokedDuringRequestsSuppressesSuccessCallbacks() {
        val h = Signup(); h.consent(); val result = CompletableDeferred<Unit>(); h.result = result
        h.request(); dispatch(); h.vm.termsAccepted = false; result.complete(Unit); dispatch()
        assertEquals(0, h.callbacks); assertTrue(h.vm.uiState is SignupUiState.Error)
    }

    private fun revokeThenRetry(outcome: String) {
        for (register in listOf(false, true)) {
            val h = Signup(); h.consent()
            val gate = CompletableDeferred<Unit>(); h.result = gate
            if (outcome == "body") h.success = false
            if (outcome == "http") h.status = 503
            fun request() { if (register) h.vm.register() else h.vm.sendVerificationCode { h.callbacks++ } }
            request(); dispatch()
            assertEquals(SignupUiState.Loading, h.vm.uiState)
            h.vm.privacyAccepted = false
            if (outcome == "exception") gate.completeExceptionally(java.io.IOException()) else gate.complete(Unit)
            dispatch()
            assertTrue(h.vm.uiState is SignupUiState.Error)
            assertEquals(0, h.callbacks)
            h.consent(); h.result = null; h.success = true; h.status = 200
            request(); dispatch()
            assertEquals(if (register) SignupUiState.Success else SignupUiState.Idle, h.vm.uiState)
            assertEquals(2, if (register) h.joins else h.sends)
            assertEquals(if (register) 0 else 1, h.callbacks)
        }
    }
    @Test fun revocationDuringSuccessLeavesLoadingAndAllowsRetry() = revokeThenRetry("success")
    @Test fun revocationDuringServerFailureLeavesLoadingAndAllowsRetry() {
        revokeThenRetry("body"); revokeThenRetry("http")
    }
    @Test fun revocationDuringExceptionLeavesLoadingAndAllowsRetry() = revokeThenRetry("exception")

    @Test fun oldGenerationResponsesCannotClearNewRequestsLoadingOrPublishCallbacks() {
        for (outcome in listOf("success", "body", "http", "exception")) {
            val h = Signup(); h.consent()
            val old = CompletableDeferred<Unit>(); h.result = old; h.request(); dispatch()
            h.vm.reset(); h.consent()
            val next = CompletableDeferred<Unit>(); h.result = next; h.request(); dispatch()
            if (outcome == "body") h.success = false
            if (outcome == "http") h.status = 503
            if (outcome == "exception") old.completeExceptionally(java.io.IOException()) else old.complete(Unit)
            dispatch()
            assertEquals(SignupUiState.Loading, h.vm.uiState)
            assertEquals(0, h.callbacks)
            h.success = true; h.status = 200; next.complete(Unit); dispatch()
            assertEquals(SignupUiState.Success, h.vm.uiState)
            assertEquals(1, h.callbacks)
        }
    }
}
