package com.ku.lostandfound.network

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class LoginResponseContractTest {
    @Test fun suspendedLoginCarriesOnlyWithdrawalCredentials() {
        val body = Gson().fromJson(
            """{"success":false,"message":"이용이 정지된 계정입니다.","data":{"userId":10,"admin":false,"suspended":true,"withdrawalToken":"withdraw-only"}}""",
            LoginResponse::class.java,
        )
        assertFalse(body.success)
        assertTrue(body.data!!.suspended)
        assertEquals("withdraw-only", body.data.withdrawalToken)
        assertNull(body.data.accessToken)
        assertNull(body.data.refreshToken)
    }

    @Test fun ordinaryLoginStillParsesExistingTokenFields() {
        val body = Gson().fromJson(
            """{"success":true,"message":"로그인 성공","data":{"accessToken":"access","refreshToken":"refresh"}}""",
            LoginResponse::class.java,
        )
        assertTrue(body.success)
        assertFalse(body.data!!.suspended)
        assertEquals("access", body.data.accessToken)
        assertEquals("refresh", body.data.refreshToken)
        assertNull(body.data.withdrawalToken)
    }
}
