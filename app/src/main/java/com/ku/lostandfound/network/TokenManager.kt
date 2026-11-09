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
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired = _sessionExpired.asSharedFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var accessToken: String?
        get() = prefs?.getString(KEY_ACCESS_TOKEN, null)
        set(value) {
            prefs?.edit()?.putString(KEY_ACCESS_TOKEN, value)?.apply()
        }

    var refreshToken: String?
        get() = prefs?.getString(KEY_REFRESH_TOKEN, null)
        set(value) {
            prefs?.edit()?.putString(KEY_REFRESH_TOKEN, value)?.apply()
        }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
        expirationReported = false
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs?.edit()
            ?.putString(KEY_ACCESS_TOKEN, accessToken)
            ?.putString(KEY_REFRESH_TOKEN, refreshToken)
            ?.apply()
        expirationReported = false
    }

    /**
     * Clears only confirmed-invalid credentials and notifies the UI once. Network failures must
     * not call this method because the user should be able to retry with the saved session.
     */
    @Synchronized
    fun expireSession() {
        prefs?.edit()?.clear()?.apply()
        if (!expirationReported) {
            expirationReported = true
            _sessionExpired.tryEmit(Unit)
        }
    }

    fun hasTokens(): Boolean = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

    fun isAccessTokenExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        JwtTokenExpiry.isExpired(accessToken, nowMillis)

    fun isLoggedIn(): Boolean = !accessToken.isNullOrBlank()
}
