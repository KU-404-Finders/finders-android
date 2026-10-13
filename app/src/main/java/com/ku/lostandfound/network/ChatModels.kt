package com.ku.lostandfound.network

data class CreateChatRoomRequest(
    val recipientId: Long,
)

data class ChatRoomResponse(
    val id: Long,
    val creatorId: Long,
    val recipientId: Long,
    val createdAt: String,

    // 서버 게시물별 채팅 추가 후 사용할 값
    val postId: Long? = null,
    val postType: String? = null,
    val postTitle: String? = null,

    // 현재는 Android에서 최신 메시지를 조회해서 채움
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val lastMessageSenderId: Long? = null,
)

data class ChatMessageResponse(
    val id: Long,
    val roomId: Long,
    val senderId: Long,
    val content: String,
    val createdAt: String,

    // 1 = 상대가 안 읽음, 0 = 상대가 읽음
    val unreadCount: Int = 0,
)

data class ChatReadRequest(
    val lastReadMessageId: Long,
)

data class ChatReadReceipt(
    val roomId: Long,
    val readerId: Long,
    val lastReadMessageId: Long,
)

data class ChatReadStateResponse(
    val roomId: Long,
    val creatorId: Long,
    val recipientId: Long,
    val creatorLastReadMessageId: Long?,
    val recipientLastReadMessageId: Long?,
)