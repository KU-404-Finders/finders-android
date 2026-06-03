package com.ku.lostandfound.ui.post.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ku.lostandfound.data.BoardComment
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.network.AuthErrorResponse
import com.ku.lostandfound.network.CommentCreateRequest
import com.ku.lostandfound.network.CommentUpdateRequest
import com.ku.lostandfound.network.ItemCommentResponse
import com.ku.lostandfound.network.NetworkLog
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.network.httpErrorMessage
import kotlinx.coroutines.launch

sealed class PostCommentUiState {
    object Idle : PostCommentUiState()
    object Loading : PostCommentUiState()
    data class Error(val message: String) : PostCommentUiState()
}

class PostCommentViewModel : ViewModel() {
    var uiState by mutableStateOf<PostCommentUiState>(PostCommentUiState.Idle)
        private set

    var commentsByPostId by mutableStateOf<Map<String, List<BoardComment>>>(emptyMap())
        private set

    fun loadComments(postId: Long, postType: PostType) {
        viewModelScope.launch {
            loadCommentsNow(postId, postType)
        }
    }

    suspend fun loadCommentsNow(postId: Long, postType: PostType) {
            uiState = PostCommentUiState.Loading
            try {
                val response = when (postType) {
                    PostType.FOUND -> RetrofitClient.foundItemApi.getFoundItemComments(postId)
                    PostType.LOST -> RetrofitClient.lostItemApi.getLostItemComments(postId)
                }
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        val postKey = postKey(postId, postType)
                        commentsByPostId = commentsByPostId + (
                            postKey to body.data.orEmpty().map { it.toBoardComment(postKey) }
                            )
                        uiState = PostCommentUiState.Idle
                    } else {
                        uiState = PostCommentUiState.Error(body?.message ?: "댓글을 불러오지 못했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("loadComments", response.code(), errorBody)
                    uiState = PostCommentUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("loadComments", e)
                uiState = PostCommentUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
    }

    fun addComment(postId: Long, postType: PostType, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            try {
                val authorization = bearerTokenOrThrow()
                val request = CommentCreateRequest(content = content.trim())
                val response = when (postType) {
                    PostType.FOUND -> RetrofitClient.foundItemApi.createFoundItemComment(
                        authorization = authorization,
                        foundItemId = postId,
                        request = request,
                    )
                    PostType.LOST -> RetrofitClient.lostItemApi.createLostItemComment(
                        authorization = authorization,
                        lostItemId = postId,
                        request = request,
                    )
                }
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        val postKey = postKey(postId, postType)
                        val current = commentsByPostId[postKey].orEmpty()
                        commentsByPostId = commentsByPostId + (postKey to (current + body.data.toBoardComment(postKey)))
                        uiState = PostCommentUiState.Idle
                    } else {
                        uiState = PostCommentUiState.Error(body?.message ?: "댓글 등록에 실패했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("addComment", response.code(), errorBody)
                    uiState = PostCommentUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("addComment", e)
                uiState = PostCommentUiState.Error(e.message ?: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun deleteComment(postId: Long, postType: PostType, commentId: Long) {
        viewModelScope.launch {
            try {
                val authorization = bearerTokenOrThrow()
                val response = when (postType) {
                    PostType.FOUND -> RetrofitClient.foundItemApi.deleteFoundItemComment(
                        authorization = authorization,
                        foundItemId = postId,
                        commentId = commentId,
                    )
                    PostType.LOST -> RetrofitClient.lostItemApi.deleteLostItemComment(
                        authorization = authorization,
                        lostItemId = postId,
                        commentId = commentId,
                    )
                }
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body == null || body.success) {
                        val postKey = postKey(postId, postType)
                        commentsByPostId = commentsByPostId + (
                            postKey to commentsByPostId[postKey].orEmpty().filterNot { it.id == commentId.toString() }
                            )
                        uiState = PostCommentUiState.Idle
                    } else {
                        uiState = PostCommentUiState.Error(body?.message ?: "댓글 삭제에 실패했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("deleteComment", response.code(), errorBody)
                    uiState = PostCommentUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("deleteComment", e)
                uiState = PostCommentUiState.Error(e.message ?: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun updateComment(postId: Long, postType: PostType, commentId: Long, content: String) {
        val trimmedContent = content.trim()
        if (trimmedContent.isBlank()) return

        viewModelScope.launch {
            try {
                val authorization = bearerTokenOrThrow()
                val request = CommentUpdateRequest(content = trimmedContent)
                val response = when (postType) {
                    PostType.FOUND -> RetrofitClient.foundItemApi.updateFoundItemComment(
                        authorization = authorization,
                        foundItemId = postId,
                        commentId = commentId,
                        request = request,
                    )
                    PostType.LOST -> RetrofitClient.lostItemApi.updateLostItemComment(
                        authorization = authorization,
                        lostItemId = postId,
                        commentId = commentId,
                        request = request,
                    )
                }
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        val postKey = postKey(postId, postType)
                        commentsByPostId = commentsByPostId + (
                            postKey to commentsByPostId[postKey].orEmpty().map { comment ->
                                if (comment.id == commentId.toString()) {
                                    body.data.toBoardComment(postKey)
                                } else {
                                    comment
                                }
                            }
                            )
                        uiState = PostCommentUiState.Idle
                    } else {
                        uiState = PostCommentUiState.Error(body?.message ?: "댓글 수정에 실패했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("updateComment", response.code(), errorBody)
                    uiState = PostCommentUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("updateComment", e)
                uiState = PostCommentUiState.Error(e.message ?: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    private fun ItemCommentResponse.toBoardComment(postId: String): BoardComment {
        return BoardComment(
            id = id.toString(),
            postId = postId,
            authorUserId = authorUserId,
            authorName = authorName,
            authorEmail = "",
            content = content,
            createdAtText = createdAt.take(10),
        )
    }

    private fun bearerTokenOrThrow(): String {
        val token = TokenManager.accessToken ?: throw IllegalStateException("로그인이 필요합니다.")
        return "Bearer $token"
    }

    fun commentsFor(postId: Long, postType: PostType): List<BoardComment> {
        return commentsByPostId[postKey(postId, postType)].orEmpty()
    }

    fun clear() {
        commentsByPostId = emptyMap()
        uiState = PostCommentUiState.Idle
    }

    private fun postKey(postId: Long, postType: PostType): String = "${postType.name}:$postId"

    private fun parseErrorMessage(errorBody: String?): String {
        if (errorBody == null) return "오류가 발생했습니다."
        return try {
            val errorResponse = Gson().fromJson(errorBody, AuthErrorResponse::class.java)
            errorResponse.errors?.values?.firstOrNull() ?: errorResponse.message ?: "요청을 처리할 수 없습니다."
        } catch (e: Exception) {
            "오류가 발생했습니다."
        }
    }
}
