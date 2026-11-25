package com.ku.lostandfound

import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.ku.lostandfound.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class GsonWireRegressionTest {
    private val gson = Gson()
    private var status = 200
    private var json = ""
    private var requestJson = ""
    private var calls = 0
    private val retrofit = Retrofit.Builder().baseUrl("https://fixture.invalid/")
        .client(OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            val request = chain.request()
            requestJson = Buffer().also { request.body?.writeTo(it) }.readUtf8()
            Response.Builder().request(request).code(status).protocol(Protocol.HTTP_1_1)
                .message("fixture").body(json.toResponseBody("application/json".toMediaType())).build()
        }.build())
        .addConverterFactory(GsonConverterFactory.create(gson)).build()

    @Test fun loginAndRefreshPopulateNestedTokenData() = runBlocking {
        json = """{"success":true,"message":"ok","data":{"accessToken":"fixture-a","refreshToken":"fixture-r"}}"""
        val api = retrofit.create(AuthApiService::class.java)
        assertEquals("fixture-a", api.login(LoginRequest("fixture@example.invalid", "fixture")).body()?.data?.accessToken)
        assertEquals(setOf("email", "password"), gson.fromJson(requestJson, Map::class.java).keys)
        assertEquals("fixture-r", api.refreshToken(RefreshTokenRequest("fixture")).body()?.data?.refreshToken)
        assertEquals(setOf("refreshToken"), gson.fromJson(requestJson, Map::class.java).keys)
        assertEquals("fixture-r", gson.fromJson(json, RefreshTokenResponse::class.java).data?.refreshToken)
    }

    @Test fun resetRequestVerifyAndConfirmRespectWireFields() = runBlocking {
        val api = retrofit.create(PasswordResetApiService::class.java)
        json = """{"success":true,"message":"sent"}"""
        assertEquals(true, api.requestReset(PasswordResetRequest("fixture")).body()?.success)
        assertEquals(setOf("email"), gson.fromJson(requestJson, Map::class.java).keys)
        json = """{"success":true,"data":{"resetToken":"fixture-reset","expiresInSeconds":120}}"""
        val token = api.verifyCode(PasswordResetVerifyRequest("fixture", "123456")).body()?.data
        assertEquals("fixture-reset", token?.resetToken)
        assertEquals(120L, token?.expiresInSeconds)
        assertEquals(setOf("email", "authCode"), gson.fromJson(requestJson, Map::class.java).keys)
        json = """{"success":true,"message":"changed"}"""
        assertEquals(true, api.confirmReset(PasswordResetConfirmRequest("fixture-reset", "fixture", "fixture")).body()?.success)
        assertEquals(setOf("resetToken", "newPassword", "newPasswordConfirm"), gson.fromJson(requestJson, Map::class.java).keys)
    }

    @Test fun httpErrorsRetainStatusAndBodyWithoutSuccessConversion() = runBlocking {
        val auth = retrofit.create(AuthApiService::class.java)
        val reset = retrofit.create(PasswordResetApiService::class.java)
        json = """{"success":false,"message":"invalid","errors":{"email":"fixture"}}"""
        for (code in listOf(400, 401, 403, 429)) {
            status = code
            val login = auth.login(LoginRequest("fixture", "fixture"))
            assertEquals(code, login.code())
            assertNull(login.body())
            assertEquals("fixture", gson.fromJson(login.errorBody()?.string(), LoginResponse::class.java).errors?.get("email"))
            val response = reset.requestReset(PasswordResetRequest("fixture"))
            assertEquals(code, response.code())
            assertNull(response.body())
            assertEquals("invalid", gson.fromJson(response.errorBody()?.string(), PasswordResetResponse::class.java).message)
        }
        assertEquals(8, calls)
    }

    @Test fun genericResponseRetainsConcreteListElementType() = runBlocking {
        json = """{"success":true,"data":[{"code":"OTHER","label":"fixture"}]}"""
        val result = retrofit.create(ModerationApiService::class.java).reportReasons().body()
        assertEquals("OTHER", result?.data?.single()?.code)
    }

    @Test fun responseConversionFailureIsNotRetried() = runBlocking {
        json = """{"success":{"unexpected":true}}"""
        val api = retrofit.create(PasswordResetApiService::class.java)
        val error = runCatching { api.requestReset(PasswordResetRequest("fixture")) }.exceptionOrNull()
        assertTrue(error is com.google.gson.JsonParseException)
        assertEquals(1, calls)
    }

    @Test fun userMessagesSeparateConversionFromTransportWithoutLeakingPayloads() {
        val conversion = networkFailureMessage(JsonIOException("fixture-sensitive"))
        val transport = networkFailureMessage(IOException("fixture-sensitive"))
        assertTrue(conversion.contains("서버 응답"))
        assertTrue(transport.contains("서버와 연결"))
        assertFalse(conversion.contains("fixture-sensitive"))
        assertFalse(transport.contains("fixture-sensitive"))
    }
}
