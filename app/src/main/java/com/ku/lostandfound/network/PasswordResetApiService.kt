package com.ku.lostandfound.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** These endpoints are public; no login token is attached. */
interface PasswordResetApiService {
    @POST("api/v1/auth/password-reset/request")
    suspend fun requestReset(@Body request: PasswordResetRequest): Response<PasswordResetResponse>
    @POST("api/v1/auth/password-reset/verify")
    suspend fun verifyCode(@Body request: PasswordResetVerifyRequest): Response<PasswordResetVerifyResponse>
    @POST("api/v1/auth/password-reset/confirm")
    suspend fun confirmReset(@Body request: PasswordResetConfirmRequest): Response<PasswordResetResponse>
}

data class PasswordResetRequest(val email: String)
data class PasswordResetVerifyRequest(val email: String, val authCode: String)
data class PasswordResetConfirmRequest(val resetToken: String, val newPassword: String, val newPasswordConfirm: String)
data class PasswordResetResponse(val success: Boolean, val message: String? = null, val errors: Map<String, String>? = null)
data class PasswordResetVerifyResponse(val success: Boolean, val message: String? = null, val data: PasswordResetTokenData? = null, val errors: Map<String, String>? = null)
data class PasswordResetTokenData(val resetToken: String? = null, val expiresInSeconds: Long? = null)
