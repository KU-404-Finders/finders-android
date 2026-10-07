package com.ku.lostandfound.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApiService {

    @POST("api/v1/chat/rooms")
    suspend fun createRoom(
        @Body request: CreateChatRoomRequest,
    ): Response<ApiResponse<ChatRoomResponse>>

    @GET("api/v1/chat/rooms")
    suspend fun getRooms(
        @Query("page") page: Int = 0,
    ): Response<ApiResponse<List<ChatRoomResponse>>>

    @GET("api/v1/chat/rooms/{roomId}/messages")
    suspend fun getMessages(
        @Path("roomId") roomId: Long,
        @Query("beforeId") beforeId: Long? = null,
        @Query("size") size: Int = 50,
    ): Response<ApiResponse<List<ChatMessageResponse>>>
}