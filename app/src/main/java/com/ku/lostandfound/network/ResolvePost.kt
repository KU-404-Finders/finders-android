package com.ku.lostandfound.network

import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.PostStatus
import com.ku.lostandfound.data.PostType
import kotlinx.coroutines.CancellationException
import retrofit2.Response

internal suspend fun <T> confirmPostResolution(
    type: PostType,
    id: Long,
    session: RequestSession,
    send: suspend () -> Response<T>,
    verify: suspend () -> Response<T>,
    decode: (T) -> BoardPost?,
    httpError: (Int, String?) -> String,
): BoardPost {
    fun matches(post: BoardPost?) = post?.type == type && post.id.toLongOrNull() == id
    val response = try {
        send()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    if (!TokenManager.isCurrentSession(session.generation)) throw CancellationException()
    if (response?.isSuccessful == true) {
        val post = response.body()?.let(decode)
        if (matches(post) && post?.status == PostStatus.RESOLVED) return post
        error("해결 완료 처리에 실패했습니다.")
    }
    val code = response?.code()
    if (code != null && code != 409 && code != 408 && code !in 500..599) {
        error(httpError(code, response.errorBody()?.string()))
    }
    response?.errorBody()?.close()
    val verified = try {
        val detail = verify()
        if (detail.isSuccessful) detail.body()?.let(decode) else {
            detail.errorBody()?.close()
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    if (!TokenManager.isCurrentSession(session.generation)) throw CancellationException()
    if (verified != null && matches(verified)) {
        if (verified.status == PostStatus.RESOLVED) return verified
        error("게시글이 아직 해결되지 않았습니다. 다시 시도해 주세요.")
    }
    error("처리 결과를 확인하지 못했습니다. 게시글을 새로고침해 상태를 확인해 주세요.")
}
