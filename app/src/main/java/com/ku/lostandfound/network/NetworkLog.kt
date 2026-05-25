package com.ku.lostandfound.network

import android.util.Log

object NetworkLog {
    private const val TAG = "KUFindersNetwork"

    fun httpError(action: String, code: Int, body: String?) {
        Log.e(TAG, "$action failed: HTTP $code, body=${body.orEmpty()}")
    }

    fun exception(action: String, throwable: Throwable) {
        Log.e(
            TAG,
            "$action failed: ${throwable::class.java.simpleName}: ${throwable.message}",
            throwable,
        )
    }
}
