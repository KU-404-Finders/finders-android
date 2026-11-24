package com.ku.lostandfound

import com.ku.lostandfound.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*

class WithdrawalAuthorizationRegressionTest : StabilityFixture() {
    private val interceptor get() = RetrofitClient::class.java.getDeclaredField("authInterceptor")
        .apply { isAccessible = true }.get(RetrofitClient) as Interceptor
    private fun request(kind: WithdrawalTokenKind, generation: Long = TokenManager.sessionGeneration(), token: String = "a") =
        Request.Builder().url("https://example.test/api/v1/auth/withdraw")
            .header("Authorization", "Bearer $token")
            .tag(WithdrawalRequest::class.java, WithdrawalRequest(generation, kind)).build()
    private fun response(sent: Request, code: Int = 200, body: String = "") = Response.Builder()
        .request(sent).protocol(Protocol.HTTP_1_1).code(code).message("Test").body(body.toResponseBody()).build()
    private fun anyRequest(): Request {
        any(Request::class.java)
        return Request.Builder().url("https://example.test/").build()
    }
    private fun send(original: Request, reply: (Request, Int) -> Response): List<Request> {
        val sent = mutableListOf<Request>()
        val chain = mock(Interceptor.Chain::class.java)
        `when`(chain.request()).thenReturn(original)
        `when`(chain.proceed(anyRequest())).thenAnswer {
            val request = it.getArgument<Request>(0); sent += request; reply(request, sent.size)
        }
        interceptor.intercept(chain).close()
        return sent
    }
    private fun rejected(request: Request) {
        var proceeded = false
        try { send(request) { sent, _ -> proceeded = true; response(sent) }; fail("Request must be rejected") }
        catch (_: java.io.IOException) { }
        assertFalse(proceeded)
    }

    @Test fun dedicatedPurposeNeverRefreshesEvenWhenTokenBytesEqualOrdinaryToken() {
        val owner = TokenManager.credentials()
        val sent = send(request(WithdrawalTokenKind.DEDICATED)) { request, _ ->
            // If incorrectly classified as ordinary, a retry would use this refreshed token.
            // No real refresh endpoint is contacted, including when this assertion regresses.
            assertTrue(TokenManager.saveRefreshedTokens(owner, "fresh-a", "fresh-r"))
            response(request, 403, """{"timestamp":"test"}""")
        }
        assertEquals(listOf("Bearer a"), sent.map { it.header("Authorization") })
        assertEquals(owner.generation, TokenManager.sessionGeneration())
        assertEquals("fresh-a", TokenManager.accessToken)
    }
    @Test fun dedicatedAuthenticationFailureKeepsUnrelatedOrdinaryCredentials() {
        val before = TokenManager.credentials()
        val sent = send(request(WithdrawalTokenKind.DEDICATED, token = "withdraw-only")) { request, _ ->
            response(request, 401, """{"timestamp":"test"}""")
        }
        assertEquals(listOf("Bearer withdraw-only"), sent.map { it.header("Authorization") })
        val after = TokenManager.credentials()
        assertEquals(before.generation, after.generation)
        assertEquals(before.access, after.access); assertEquals(before.refresh, after.refresh)
    }
    @Test fun ordinaryWithdrawalUsesLatestSameSessionTokenAndSharedRefreshResult() {
        val before = TokenManager.credentials()
        val sent = send(request(WithdrawalTokenKind.SESSION, token = "old-a")) { request, attempt ->
            if (attempt == 1) {
                assertTrue(TokenManager.saveRefreshedTokens(before, "fresh-a", "fresh-r"))
                response(request, 403, """{"timestamp":"test"}""")
            } else response(request)
        }
        assertEquals(listOf("Bearer a", "Bearer fresh-a"), sent.map { it.header("Authorization") })
        assertEquals(before.generation, TokenManager.sessionGeneration())
    }
    @Test fun accountSwitchDuringOrdinaryWithdrawalDoesNotRetryUsingNewAccount() {
        val sent = send(request(WithdrawalTokenKind.SESSION)) { request, _ ->
            login("b"); response(request, 403, """{"timestamp":"test"}""")
        }
        assertEquals(listOf("Bearer a"), sent.map { it.header("Authorization") })
        assertEquals("b", TokenManager.accessToken)
    }
    @Test fun oldDedicatedRequestIsRejectedDespiteHeaderMatchingNewAccount() {
        val generation = TokenManager.sessionGeneration(); login("b")
        rejected(request(WithdrawalTokenKind.DEDICATED, generation, "b"))
        assertEquals("b", TokenManager.accessToken)
    }
    @Test fun oldOrdinaryWithdrawalIsRejectedBeforeAnyNewAccountHeaderIsSent() {
        val generation = TokenManager.sessionGeneration(); login("b")
        rejected(request(WithdrawalTokenKind.SESSION, generation))
        assertEquals("b", TokenManager.accessToken)
    }
    @Test fun dedicatedCredentialCannotBeUsedForOrdinaryApi() {
        rejected(request(WithdrawalTokenKind.DEDICATED).newBuilder()
            .url("https://example.test/api/v1/users/me").build())
    }
    @Test fun unownedExplicitWithdrawalIsRejected() {
        rejected(request(WithdrawalTokenKind.SESSION).newBuilder()
            .tag(WithdrawalRequest::class.java, null).build())
    }
    @Test fun normalAuthorizationFailureWithoutAuthShapeDoesNotRefresh() {
        val before = TokenManager.credentials()
        val sent = send(request(WithdrawalTokenKind.SESSION)) { request, _ ->
            assertTrue(TokenManager.saveRefreshedTokens(before, "fresh-a", "fresh-r"))
            response(request, 403, """{"message":"not allowed"}""")
        }
        assertEquals(1, sent.size); assertEquals("fresh-a", TokenManager.accessToken)
    }
    @Test fun retrofitCarriesPurposeAndOriginalGenerationWithoutSerializingTag() {
        var captured: Request? = null
        val api = retrofit2.Retrofit.Builder().baseUrl("https://example.test/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .callFactory { captured = it; throw java.io.IOException("No network in test") }
            .build().create(AuthApiService::class.java)
        val tag = WithdrawalRequest(TokenManager.sessionGeneration(), WithdrawalTokenKind.DEDICATED)
        try { runBlocking { api.withdraw("Bearer dedicated", tag) }; fail() }
        catch (_: java.io.IOException) { }
        assertEquals(tag, captured?.tag(WithdrawalRequest::class.java))
        assertEquals("Bearer dedicated", captured?.header("Authorization"))
        assertEquals("DELETE", captured?.method)
        assertNull(captured?.body); assertNull(captured?.url?.query)
    }
}
