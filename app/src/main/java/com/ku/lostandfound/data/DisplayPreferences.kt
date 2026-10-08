package com.ku.lostandfound.data

import android.content.Context

/** Device-local display settings, kept independently of login credentials. */
class DisplayPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "display_preferences", Context.MODE_PRIVATE,
    )

    var showSimilarityScores: Boolean
        get() = preferences.getBoolean("show_similarity_scores", false)
        set(value) {
            preferences.edit().putBoolean("show_similarity_scores", value).apply()
        }
}
