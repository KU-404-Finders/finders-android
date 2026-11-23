package com.ku.lostandfound

import com.ku.lostandfound.network.*
import com.ku.lostandfound.notification.PushRegistration
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class PushLogoutRegressionTest : StabilityFixture() {
    private val scope = CoroutineScope(main + SupervisorJob())
    @After fun close() { scope.cancel() }

    private inner class Devices(initialSaved: String? = null) {
        var saved = initialSaved
        var device = "B"
        var pending = false
        var nonCancellable = false
        val registration = CompletableDeferred<Unit>()
        val removed = mutableListOf<String>()
        val registered = mutableListOf<String>()
        var remove: suspend (String) -> Response<Unit> = { Response.success(Unit) }
        val api = object : PushDeviceApiService {
            override suspend fun registerDevice(request: PushDeviceRequest, session: RequestSession): Response<Unit> {
                registered += request.installationId
                if (pending) {
                    if (nonCancellable) withContext(NonCancellable) { registration.await() }
                    else registration.await()
                }
                return Response.success(Unit)
            }
            override suspend fun unregisterDevice(authorization: String, installationId: String): Response<Unit> {
                assertEquals("Bearer a", authorization)
                removed += installationId
                return remove(installationId)
            }
        }
        val manager = PushRegistration(scope, api, { saved }, { saved = it }, main) { device }
        fun sync() { manager.syncIfLoggedIn(TokenManager.sessionGeneration()); dispatch() }
        fun logout() { scope.launch { manager.unregisterBeforeLogout("Bearer a", TokenManager.sessionGeneration()) }; dispatch() }
    }

    @Test fun completedOnlyUsesPersistedId() {
        val h = Devices("A")
        h.logout()
        assertEquals(listOf("A"), h.removed)
        assertNull(h.saved)
    }

    @Test fun pendingOnlyIsCancelledAndRemoved() {
        val h = Devices()
        h.pending = true
        h.sync()
        h.logout()
        assertEquals(listOf("B"), h.removed)
        h.registration.complete(Unit)
        dispatch()
        assertNull(h.saved)
    }

    @Test fun differentCompletedAndPendingIdsAreBothRemoved() {
        val h = Devices()
        h.device = "A"
        h.sync()
        h.device = "B"
        h.pending = true
        h.sync()
        h.logout()
        assertEquals(listOf("A", "B"), h.removed)
        assertNull(h.saved)
    }

    @Test fun sameCompletedAndPendingIdIsRemovedOnce() {
        val h = Devices("B")
        h.pending = true
        h.sync()
        h.logout()
        assertEquals(listOf("B"), h.removed)
    }

    @Test fun failedFirstIdDoesNotSkipSecondOrClearFailedPersistence() {
        val h = Devices("A")
        h.pending = true
        h.remove = { if (it == "A") Response.error(400, "".toResponseBody()) else Response.success(Unit) }
        h.sync()
        h.logout()
        assertEquals(listOf("A", "B"), h.removed)
        assertEquals("A", h.saved)
    }

    @Test fun retryExhaustionStillAttemptsOtherId() {
        val h = Devices("A")
        h.pending = true
        h.remove = { if (it == "A") throw java.io.IOException() else Response.success(Unit) }
        h.sync()
        h.logout()
        main.scheduler.advanceUntilIdle()
        assertEquals(listOf("A", "B", "A", "A"), h.removed)
        assertEquals("A", h.saved)
    }

    @Test fun logoutWaitsForRacingRegistrationBeforeRemovingBothIds() {
        val h = Devices("A")
        h.pending = true
        h.nonCancellable = true
        h.sync()
        h.logout()
        assertTrue(h.removed.isEmpty())
        h.registration.complete(Unit)
        dispatch()
        assertEquals(listOf("A", "B"), h.removed)
        assertNull(h.saved)
        h.sync()
        assertEquals(listOf("B"), h.registered)
    }

    @Test fun newSessionRegistrationWaitsForOldCleanupAndCannotBeRemovedByIt() {
        val h = Devices("A")
        val release = CompletableDeferred<Unit>()
        h.remove = { release.await(); Response.success(Unit) }
        h.logout()
        login("b")
        h.device = "A"
        h.sync()
        assertTrue(h.registered.isEmpty())
        release.complete(Unit)
        dispatch()
        assertEquals(listOf("A"), h.registered)
        assertEquals("A", h.saved)
        h.sync()
        assertEquals(listOf("A"), h.removed)
    }

    @Test fun slowFirstIdDoesNotConsumeSecondIdsDeadline() {
        val h = Devices("A")
        h.pending = true
        h.remove = { if (it == "A") delay(10_000); Response.success(Unit) }
        h.sync()
        val cleanup = scope.launch {
            withTimeoutOrNull(4_000) { h.manager.unregisterBeforeLogout("Bearer a", TokenManager.sessionGeneration()) }
        }
        dispatch()
        assertEquals(listOf("A", "B"), h.removed)
        main.scheduler.advanceTimeBy(4_000)
        dispatch()
        assertTrue(cleanup.isCompleted)
        assertEquals("A", h.saved)
        assertEquals(listOf("A", "B"), h.removed)
    }

    @Test fun retryableFirstFailureStillStartsSecondImmediately() {
        val h = Devices("A")
        h.pending = true
        h.remove = { if (it == "A") Response.error(503, "".toResponseBody()) else Response.success(Unit) }
        h.sync()
        h.logout()
        assertEquals(listOf("A", "B"), h.removed)
        main.scheduler.advanceUntilIdle()
        assertEquals(listOf("A", "B", "A", "A"), h.removed)
    }

    @Test fun transientErrorRetriesAndClearsPersistenceOnSuccess() {
        val h = Devices("A")
        var calls = 0
        h.remove = { if (++calls < 3) throw java.io.IOException() else Response.success(Unit) }
        h.logout()
        main.scheduler.advanceUntilIdle()
        assertEquals(3, calls)
        assertNull(h.saved)
    }

    @Test fun cancellationIsPropagatedWithoutRetry() {
        val h = Devices("A")
        h.remove = { throw CancellationException("cancel") }
        val cleanup = scope.launch { h.manager.unregisterBeforeLogout("Bearer a", TokenManager.sessionGeneration()) }
        dispatch()
        assertTrue(cleanup.isCancelled)
        main.scheduler.advanceUntilIdle()
        assertEquals(listOf("A"), h.removed)
        assertEquals("A", h.saved)
    }

    @Test fun newSessionStopsOldRetriesAndRegistersAfterDeadline() {
        val h = Devices("A")
        h.pending = true
        h.remove = { if (it == "A") delay(10_000); Response.success(Unit) }
        h.sync()
        scope.launch {
            withTimeoutOrNull(4_000) { h.manager.unregisterBeforeLogout("Bearer a", TokenManager.sessionGeneration()) }
        }
        dispatch()
        login("b")
        h.pending = false
        h.sync()
        assertEquals(listOf("B"), h.registered)
        main.scheduler.advanceTimeBy(4_000)
        dispatch()
        assertEquals(listOf("B", "B"), h.registered)
        assertEquals(listOf("A", "B"), h.removed)
        assertEquals("B", h.saved)
    }

    @Test fun timeoutWaitingForRegistrationDoesNotSendLateCleanupIntoNewSession() {
        val h = Devices("A")
        h.pending = true
        h.nonCancellable = true
        h.sync()
        scope.launch {
            withTimeoutOrNull(4_000) { h.manager.unregisterBeforeLogout("Bearer a", TokenManager.sessionGeneration()) }
        }
        dispatch()
        main.scheduler.advanceTimeBy(4_000)
        dispatch()
        assertTrue(h.removed.isEmpty())
        login("b")
        h.pending = false
        h.sync()
        h.registration.complete(Unit)
        dispatch()
        assertEquals(listOf("B", "B"), h.registered)
        assertTrue(h.removed.isEmpty())
        assertEquals("B", h.saved)
    }
}
