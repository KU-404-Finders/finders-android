package com.ku.lostandfound.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object TokenManager {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    private var prefs: SharedPreferences? = null
    private var expirationReported = false
    /** Changes whenever credentials are discarded or a new login is accepted. */
    private val sessionGeneration = SessionGeneration()
    // replay keeps an expiry observed while the navigation host is being recreated.
    // The emitted generation lets a later login ignore this stale event safely.
    private val _sessionExpired = MutableSharedFlow<Long>(replay = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val sessionExpired = _sessionExpired.asSharedFlow()

    @Synchronized
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val accessToken: String? get() = prefs?.getString(KEY_ACCESS_TOKEN, null)
    val refreshToken: String? get() = prefs?.getString(KEY_REFRESH_TOKEN, null)

    class Credentials internal constructor(val generation: Long, val access: String?, val refresh: String?)

    @Synchronized
    fun credentials() = Credentials(sessionGeneration.capture(), accessToken, refreshToken)

    private fun matches(expected: Credentials) =
        sessionGeneration.isCurrent(expected.generation) && accessToken == expected.access && refreshToken == expected.refresh

    @Synchronized
    fun clear(): Long {
        prefs?.edit()?.clear()?.apply()
        expirationReported = false
        return sessionGeneration.advance()
    }

    /** Accepts a completed login only when it belongs to the session that started it. */
    @Synchronized
    fun saveNewSessionTokens(expectedGeneration: Long, accessToken: String, refreshToken: String): Boolean {
        if (!sessionGeneration.isCurrent(expectedGeneration)) return false
        sessionGeneration.advance()
        prefs?.edit()
            ?.putString(KEY_ACCESS_TOKEN, accessToken)
            ?.putString(KEY_REFRESH_TOKEN, refreshToken)
            ?.apply()
        expirationReported = false
        return true
    }

    /** Accepts a refresh result only while its original authenticated session is still active. */
    @Synchronized
    fun saveRefreshedTokens(expected: Credentials, accessToken: String, refreshToken: String): Boolean {
        if (!matches(expected) || expected.access.isNullOrBlank() || accessToken.isBlank() || refreshToken.isBlank()) return false
        prefs?.edit()
            ?.putString(KEY_ACCESS_TOKEN, accessToken)
            ?.putString(KEY_REFRESH_TOKEN, refreshToken)
            ?.apply()
        expirationReported = false
        return true
    }

    @Synchronized
    fun sessionGeneration(): Long = sessionGeneration.capture()

    @Synchronized
    fun isCurrentSession(expectedGeneration: Long, expectedAccessToken: String? = null): Boolean =
        sessionGeneration.isCurrent(expectedGeneration) &&
                (expectedAccessToken == null || accessToken == expectedAccessToken)

    /**
     * Clears only confirmed-invalid credentials and notifies the UI once. Network failures must
     * not call this method because the user should be able to retry with the saved session.
     */
    @Synchronized
    fun expireSession(expected: Credentials): Boolean {
        if (expirationReported || !matches(expected) || (expected.access == null && expected.refresh == null)) return false
        val expiredGeneration = sessionGeneration.advance()
        prefs?.edit()?.clear()?.apply()
        expirationReported = true
        _sessionExpired.tryEmit(expiredGeneration)
        return true
    }

    @Synchronized
    fun consumeExpiry(generation: Long): Boolean {
        if (!expirationReported || !isCurrentSession(generation)) return false
        expirationReported = false
        return true
    }

    @Synchronized
    fun hasTokens(): Boolean = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

    fun isAccessTokenExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        JwtTokenExpiry.isExpired(accessToken, nowMillis)

    /** A partial or backup-restored credential set is never treated as authenticated. */
    fun isLoggedIn(): Boolean = hasTokens()
}
