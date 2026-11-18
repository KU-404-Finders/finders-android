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
import com.ku.lostandfound.network.LostItemApiService
import com.ku.lostandfound.network.FoundItemApiService
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.network.httpErrorMessage
import kotlinx.coroutines.launch

sealed class PostCommentUiState {
    object Idle : PostCommentUiState()
    object Loading : PostCommentUiState()
    data class Error(val message: String) : PostCommentUiState()
}

class PostCommentViewModel(
    private val lostItemApi: LostItemApiService = RetrofitClient.lostItemApi,
    private val foundItemApi: FoundItemApiService = RetrofitClient.foundItemApi,
) : ViewModel() {
    var uiState by mutableStateOf<PostCommentUiState>(PostCommentUiState.Idle)
        private set

    var commentsByPostId by mutableStateOf<Map<String, List<BoardComment>>>(emptyMap())
        private set

    var isAddingComment by mutableStateOf(false)
        private set

    private val deletingCommentIds = mutableSetOf<Long>()

    private var generation = 0L
    private val loadVersions = mutableMapOf<String, Long>()
    private val editVersions = mutableMapOf<Long, Long>()

    private fun current(cache: Long, session: Long) =
        generation == cache && TokenManager.isCurrentSession(session)

    fun loadComments(postId: Long, postType: PostType) {
        val cache = generation
        val session = TokenManager.sessionGeneration()
        viewModelScope.launch {
            if (!current(cache, session)) return@launch
            loadCommentsNow(postId, postType)
        }
    }

    suspend fun loadCommentsNow(postId: Long, postType: PostType) {
        val cache = generation
        val session = TokenManager.sessionGeneration()
        val key = postKey(postId, postType)
        val version = (loadVersions[key] ?: 0L) + 1
        loadVersions[key] = version
        fun valid() = current(cache, session) && loadVersions[key] == version
        uiState = PostCommentUiState.Loading
        try {
            val response = when (postType) {
                PostType.FOUND -> foundItemApi.getFoundItemComments(postId)
                PostType.LOST -> lostItemApi.getLostItemComments(postId)
            }
            if (!valid()) return
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
                NetworkLog.httpError("loadComments", response.code())
                uiState = PostCommentUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!valid()) return
            NetworkLog.exception("loadComments", e)
            uiState = PostCommentUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
        }
    }

    fun addComment(
        postId: Long,
        postType: PostType,
        content: String,
        onSuccess: (BoardComment) -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) {
        if (content.isBlank() || isAddingComment) return
        isAddingComment = true
        val cache = generation
        val session = TokenManager.sessionGeneration()
        viewModelScope.launch {
            try {
                if (!current(cache, session)) return@launch
                val authorization = bearerTokenOrThrow()
                val request = CommentCreateRequest(content = content.trim())
                val response = when (postType) {
                    PostType.FOUND -> foundItemApi.createFoundItemComment(
                        authorization = authorization,
                        foundItemId = postId,
                        request = request,
                    )
                    PostType.LOST -> lostItemApi.createLostItemComment(
                        authorization = authorization,
                        lostItemId = postId,
                        request = request,
                    )
                }
                if (!current(cache, session)) return@launch
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        val postKey = postKey(postId, postType)
                        loadVersions[postKey] = (loadVersions[postKey] ?: 0L) + 1
                        val current = commentsByPostId[postKey].orEmpty()
                        val createdComment = body.data.toBoardComment(postKey)
                        commentsByPostId = commentsByPostId + (postKey to (current.filterNot { it.id == createdComment.id } + createdComment))
                        uiState = PostCommentUiState.Idle
                        onSuccess(createdComment)
                    } else {
                        val message = body?.message ?: "댓글 등록에 실패했습니다."
                        uiState = PostCommentUiState.Error(message)
                        onFailure(message)
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("addComment", response.code())
                    val message = httpErrorMessage(response.code(), parseErrorMessage(errorBody))
                    uiState = PostCommentUiState.Error(message)
                    onFailure(message)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!current(cache, session)) return@launch
                NetworkLog.exception("addComment", e)
                val message = e.message ?: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요."
                uiState = PostCommentUiState.Error(message)
                onFailure(message)
            } finally {
                if (current(cache, session)) isAddingComment = false
            }
        }
    }

    fun deleteComment(
        postId: Long,
        postType: PostType,
        commentId: Long,
        onFailure: (String) -> Unit = {},
    ) {
        if (!deletingCommentIds.add(commentId)) return
        val cache = generation
        val session = TokenManager.sessionGeneration()
        viewModelScope.launch {
            try {
                if (!current(cache, session)) return@launch
                val authorization = bearerTokenOrThrow()
                val response = when (postType) {
                    PostType.FOUND -> foundItemApi.deleteFoundItemComment(
                        authorization = authorization,
                        foundItemId = postId,
                        commentId = commentId,
                    )
                    PostType.LOST -> lostItemApi.deleteLostItemComment(
                        authorization = authorization,
                        lostItemId = postId,
                        commentId = commentId,
                    )
                }
                if (!current(cache, session)) return@launch
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body == null || body.success) {
                        val postKey = postKey(postId, postType)
                        loadVersions[postKey] = (loadVersions[postKey] ?: 0L) + 1
                        commentsByPostId = commentsByPostId + (
                            postKey to commentsByPostId[postKey].orEmpty().filterNot { it.id == commentId.toString() }
                            )
                        uiState = PostCommentUiState.Idle
                    } else {
                        val message = body?.message ?: "댓글 삭제에 실패했습니다."
                        uiState = PostCommentUiState.Error(message)
                        onFailure(message)
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("deleteComment", response.code())
                    val message = httpErrorMessage(response.code(), parseErrorMessage(errorBody))
                    uiState = PostCommentUiState.Error(message)
                    onFailure(message)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!current(cache, session)) return@launch
                NetworkLog.exception("deleteComment", e)
                val message = e.message ?: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요."
                uiState = PostCommentUiState.Error(message)
                onFailure(message)
            } finally {
                if (current(cache, session)) deletingCommentIds.remove(commentId)
            }
        }
    }

    fun updateComment(postId: Long, postType: PostType, commentId: Long, content: String) {
        val trimmedContent = content.trim()
        if (trimmedContent.isBlank()) return

        val version = (editVersions[commentId] ?: 0L) + 1
        editVersions[commentId] = version
        val cache = generation
        val session = TokenManager.sessionGeneration()
        viewModelScope.launch {
            try {
                if (!current(cache, session)) return@launch
                val authorization = bearerTokenOrThrow()
                val request = CommentUpdateRequest(content = trimmedContent)
                val response = when (postType) {
                    PostType.FOUND -> foundItemApi.updateFoundItemComment(
                        authorization = authorization,
                        foundItemId = postId,
                        commentId = commentId,
                        request = request,
                    )
                    PostType.LOST -> lostItemApi.updateLostItemComment(
                        authorization = authorization,
                        lostItemId = postId,
                        commentId = commentId,
                        request = request,
                    )
                }
                if (!current(cache, session) || editVersions[commentId] != version) return@launch
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        val postKey = postKey(postId, postType)
                        loadVersions[postKey] = (loadVersions[postKey] ?: 0L) + 1
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
                    NetworkLog.httpError("updateComment", response.code())
                    uiState = PostCommentUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!current(cache, session) || editVersions[commentId] != version) return@launch
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
        generation++
        loadVersions.clear()
        editVersions.clear()
        commentsByPostId = emptyMap()
        uiState = PostCommentUiState.Idle
        isAddingComment = false
        deletingCommentIds.clear()
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
