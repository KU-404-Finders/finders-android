package com.ku.lostandfound

import android.content.SharedPreferences
import com.ku.lostandfound.network.*
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

class CredentialAtomicityRegressionTest : StabilityFixture() {
    private fun snapshotDuringWrite(action: String) {
        val before = TokenManager.credentials()
        val values = ConcurrentHashMap(mapOf("access_token" to before.access!!, "refresh_token" to before.refresh!!))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writerStarted = CountDownLatch(1)
        val blockRead = AtomicBoolean(true)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(application.getSharedPreferences("auth_prefs", 0)).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.clear()).thenAnswer { values.clear(); editor }
        `when`(editor.putString(anyString(), anyString())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument(1); editor
        }
        `when`(prefs.getString(anyString(), isNull())).thenAnswer {
            if (it.getArgument<String>(0) == "access_token" && blockRead.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            values[it.getArgument<String>(0)]
        }
        TokenManager.init(application)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val snapshot = pool.submit<TokenManager.Credentials> { TokenManager.credentials() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val write = pool.submit {
                writerStarted.countDown()
                when (action) {
                    "login" -> check(TokenManager.saveNewSessionTokens(before.generation, "b", "refresh-b"))
                    "refresh" -> check(TokenManager.saveRefreshedTokens(before, "b", "refresh-b"))
                    "logout" -> TokenManager.clear()
                }
            }
            assertTrue(writerStarted.await(5, TimeUnit.SECONDS))
            try {
                write.get(150, TimeUnit.MILLISECONDS)
                fail("A session write interleaved with the credential snapshot")
            } catch (_: TimeoutException) { /* The writer waits for the snapshot monitor. */ }
            release.countDown()
            val captured = snapshot.get(5, TimeUnit.SECONDS)
            write.get(5, TimeUnit.SECONDS)
            assertEquals(before.generation, captured.generation)
            assertEquals(before.access, captured.access)
            assertEquals(before.refresh, captured.refresh)
            val after = TokenManager.credentials()
            assertEquals(before.generation + if (action == "refresh") 0 else 1, after.generation)
            assertEquals(if (action == "logout") null else "b", after.access)
            assertEquals(if (action == "logout") null else "refresh-b", after.refresh)
        } finally {
            release.countDown(); pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
    @Test fun accountSwitchCannotSplitCredentialSnapshot() = snapshotDuringWrite("login")
    @Test fun refreshCannotSplitAccessAndRefreshTokenSnapshot() = snapshotDuringWrite("refresh")
    @Test fun logoutCannotSplitGenerationAndTokenSnapshot() = snapshotDuringWrite("logout")

    private val interceptor: Interceptor get() =
        RetrofitClient::class.java.getDeclaredField("authInterceptor").apply { isAccessible = true }
            .get(RetrofitClient) as Interceptor
    private fun request(generation: Long) = Request.Builder().url("https://example.test/api/v1/users/me/blocks")
        .tag(RequestSession::class.java, RequestSession(generation)).build()
    private fun response(request: Request) = okhttp3.Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(200).message("OK").body("".toResponseBody()).build()
    private fun anyRequest(): Request {
        any(Request::class.java)
        // Mockito's matcher returns null; OkHttp's Kotlin signature requires a non-null argument.
        return Request.Builder().url("https://example.test/").build()
    }

    @Test fun oldQueuedRequestIsRejectedBeforeNewAccountHeaderCanBeSent() {
        val old = request(TokenManager.sessionGeneration())
        login("b")
        val chain = mock(Interceptor.Chain::class.java)
        `when`(chain.request()).thenReturn(old)
        try { interceptor.intercept(chain); fail("Old request must be rejected") }
        catch (_: java.io.IOException) { }
        verify(chain, never()).proceed(anyRequest())
    }
    @Test fun currentRequestUsesCurrentAccountHeader() {
        login("b")
        val original = request(TokenManager.sessionGeneration())
        val chain = mock(Interceptor.Chain::class.java)
        `when`(chain.request()).thenReturn(original)
        `when`(chain.proceed(anyRequest())).thenAnswer {
            val sent = it.getArgument<Request>(0)
            assertEquals("Bearer b", sent.header("Authorization")); response(sent)
        }
        interceptor.intercept(chain).close()
        verify(chain).proceed(anyRequest())
    }
    @Test fun accountSwitchDuringDispatchCannotReplaceCapturedHeaderOrHoldTokenLock() {
        val original = request(TokenManager.sessionGeneration())
        val chain = mock(Interceptor.Chain::class.java)
        val pool = Executors.newSingleThreadExecutor()
        `when`(chain.request()).thenReturn(original)
        `when`(chain.proceed(anyRequest())).thenAnswer {
            val sent = it.getArgument<Request>(0)
            pool.submit { login("b") }.get(5, TimeUnit.SECONDS)
            assertEquals("Bearer a", sent.header("Authorization"))
            assertEquals("b", TokenManager.credentials().access)
            response(sent)
        }
        try { interceptor.intercept(chain).close() }
        finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)) }
    }
    @Test fun explicitOldLogoutAuthorizationRemainsOldAuthorization() {
        val original = request(TokenManager.sessionGeneration()).newBuilder()
            .header("Authorization", "Bearer a").build()
        login("b")
        val chain = mock(Interceptor.Chain::class.java)
        `when`(chain.request()).thenReturn(original)
        `when`(chain.proceed(anyRequest())).thenAnswer {
            val sent = it.getArgument<Request>(0)
            assertEquals("Bearer a", sent.header("Authorization")); response(sent)
        }
        interceptor.intercept(chain).close()
    }

    @Test fun moderationApiCarriesOriginalOwnerTagForListAndUnblock() {
        var captured: Request? = null
        val api = retrofit2.Retrofit.Builder().baseUrl("https://example.test/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .callFactory { captured = it; throw java.io.IOException("Intercepted before network") }
            .build().create(ModerationApiService::class.java)
        val owner = RequestSession(TokenManager.sessionGeneration())
        for (unblock in listOf(false, true)) {
            try {
                kotlinx.coroutines.runBlocking {
                    if (unblock) api.unblock(42, owner) else api.blockedUsers(2, owner)
                }
                fail("The test call factory must prevent network access")
            } catch (_: java.io.IOException) { }
            assertEquals(owner, captured?.tag(RequestSession::class.java))
            assertEquals(if (unblock) "/api/v1/users/42/block" else "/api/v1/users/me/blocks", captured?.url?.encodedPath)
        }
    }
}
