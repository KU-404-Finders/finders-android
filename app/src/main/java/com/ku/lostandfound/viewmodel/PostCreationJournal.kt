package com.ku.lostandfound.viewmodel

import android.content.Context
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.network.TokenManager
import java.util.UUID

internal object PostCreationJournal {
    const val PREFS = "post_creation_attempts"

    data class Entry(val key: String, val value: String) {
        val type: PostType get() = PostType.valueOf(value.substringBefore(':'))
    }

    fun account(expected: Long = TokenManager.sessionGeneration()): String? {
        val credentials = TokenManager.credentials()
        if (credentials.generation != expected || credentials.access.isNullOrBlank() || credentials.refresh.isNullOrBlank()) {
            return null
        }
        return TokenManager.tokenUserId(credentials.access)?.let { "user:$it" }
    }

    @Synchronized
    fun pending(context: Context, key: String): Entry? {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // A legacy key cannot prove ownership after an email is reused.
        val legacyKeys = preferences.all.keys.filter { it.startsWith("session:") || LEGACY_HASH.matches(it) }
        if (legacyKeys.isNotEmpty()) {
            val edit = preferences.edit()
            legacyKeys.forEach(edit::remove)
            edit.apply()
        }
        return preferences.getString(key, null)
            ?.takeIf { value -> PostType.entries.any { value.startsWith("${it.name}:") } }
            ?.let { Entry(key, it) }
    }

    private val LEGACY_HASH = Regex("[0-9a-f]{64}")

    @Synchronized
    fun begin(context: Context, key: String, type: PostType): Entry? {
        if (pending(context, key) != null) return null
        val entry = Entry(key, "${type.name}:${UUID.randomUUID()}")
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key, entry.value).commit()) { "등록 상태를 저장하지 못했습니다." }
        return entry
    }

    @Synchronized
    fun clear(context: Context, entry: Entry): Boolean {
        if (pending(context, entry.key) != entry) return true
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(entry.key).commit()
    }
}
