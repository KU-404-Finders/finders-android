package com.ku.lostandfound.network

data class CommentCreateRequest(
    val content: String,
)

data class CommentUpdateRequest(
    val content: String,
)

data class ItemCommentResponse(
    val id: Long,
    val authorUserId: Long,
    val authorName: String,
    val content: String,
    val createdAt: String,
)
