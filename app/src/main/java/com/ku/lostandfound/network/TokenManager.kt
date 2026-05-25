package com.ku.lostandfound.network

import android.content.Context
import android.content.SharedPreferences

object TokenManager {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    private var prefs: SharedPreferences? = null

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
    }

    fun isLoggedIn(): Boolean = accessToken != null
}
