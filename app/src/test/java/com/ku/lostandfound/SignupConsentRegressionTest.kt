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

class SignupConsentRegressionTest : StabilityFixture() {
    private val models = mutableListOf<SignupViewmodel>()
    @After fun close() { models.forEach { it.viewModelScope.cancel() } }
    private inner class Signup {
        var sends = 0
        var joins = 0
        var callbacks = 0
        var result: CompletableDeferred<Unit>? = null
        val api = object : SignupApiService {
            override suspend fun sendVerificationCode(email: String): Response<SignupResponse> {
                sends++; result?.await(); return Response.success(SignupResponse(true, ""))
            }
            override suspend fun register(request: SignupRequest): Response<SignupResponse> {
                joins++; result?.await(); return Response.success(SignupResponse(true, ""))
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
        assertEquals(0, h.callbacks); assertNotEquals(SignupUiState.Success, h.vm.uiState)
    }
}
