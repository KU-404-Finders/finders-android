package com.ku.lostandfound.network

import com.google.gson.JsonParser

internal object RefreshSessionPolicy {
    fun isConfirmedInvalid(statusCode: Int, errorBody: String? = null): Boolean {
        if (statusCode == 401 || statusCode == 403) return true
        if (statusCode != 400) return false
        return runCatching {
            val body = JsonParser.parseString(errorBody).asJsonObject
            body.get("success")?.asBoolean == false && body.get("message")?.asString in setOf(
                "유효하지 않은 리프레시 토큰입니다.",
                "리프레시 토큰이 만료되었거나 유효하지 않습니다.",
            )
        }.getOrDefault(false)
    }
}
