package com.ku.lostandfound.network

import retrofit2.Response
import retrofit2.http.GET

interface UserApiService {
    @GET("api/v1/users/me")
    suspend fun getMe(): Response<UserMeResponse>

    @GET("api/v1/users/me/lost-items")
    suspend fun getMyLostItems(): Response<MyLostItemsResponse>

    @GET("api/v1/users/me/found-items")
    suspend fun getMyFoundItems(): Response<MyFoundItemsResponse>
}
