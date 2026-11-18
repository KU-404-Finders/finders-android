package com.ku.lostandfound

import com.ku.lostandfound.network.*
import com.ku.lostandfound.notification.PushRegistration
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class SessionPushRegressionTest : StabilityFixture() {
    @Test fun previousSessionCannotExpireNewLogin() {
        val previous = TokenManager.credentials()
        login("b")
        assertFalse(TokenManager.expireSession(previous))
        assertEquals("b", TokenManager.accessToken)
        assertEquals("refresh-b", TokenManager.refreshToken)
    }

    @Test fun duplicateExpiryPreservesPendingEvent() {
        val rejected = TokenManager.credentials()
        assertTrue(TokenManager.expireSession(rejected))
        val event = TokenManager.sessionExpired.replayCache.single()
        assertFalse(TokenManager.expireSession(rejected))
        assertFalse(TokenManager.expireSession(TokenManager.credentials()))
        assertTrue(TokenManager.consumeExpiry(event))
        assertFalse(TokenManager.consumeExpiry(event))
    }

    @Test fun staleSyncAndLogoutCannotCancelNewRegistration() {
        val calls = mutableListOf<Long>()
        val release = CompletableDeferred<Unit>()
        val api = object : PushDeviceApiService {
            override suspend fun registerDevice(request: PushDeviceRequest, session: RequestSession): Response<Unit> {
                calls += session.generation
                release.await()
                return Response.success(Unit)
            }
            override suspend fun unregisterDevice(authorization: String, installationId: String): Response<Unit> =
                error("Old logout reached the server")
        }
        val manager = PushRegistration(CoroutineScope(main), api) { "device" }
        val previous = TokenManager.sessionGeneration()
        login("b")
        val current = TokenManager.sessionGeneration()
        manager.syncIfLoggedIn(current)
        dispatch()
        manager.syncIfLoggedIn(previous)
        runBlocking { manager.unregisterBeforeLogout("Bearer a", previous) }
        release.complete(Unit)
        dispatch()
        manager.syncIfLoggedIn(current)
        dispatch()
        assertEquals(listOf(current), calls)
    }

    @Test fun changedInstallationInvalidatesSleepingRetry() {
        var device = "first"
        val calls = mutableListOf<String>()
        val api = object : PushDeviceApiService {
            override suspend fun registerDevice(request: PushDeviceRequest, session: RequestSession): Response<Unit> {
                calls += request.installationId
                return if (request.installationId == "first") Response.error(503, "".toResponseBody())
                    else Response.success(Unit)
            }
            override suspend fun unregisterDevice(authorization: String, installationId: String) = Response.success(Unit)
        }
        val manager = PushRegistration(CoroutineScope(main), api) { device }
        manager.syncIfLoggedIn(TokenManager.sessionGeneration())
        dispatch()
        device = "next"
        manager.syncIfLoggedIn(TokenManager.sessionGeneration())
        main.scheduler.advanceUntilIdle()
        assertEquals(listOf("first", "next"), calls)
    }
}
