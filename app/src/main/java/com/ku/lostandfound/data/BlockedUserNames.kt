package com.ku.lostandfound.data

import android.content.Context

/** Device-local fallback for block responses without a usable name. */
class BlockedUserNames(context: Context) {
    private val preferences = context.getSharedPreferences("blocked_user_names", Context.MODE_PRIVATE)

    fun save(accountId: Long, userId: Long, name: String?) {
        name?.trim()?.takeIf { it.isNotEmpty() }?.let {
            preferences.edit().putString("${accountId}_$userId", it).apply()
        }
    }

    fun displayName(accountId: Long, userId: Long, serverName: String? = null): String =
        serverName?.trim()?.takeIf { it.isNotEmpty() }
            ?: preferences.getString("${accountId}_$userId", null)?.takeIf { it.isNotBlank() }
            ?: "사용자 #$userId"

    fun remove(accountId: Long, userId: Long) {
        preferences.edit().remove("${accountId}_$userId").apply()
    }
}
