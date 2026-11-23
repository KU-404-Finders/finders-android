package com.ku.lostandfound.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshSessionPolicyTest {
    @Test fun malformedPrimitiveTypesDoNotConfirmRejection() {
        for (success in listOf("0", "\"false\"", "null", "[]", "{}")) {
            assertFalse(RefreshSessionPolicy.isConfirmedInvalid(400,
                "{\"success\":$success,\"message\":\"유효하지 않은 리프레시 토큰입니다.\"}"))
        }
        for (message in listOf("0", "false", "null", "[]", "{}")) {
            assertFalse(RefreshSessionPolicy.isConfirmedInvalid(400, "{\"success\":false,\"message\":$message}"))
        }
    }
    @Test
    fun `expired or revoked refresh credentials are confirmed invalid`() {
        assertTrue(RefreshSessionPolicy.isConfirmedInvalid(401))
        assertTrue(RefreshSessionPolicy.isConfirmedInvalid(403))
    }

    @Test
    fun `temporary server failures do not invalidate the saved session`() {
        assertFalse(RefreshSessionPolicy.isConfirmedInvalid(429))
        assertFalse(RefreshSessionPolicy.isConfirmedInvalid(500))
        assertFalse(RefreshSessionPolicy.isConfirmedInvalid(200))
    }

    @Test fun knownRefreshRejectionsOnlyExpireOn400() {
        val messages = listOf("유효하지 않은 리프레시 토큰입니다.", "리프레시 토큰이 만료되었거나 유효하지 않습니다.")
        for (message in messages) {
            val body = com.google.gson.Gson().toJson(mapOf("success" to false, "message" to message))
            assertTrue(RefreshSessionPolicy.isConfirmedInvalid(400, body))
            for (code in listOf(429, 500, 503)) assertFalse(RefreshSessionPolicy.isConfirmedInvalid(code, body))
        }
        for (body in listOf(null, "", "bad json", "{}", "{\"success\":false,\"message\":\"입력 값을 확인해 주세요.\"}", "{\"success\":false,\"message\":\"리프레시 토큰만 재발급에 사용할 수 있습니다.\"}")) {
            assertFalse(RefreshSessionPolicy.isConfirmedInvalid(400, body))
        }
    }
}
