package com.ku.lostandfound.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface SignupApiService {
    @POST("api/v1/auth/email/send")
    suspend fun sendVerificationCode(@Query("email") email: String): Response<SignupResponse>

    @POST("api/v1/auth/join")
    suspend fun register(@Body request: SignupRequest): Response<SignupResponse>
}
