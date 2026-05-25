package com.ku.lostandfound.network

import retrofit2.Response
import retrofit2.http.GET

interface UserApiService {
    @GET("api/v1/users/me")
    suspend fun getMe(): Response<UserMeResponse>
}
