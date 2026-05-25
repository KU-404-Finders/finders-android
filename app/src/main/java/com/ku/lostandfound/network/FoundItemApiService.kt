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
import retrofit2.http.Query

interface FoundItemApiService {
    @GET("api/v1/found-items")
    suspend fun getFoundItemsByBuilding(
        @Query("buildingName") buildingName: String,
    ): Response<FoundItemListResponse>

    @Multipart
    @POST("api/v1/found-items")
    suspend fun createFoundItem(
        @Header("Authorization") authorization: String,
        @Part("request") request: RequestBody,
        @Part image: MultipartBody.Part,
    ): Response<FoundItemDetailResponse>

    @PATCH("api/v1/found-items/{id}/status")
    suspend fun updateFoundItemStatus(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
    ): Response<FoundItemDetailResponse>

    @GET("api/v1/found-items/{id}")
    suspend fun getFoundItemDetail(@Path("id") id: Long): Response<FoundItemDetailResponse>

    @GET("api/v1/found-items/{id}/matches")
    suspend fun getFoundItemMatches(@Path("id") id: Long): Response<FoundItemMatchesResponse>
}
