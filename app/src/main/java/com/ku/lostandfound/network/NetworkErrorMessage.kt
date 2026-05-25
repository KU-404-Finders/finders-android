package com.ku.lostandfound.network

fun httpErrorMessage(code: Int, message: String?): String {
    return if (code >= 500) {
        "서버 오류가 발생했습니다. (HTTP $code)"
    } else {
        "${message ?: "요청을 처리할 수 없습니다."} (HTTP $code)"
    }
}
