package com.ku.lostandfound.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApiService {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/v1/auth/reissue")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") authorization: String): Response<ApiMessageResponse>

    @DELETE("api/v1/auth/withdraw")
    suspend fun withdraw(@Header("Authorization") authorization: String): Response<ApiMessageResponse>
}
