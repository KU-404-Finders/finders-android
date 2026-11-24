package com.ku.lostandfound.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Serializes local login issuance with bounded server logout cleanup. */
internal class AuthRequestOrder(private val cleanupTimeoutMillis: Long = 8_000L) {
    private val mutex = Mutex()

    suspend fun <T> login(request: suspend () -> T): T = mutex.withLock { request() }

    suspend fun logout(invalidate: () -> Unit, cleanup: suspend () -> Unit) {
        invalidate()
        mutex.withLock {
            withTimeoutOrNull(cleanupTimeoutMillis) { cleanup() }
        }
    }

    companion object {
        val shared = AuthRequestOrder()
    }
}
