package com.ku.lostandfound.network

data class CreateChatRoomRequest(
    val recipientId: Long,
)

data class ChatRoomResponse(
    val id: Long,
    val creatorId: Long,
    val recipientId: Long,
    val createdAt: String,
)

data class ChatMessageResponse(
    val id: Long,
    val roomId: Long,
    val senderId: Long,
    val content: String,
    val createdAt: String,
)