package com.ku.lostandfound.network

import android.util.Log
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import java.io.IOException

internal fun networkFailureMessage(error: Exception): String = when (error) {
    is JsonParseException, is MalformedJsonException ->
        "서버 응답을 처리할 수 없습니다. 요청이 처리되었을 수 있으니 결과를 먼저 확인해주세요."
    is IOException -> "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요."
    else -> "요청을 처리하는 중 오류가 발생했습니다."
}

object NetworkLog {
    private const val TAG = "KUFindersNetwork"

    fun httpError(action: String, code: Int) {
        Log.e(TAG, "$action failed: HTTP $code")
    }

    fun exception(action: String, throwable: Throwable) {
        // Exception messages can include JSON values or credentials. Classify without logging them.
        val kind = when (throwable) {
            is com.google.gson.JsonIOException -> "JsonIOException"
            is JsonParseException -> "JSON conversion"
            is MalformedJsonException -> "malformed JSON"
            is IOException -> "transport I/O"
            else -> throwable::class.java.simpleName
        }
        Log.e(
            TAG,
            "$action failed: $kind",
        )
    }
}
