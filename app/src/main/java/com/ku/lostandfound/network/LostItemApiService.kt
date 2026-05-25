package com.ku.lostandfound.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface LostItemApiService {
    @GET("api/v1/lost-items")
    suspend fun getLostItems(): Response<LostItemListResponse>

    @GET("api/v1/lost-items/{id}")
    suspend fun getLostItemDetail(@Path("id") id: Long): Response<LostItemCreateResponse>

    @GET("api/v1/lost-items/{id}/matches")
    suspend fun getLostItemMatches(@Path("id") id: Long): Response<LostItemMatchesResponse>

    @Multipart
    @POST("api/v1/lost-items")
    suspend fun createLostItem(
        @Header("Authorization") authorization: String,
        @Part("request") request: RequestBody,
        @Part image: MultipartBody.Part? = null,
    ): Response<LostItemCreateResponse>

    @PATCH("api/v1/lost-items/{id}/status")
    suspend fun markLostItemReturned(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
    ): Response<LostItemCreateResponse>
}
