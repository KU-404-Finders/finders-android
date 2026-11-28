package com.ku.lostandfound.viewmodel

import android.content.Context
import androidx.annotation.WorkerThread
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.network.TokenManager
import java.util.UUID

/**
 * One in-flight creation per account (key = "user:<id>", value = "<TYPE>:<UUID>").
 * Calls are serialized on this object's monitor; writers use commit() so a POST never
 * starts before its record is durable.
 */
internal object PostCreationJournal {
    const val PREFS = "post_creation_attempts"

    data class Entry(val key: String, val value: String) {
        val type: PostType get() = PostType.valueOf(value.substringBefore(':'))
    }

    // commit() drops a removed key from memory even when the disk write fails, so pending()
    // alone can no longer see it; remember those entries to retry their deletion.
    private val unclearedOnDisk = mutableSetOf<Entry>()

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

    @WorkerThread
    @Synchronized
    fun begin(context: Context, key: String, type: PostType): Entry? {
        if (pending(context, key) != null) return null
        val entry = Entry(key, "${type.name}:${UUID.randomUUID()}")
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!preferences.edit().putString(key, entry.value).commit()) {
            // The failed write is still in memory; drop it so an unsent attempt cannot read as unconfirmed.
            preferences.edit().remove(key).commit()
            error("등록 상태를 저장하지 못했습니다.")
        }
        // A full-map commit succeeded, so any older failed deletion for this key is now on disk too.
        unclearedOnDisk.removeAll { it.key == key }
        return entry
    }

    /** Removes [entry] only; another attempt's record under the same account is left intact. */
    @WorkerThread
    @Synchronized
    fun clear(context: Context, entry: Entry): Boolean {
        val current = pending(context, entry.key)
        if (current != entry && (current != null || entry !in unclearedOnDisk)) {
            unclearedOnDisk.remove(entry)
            return true
        }
        val removed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(entry.key).commit()
        if (removed) unclearedOnDisk.remove(entry) else unclearedOnDisk.add(entry)
        return removed
    }
}
