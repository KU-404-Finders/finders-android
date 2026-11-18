package com.ku.lostandfound

import com.ku.lostandfound.network.AuthRequestOrder
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AuthRequestOrderTest {
    @Test fun loginWaitsForLogoutBeforeIssuingNewToken() = runTest {
        val order = AuthRequestOrder()
        val release = CompletableDeferred<Unit>()
        var token: String? = "old"
        var invalidated = false
        val logout = launch(start = CoroutineStart.UNDISPATCHED) {
            order.logout({ invalidated = true }) { release.await(); token = null }
        }
        val login = launch(start = CoroutineStart.UNDISPATCHED) { order.login { token = "new" } }
        assertTrue(invalidated)
        assertFalse(login.isCompleted)
        release.complete(Unit)
        logout.join()
        login.join()
        assertEquals("new", token)
    }

    @Test fun timedOutCleanupReleasesLogin() = runTest {
        val order = AuthRequestOrder(100)
        val cleanup = launch { order.logout({}) { awaitCancellation() } }
        yield()
        order.login { assertTrue(cleanup.isCompleted) }
    }

    @Test fun failedCleanupReleasesLogin() = runTest {
        val order = AuthRequestOrder()
        runCatching { order.logout({}) { error("offline") } }
        assertEquals("new", order.login { "new" })
    }
}
