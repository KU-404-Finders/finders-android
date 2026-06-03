package com.ku.lostandfound.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
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
    suspend fun getFoundItems(
        @Query("buildingName") buildingName: String? = null,
    ): Response<FoundItemListResponse>

    @Multipart
    @POST("api/v1/found-items")
    suspend fun createFoundItem(
        @Header("Authorization") authorization: String,
        @Part("request") request: RequestBody,
        @Part image: MultipartBody.Part,
    ): Response<FoundItemDetailResponse>

    @Multipart
    @PATCH("api/v1/found-items/{id}")
    suspend fun updateFoundItem(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
        @Part("request") request: RequestBody,
        @Part image: MultipartBody.Part? = null,
    ): Response<FoundItemDetailResponse>

    @DELETE("api/v1/found-items/{id}")
    suspend fun deleteFoundItem(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
    ): Response<ApiMessageResponse>

    @PATCH("api/v1/found-items/{id}/status")
    suspend fun updateFoundItemStatus(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
    ): Response<FoundItemDetailResponse>

    @GET("api/v1/found-items/{id}")
    suspend fun getFoundItemDetail(@Path("id") id: Long): Response<FoundItemDetailResponse>

    @GET("api/v1/found-items/{id}/matches")
    suspend fun getFoundItemMatches(@Path("id") id: Long): Response<FoundItemMatchesResponse>

    @GET("api/v1/found-items/{foundItemId}/comments")
    suspend fun getFoundItemComments(
        @Path("foundItemId") foundItemId: Long,
    ): Response<ApiResponse<List<ItemCommentResponse>>>

    @POST("api/v1/found-items/{foundItemId}/comments")
    suspend fun createFoundItemComment(
        @Header("Authorization") authorization: String,
        @Path("foundItemId") foundItemId: Long,
        @Body request: CommentCreateRequest,
    ): Response<ApiResponse<ItemCommentResponse>>

    @PATCH("api/v1/found-items/{foundItemId}/comments/{commentId}")
    suspend fun updateFoundItemComment(
        @Header("Authorization") authorization: String,
        @Path("foundItemId") foundItemId: Long,
        @Path("commentId") commentId: Long,
        @Body request: CommentUpdateRequest,
    ): Response<ApiResponse<ItemCommentResponse>>

    @DELETE("api/v1/found-items/{foundItemId}/comments/{commentId}")
    suspend fun deleteFoundItemComment(
        @Header("Authorization") authorization: String,
        @Path("foundItemId") foundItemId: Long,
        @Path("commentId") commentId: Long,
    ): Response<ApiMessageResponse>
}
