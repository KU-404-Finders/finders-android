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

    @Multipart
    @PATCH("api/v1/lost-items/{id}")
    suspend fun updateLostItem(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
        @Part("request") request: RequestBody,
        @Part image: MultipartBody.Part? = null,
    ): Response<LostItemCreateResponse>

    @DELETE("api/v1/lost-items/{id}")
    suspend fun deleteLostItem(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
    ): Response<ApiMessageResponse>

    @PATCH("api/v1/lost-items/{id}/status")
    suspend fun markLostItemReturned(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
    ): Response<LostItemCreateResponse>

    @GET("api/v1/lost-items/{lostItemId}/comments")
    suspend fun getLostItemComments(
        @Path("lostItemId") lostItemId: Long,
    ): Response<ApiResponse<List<ItemCommentResponse>>>

    @POST("api/v1/lost-items/{lostItemId}/comments")
    suspend fun createLostItemComment(
        @Header("Authorization") authorization: String,
        @Path("lostItemId") lostItemId: Long,
        @Body request: CommentCreateRequest,
    ): Response<ApiResponse<ItemCommentResponse>>

    @PATCH("api/v1/lost-items/{lostItemId}/comments/{commentId}")
    suspend fun updateLostItemComment(
        @Header("Authorization") authorization: String,
        @Path("lostItemId") lostItemId: Long,
        @Path("commentId") commentId: Long,
        @Body request: CommentUpdateRequest,
    ): Response<ApiResponse<ItemCommentResponse>>

    @DELETE("api/v1/lost-items/{lostItemId}/comments/{commentId}")
    suspend fun deleteLostItemComment(
        @Header("Authorization") authorization: String,
        @Path("lostItemId") lostItemId: Long,
        @Path("commentId") commentId: Long,
    ): Response<ApiMessageResponse>
}
