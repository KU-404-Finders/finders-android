package com.ku.lostandfound.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class CreateReportRequest(val postType: String, val postId: Long, val reason: String, val detail: String? = null)
data class ReportResponse(val id: Long, val postType: String, val postId: Long, val status: String)
data class BlockResponse(val blockedUserId: Long)
data class ReportReasonOption(val code: String, val label: String)

interface ModerationApiService {
    @GET("api/v1/reports/reasons") suspend fun reportReasons(): Response<ApiResponse<List<ReportReasonOption>>>
    @POST("api/v1/reports") suspend fun report(@Body request: CreateReportRequest): Response<ApiResponse<ReportResponse>>
    @GET("api/v1/reports/me") suspend fun myReports(): Response<ApiResponse<List<ReportResponse>>>
    @POST("api/v1/users/{userId}/block") suspend fun block(@Path("userId") userId: Long): Response<ApiResponse<BlockResponse>>
    @DELETE("api/v1/users/{userId}/block") suspend fun unblock(@Path("userId") userId: Long): Response<ApiResponse<Unit>>
    @GET("api/v1/users/me/blocks") suspend fun blockedUsers(): Response<ApiResponse<List<BlockResponse>>>
}
