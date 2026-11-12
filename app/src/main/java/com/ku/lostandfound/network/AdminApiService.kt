package com.ku.lostandfound.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

data class AdminDashboard(
    val pendingReportCount: Long,
    val userCount: Long,
    val lostPostCount: Long,
    val foundPostCount: Long,
)

data class AdminPage<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val hasNext: Boolean,
)

data class AdminReport(
    val id: Long,
    val reporterId: Long? = null,
    val reporterName: String,
    val reportedUserId: Long? = null,
    val reportedUserName: String,
    val postType: String? = null,
    val postId: Long? = null,
    val postTitle: String,
    val reason: String,
    val reasonCode: String? = null,
    val detail: String? = null,
    val status: String,
    val isProcessed: Boolean,
    val processedAction: String? = null,
    val action: String? = null,
    val processedBy: Long? = null,
    val processedAt: String? = null,
    val processingNote: String? = null,
    val createdAt: String,
)

data class ProcessAdminReportRequest(val action: String, val note: String? = null)
data class AdminSuspensionRequest(val suspended: Boolean, val reason: String? = null)

data class AdminUser(
    val id: Long,
    val email: String,
    val name: String,
    val role: String,
    val suspended: Boolean,
    val suspensionReason: String? = null,
    val suspendedAt: String? = null,
    val createdAt: String,
)

data class AdminPost(
    val postType: String,
    val id: Long,
    val title: String,
    val content: String,
    val userId: Long,
    val userName: String,
    val imageUrl: String? = null,
    val itemStatus: String,
    val createdAt: String,
)

data class AdminAudit(
    val id: Long,
    val adminId: Long,
    val action: String,
    val targetType: String,
    val targetId: Long,
    val postType: String? = null,
    val note: String? = null,
    val createdAt: String,
)

interface AdminApiService {
    @GET("api/v1/admin/dashboard")
    suspend fun dashboard(): Response<ApiResponse<AdminDashboard>>

    @GET("api/v1/admin/reports")
    suspend fun reports(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Response<ApiResponse<AdminPage<AdminReport>>>

    @GET("api/v1/admin/reports/{id}")
    suspend fun report(@Path("id") id: Long): Response<ApiResponse<AdminReport>>

    @PATCH("api/v1/admin/reports/{id}")
    suspend fun processReport(
        @Path("id") id: Long,
        @Body request: ProcessAdminReportRequest,
    ): Response<ApiResponse<AdminReport>>

    @GET("api/v1/admin/users")
    suspend fun users(
        @Query("keyword") keyword: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Response<ApiResponse<AdminPage<AdminUser>>>

    @GET("api/v1/admin/users/{id}")
    suspend fun user(@Path("id") id: Long): Response<ApiResponse<AdminUser>>

    @PATCH("api/v1/admin/users/{id}/suspension")
    suspend fun setSuspension(
        @Path("id") id: Long,
        @Body request: AdminSuspensionRequest,
    ): Response<ApiResponse<AdminUser>>

    @GET("api/v1/admin/posts")
    suspend fun posts(
        @Query("postType") postType: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Response<ApiResponse<AdminPage<AdminPost>>>

    @DELETE("api/v1/admin/posts/{postType}/{id}")
    suspend fun deletePost(
        @Path("postType") postType: String,
        @Path("id") id: Long,
    ): Response<ApiResponse<Unit>>

    @GET("api/v1/admin/audit-logs")
    suspend fun auditLogs(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Response<ApiResponse<AdminPage<AdminAudit>>>
}
