package com.ku.lostandfound

import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.post.viewmodel.PostCommentViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import retrofit2.Response

class CommentDeletionRaceRegressionTest : StabilityFixture() {
    private val models = mutableListOf<PostCommentViewModel>()
    @After fun close() { models.forEach { it.viewModelScope.cancel() } }
    private fun row(id: Long, content: String) = ItemCommentResponse(id, 1, "writer", content, "")

    private inner class Comments {
        val edits = mutableMapOf<Long, CompletableDeferred<ItemCommentResponse>>()
        var deleteSuccess = true
        val api = object : LostItemApiService by mock(LostItemApiService::class.java) {
            override suspend fun getLostItemComments(lostItemId: Long) = ok(listOf(row(1, "original"), row(2, "other")))
            override suspend fun updateLostItemComment(authorization: String, lostItemId: Long, commentId: Long, request: CommentUpdateRequest) =
                ok(CompletableDeferred<ItemCommentResponse>().also { edits[commentId] = it }.await())
            override suspend fun deleteLostItemComment(authorization: String, lostItemId: Long, commentId: Long) =
                Response.success(ApiMessageResponse(deleteSuccess, "delete result"))
        }
        val vm = PostCommentViewModel(api, mock(FoundItemApiService::class.java)).also(models::add)
        init { reload() }
        fun reload(postId: Long = 1) { vm.loadComments(postId, PostType.LOST); dispatch() }
        fun edit(id: Long = 1) { vm.updateComment(1, PostType.LOST, id, "edited"); dispatch() }
        fun delete() { vm.deleteComment(1, PostType.LOST, 1); dispatch() }
        fun finish(id: Long = 1) { edits.getValue(id).complete(row(id, "edited")); dispatch() }
        fun contents() = vm.commentsFor(1, PostType.LOST).map { it.content }
        fun assertNoPendingDeleted() {
            // Assert the mutation cache as well as the visible list, before another load can clean it.
            val field = PostCommentViewModel::class.java.getDeclaredField("createdComments").apply { isAccessible = true }
            val pending = field.get(vm) as Map<*, *>
            val rows = pending["LOST:1"] as? List<*>
            assertTrue(rows.orEmpty().none { (it as com.ku.lostandfound.data.BoardComment).id == "1" })
            assertEquals(listOf("other"), contents())
        }
    }

    @Test fun normalEditSurvivesStaleReload() {
        val h = Comments()
        h.edit(); h.finish()
        assertEquals(listOf("edited", "other"), h.contents())
        h.reload()
        assertEquals(listOf("edited", "other"), h.contents())
    }

    @Test fun normalDeleteSurvivesStaleReload() {
        val h = Comments()
        h.delete(); h.assertNoPendingDeleted()
        h.reload(); h.assertNoPendingDeleted()
    }

    @Test fun successfulDeleteRejectsLateEditBeforeAndAfterReload() {
        val h = Comments()
        h.edit(); h.delete(); h.finish()
        h.assertNoPendingDeleted()
        h.reload(); h.assertNoPendingDeleted()
    }

    @Test fun reloadBetweenDeleteAndLateEditCannotResurrectComment() {
        val h = Comments()
        h.edit(); h.delete(); h.reload(); h.finish()
        h.assertNoPendingDeleted()
        h.reload(); h.assertNoPendingDeleted()
    }

    @Test fun failedDeleteAllowsLateEditAndReload() {
        val h = Comments()
        h.deleteSuccess = false
        h.edit(); h.delete(); h.finish()
        assertEquals(listOf("edited", "other"), h.contents())
        h.reload()
        assertEquals(listOf("edited", "other"), h.contents())
    }

    @Test fun reloadPurgesDeletedRowEvenIfPendingCacheContainsLateMutation() {
        val h = Comments()
        h.edit(); h.finish()
        val field = PostCommentViewModel::class.java.getDeclaredField("createdComments").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val pending = field.get(h.vm) as MutableMap<String, List<com.ku.lostandfound.data.BoardComment>>
        val lateMutation = pending.getValue("LOST:1")
        h.delete()
        // Reproduce the stale pending state independently of the edit-response guard.
        pending["LOST:1"] = lateMutation
        h.reload()
        h.assertNoPendingDeleted()
    }

    @Test fun deleteDoesNotSuppressAnotherCommentOrAnotherPost() {
        val h = Comments()
        h.reload(2)
        h.edit(1); h.edit(2); h.delete(); h.finish(2); h.finish(1)
        assertEquals(listOf("edited"), h.contents())
        assertEquals(listOf("original", "other"), h.vm.commentsFor(2, PostType.LOST).map { it.content })
        h.reload()
        assertEquals(listOf("edited"), h.contents())
    }
}
