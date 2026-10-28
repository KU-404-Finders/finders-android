package com.ku.lostandfound.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PUT
import retrofit2.http.Path

data class PushDeviceRequest(
    val platform: String = "ANDROID",
    val installationId: String,
)

interface PushDeviceApiService {

    // 현재 로그인한 사용자에게 Firebase 기기 등록
    @PUT("api/v1/users/me/push-devices")
    suspend fun registerDevice(
        @Body request: PushDeviceRequest,
    ): Response<Unit>

    // 로그아웃 시 현재 기기 등록 해제
    @DELETE("api/v1/users/me/push-devices/{installationId}")
    suspend fun unregisterDevice(
        @Path("installationId") installationId: String,
    ): Response<Unit>
}