package com.ku.lostandfound

import android.content.ContentResolver
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.GeoPoint
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.data.mergeBoardPosts
import com.ku.lostandfound.network.FoundItemApiService
import com.ku.lostandfound.network.LostItemApiService
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.RefreshSessionResult
import com.ku.lostandfound.network.RequestNotSentException
import com.ku.lostandfound.network.RequestSession
import com.ku.lostandfound.viewmodel.PostCreationJournal
import okhttp3.RequestBody.Companion.toRequestBody
import com.ku.lostandfound.ui.navigation.PostEditRestore
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.viewmodel.PostWriteUiState
import com.ku.lostandfound.viewmodel.PostWriteViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

class PostSubmissionRegressionTest : StabilityFixture() {
    private lateinit var server: MockWebServer
    private lateinit var vm: PostWriteViewModel
    private lateinit var retrofit: Retrofit
    private val journalValues = mutableMapOf<String, String>()
    private lateinit var journalEditor: SharedPreferences.Editor

    @Before fun setupWriter() {
        server = MockWebServer()
        server.start()
        login(jwt(5))
        retrofit = Retrofit.Builder().baseUrl(server.url("/")).callFactory(RetrofitClient::newCall)
            .addConverterFactory(GsonConverterFactory.create()).build()
        vm = newWriter()
        `when`(application.applicationContext).thenReturn(application)
        val values = journalValues
        val journal = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        journalEditor = editor
        `when`(application.getSharedPreferences("post_creation_attempts", 0)).thenReturn(journal)
        `when`(journal.getString(anyString(), isNull())).thenAnswer { values[it.getArgument<String>(0)] }
        `when`(journal.edit()).thenReturn(editor)
        `when`(journal.all).thenAnswer { values.toMap() }
        `when`(editor.putString(anyString(), anyString())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument(1)
            editor
        }
        `when`(editor.remove(anyString())).thenAnswer {
            values.remove(it.getArgument<String>(0))
            editor
        }
        `when`(editor.commit()).thenReturn(true)
        vm.title = "검은 지갑"
        vm.category = "지갑"
        vm.content = "사진 속 물건"
        vm.changePostType(PostType.LOST)
        vm.setLostOutdoorPins(listOf(GeoPoint(127.076, 37.5418)))
    }

    private fun newWriter() = PostWriteViewModel(retrofit.create(FoundItemApiService::class.java),
        retrofit.create(LostItemApiService::class.java), main)

    @After fun closeWriter() {
        vm.viewModelScope.cancel()
        dispatch()
        server.close()
    }

    @Test fun firstFoundPostSendsOneMultipartRequestWhileAuthorAndResponseAreDelayed() {
        val uriString = "content://fixture/photo"
        val uri = mock(Uri::class.java)
        val resolver = mock(ContentResolver::class.java)
        `when`(application.contentResolver).thenReturn(resolver)
        `when`(resolver.getType(uri)).thenReturn("image/png")
        `when`(resolver.openInputStream(uri)).thenAnswer { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        vm.changePostType(PostType.FOUND)
        vm.setFoundOutdoorPin(GeoPoint(127.076, 37.5418))
        vm.imageUri = uriString
        val author = CompletableDeferred<Pair<String, String>>()
        mockStatic(Uri::class.java).use { uris ->
            uris.`when`<Uri> { Uri.parse(uriString) }.thenReturn(uri)
            server.enqueue(success(""""locationType":"OUTDOOR","latitude":37.5418,"longitude":127.076,"associatedBuildingNames":[]""")
                .setBodyDelay(250, TimeUnit.MILLISECONDS))
            vm.save(application) { author.await() }
            repeat(5) { vm.save(application) { error("duplicate must not resolve an author") } }
            dispatch()
            assertTrue(vm.isWriting)
            assertEquals(0, server.requestCount)

            author.complete("신규 사용자" to "new@example.invalid")
            val request = request()
            assertEquals("/api/v1/found-items", request.path)
            val multipart = request.body.readUtf8()
            assertTrue(multipart.contains("name=\"request\""))
            assertTrue(multipart.contains("name=\"image\""))
            repeat(5) { vm.save(application) }
            awaitState { it is PostWriteUiState.Saved }
            val saved = vm.uiState as PostWriteUiState.Saved
            assertEquals("신규 사용자", saved.post.authorName)
            assertEquals(PostType.FOUND, saved.post.type)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun firstLostPostAndEditUseOneRequestEach() {
        server.enqueue(success(""""indoorSpots":[],"outdoorPoints":[]"""))
        repeat(5) { vm.save(application) }
        assertEquals("/api/v1/lost-items", request().path)
        awaitState { it is PostWriteUiState.Saved }
        val created = vm.uiState as PostWriteUiState.Saved
        assertFalse(created.isEdit)
        vm.reset()
        vm.loadForEdit(created.post.copy(lostLocation = com.ku.lostandfound.data.LostLocationSelection(
            outdoorPins = listOf(com.ku.lostandfound.data.OutdoorPin(1, GeoPoint(127.076, 37.5418))),
        )))
        server.enqueue(success(""""indoorSpots":[],"outdoorPoints":[]"""))
        repeat(5) { vm.save(application, editedId = 7L) }
        val edit = request()
        assertEquals("PATCH", edit.method)
        assertEquals("/api/v1/lost-items/7", edit.path)
        awaitState { it is PostWriteUiState.Saved }
        assertTrue((vm.uiState as PostWriteUiState.Saved).isEdit)
        assertEquals(2, server.requestCount)
    }

    @Test fun lostResponseDoesNotRepeatPostOrOfferAutomaticRetry() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        vm.save(application)
        request()
        awaitState { it == PostWriteUiState.Unconfirmed }
        vm.save(application)
        dispatch()
        assertEquals(1, server.requestCount)
        vm.acknowledgeUnconfirmed()
        dispatch()
        assertEquals(PostWriteUiState.Idle, vm.uiState)
    }

    @Test fun retryAfterZeroAndAuthenticationFailureDoNotReplayPost() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        vm.save(application)
        request()
        awaitState { it == PostWriteUiState.Unconfirmed }
        assertEquals(1, server.requestCount)

        vm.acknowledgeUnconfirmed()
        dispatch()
        server.enqueue(MockResponse().setResponseCode(401))
        vm.save(application)
        request()
        awaitState { it is PostWriteUiState.Error }
        assertEquals(2, server.requestCount)
    }

    @Test fun logoutAfterDispatchDiscardsResponseAndUnlocksWriter() {
        server.enqueue(success(""""indoorSpots":[],"outdoorPoints":[]""").setBodyDelay(200, TimeUnit.MILLISECONDS))
        vm.save(application)
        request()
        TokenManager.clear()
        awaitState { it == PostWriteUiState.Idle }
        assertEquals(1, server.requestCount)
    }

    @Test fun cancellationBeforeDispatchReleasesLoadingWithoutSending() {
        val author = CompletableDeferred<Pair<String, String>>()
        vm.save(application) { author.await() }
        dispatch()
        vm.reset()
        assertEquals(PostWriteUiState.Idle, vm.uiState)
        author.complete("A" to "a@example.invalid")
        dispatch()
        assertEquals(0, server.requestCount)
    }

    @Test fun mergePreservesTypeAndDifferentIdsWithTheSameContent() {
        val found = BoardPost("1", PostType.FOUND, title = "지갑", category = "지갑", content = "검정")
        val lost = found.copy(type = PostType.LOST)
        val other = found.copy(id = "2")
        assertEquals(listOf(found, lost, other), mergeBoardPosts(listOf(found, lost),
            listOf(found.copy(title = "이전 제목"), lost, other)))
    }

    @Test fun restoredEditReloadsBothTypesWithTheSameIdAndOnlyARecreatedWriterReloadsAgain() = runBlocking {
        val lost = """"indoorSpots":[],"outdoorPoints":[]"""
        val found = """"locationType":"OUTDOOR","latitude":37.5418,"longitude":127.076,"associatedBuildingNames":[]"""
        server.enqueue(success(lost))
        assertTrue(vm.restoreEdit(PostType.LOST, 7))
        assertEquals("/api/v1/lost-items/7", request().path)
        vm.title = "회전 전 제목"
        assertTrue(vm.restoreEdit(PostType.LOST, 7))
        assertEquals("회전 전 제목", vm.title)
        assertEquals(1, server.requestCount)

        val recreated = newWriter()
        server.enqueue(success(lost))
        assertTrue(recreated.restoreEdit(PostType.LOST, 7))
        assertEquals("/api/v1/lost-items/7", request().path)
        server.enqueue(success(found))
        assertTrue(recreated.restoreEdit(PostType.FOUND, 7))
        assertEquals("/api/v1/found-items/7", request().path)
        assertEquals(PostType.FOUND, recreated.postType)
        assertFalse(recreated.isEditReady(PostType.LOST, 7))
    }

    @Test fun invalidIdMismatchAndMissingDetailNeverEnableAnEditWrite() = runBlocking {
        assertNull(com.ku.lostandfound.ui.navigation.Route.PostEdit.parse("OTHER", 7))
        assertFalse(vm.restoreEdit(PostType.LOST, 0))
        assertEquals(0, server.requestCount)
        server.enqueue(success(""""indoorSpots":[],"outdoorPoints":[]""", id = 8))
        assertFalse(vm.restoreEdit(PostType.LOST, 7))
        request()
        server.enqueue(MockResponse().setResponseCode(404))
        assertFalse(vm.restoreEdit(PostType.LOST, 7))
        request()
        vm.save(application, editedId = 7)
        dispatch()
        assertEquals(2, server.requestCount)
    }

    @Test fun uncertainCreationSurvivesResetAndWriterRecreationAndCanBeAcknowledged() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        vm.save(application)
        request()
        awaitState { it == PostWriteUiState.Unconfirmed }
        vm.reset()
        vm.restoreCreationStatus(application)
        assertEquals(PostWriteUiState.Unconfirmed, vm.uiState)
        val recreated = newWriter()
        recreated.restoreCreationStatus(application)
        assertEquals(PostType.LOST, recreated.unconfirmedType)
        assertEquals(PostWriteUiState.Unconfirmed, recreated.uiState)
        recreated.acknowledgeUnconfirmed()
        dispatch()
        assertEquals(PostWriteUiState.Idle, recreated.uiState)
        assertEquals(1, server.requestCount)
    }

    @Test fun cancellationAfterDispatchKeepsAttemptAndOtherAccountsDoNotSeeIt() {
        val first = TokenManager.accessToken!!
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        vm.save(application)
        request()
        vm.reset()
        dispatch()
        login(jwt(6, email = "other@example.invalid"))
        vm.restoreCreationStatus(application)
        assertEquals(PostWriteUiState.Idle, vm.uiState)
        login(first)
        vm.restoreCreationStatus(application)
        assertEquals(PostWriteUiState.Unconfirmed, vm.uiState)
        assertEquals(1, server.requestCount)
    }

    @Test fun expiredEditRefreshesThroughTheRealReissueClientBeforeReloading() = runBlocking {
        login(jwt(5, expiry = 1))
        val restore = PostEditRestore(checkNotNull(TokenManager.reminderSession()), PostType.LOST, 7)
        val refreshed = jwt(5)
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"accessToken":"$refreshed","refreshToken":"fixture-r"}}"""))
        withLocalRefresh {
            assertEquals(RefreshSessionResult.Success, restore.authenticate())
        }
        val reissue = request()
        assertEquals("/api/v1/auth/reissue", reissue.path)
        assertTrue(reissue.body.readUtf8().contains("refreshToken"))
        assertTrue(restore.belongsToCurrentSession())
        assertEquals("postEdit/LOST/7", restore.route)
        server.enqueue(success(""""indoorSpots":[],"outdoorPoints":[]"""))
        assertTrue(vm.restoreEdit(restore.type, restore.id))
        val detail = request()
        assertEquals("/api/v1/lost-items/7", detail.path)
        assertEquals("Bearer $refreshed", detail.getHeader("Authorization"))
    }

    @Test fun failedRefreshAndNewLoginCannotRestoreTheOldEdit() = runBlocking {
        login(jwt(5, expiry = 1))
        val restore = PostEditRestore(checkNotNull(TokenManager.reminderSession()), PostType.FOUND, 7)
        server.enqueue(MockResponse().setResponseCode(503))
        withLocalRefresh {
            assertEquals(RefreshSessionResult.RetryableFailure, restore.authenticate())
        }
        assertEquals("/api/v1/auth/reissue", request().path)
        login(jwt(5))
        assertNull(restore.authenticate { fail("The old edit must not refresh a new session"); RefreshSessionResult.Success })
        assertEquals(1, server.requestCount)
    }

    @Test fun sameEmailReenrollmentDoesNotInheritTheOldOrLegacyJournalEntry() {
        val legacy = "a".repeat(64)
        journalValues[legacy] = "FOUND:legacy"
        journalValues["session:old"] = "LOST:legacy"
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        vm.save(application)
        request()
        awaitState { it == PostWriteUiState.Unconfirmed }
        assertFalse(journalValues.containsKey(legacy))
        assertFalse(journalValues.containsKey("session:old"))
        vm.reset()
        login(jwt(6))
        vm.restoreCreationStatus(application)
        assertEquals(PostWriteUiState.Idle, vm.uiState)
        login(jwt(5, email = "renamed@example.invalid"))
        vm.restoreCreationStatus(application)
        assertEquals(PostWriteUiState.Unconfirmed, vm.uiState)
    }

    @Test fun createResponseWithoutSuccessNullSuccessOrMissingDataStaysUnconfirmed() {
        val bodies = listOf(
            """{"message":"ok","data":null}""",
            """{"success":null,"message":"ok"}""",
            """{"success":true,"message":"ok","data":null}""",
            "{}",
        )
        bodies.forEachIndexed { index, body ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
            vm.save(application)
            request()
            awaitState { it == PostWriteUiState.Unconfirmed }
            assertTrue("journal must survive for $body", journalValues.containsKey("user:5"))
            vm.save(application)
            dispatch()
            assertEquals("no replay for $body", index + 1, server.requestCount)
            vm.acknowledgeUnconfirmed()
            dispatch()
            assertEquals(PostWriteUiState.Idle, vm.uiState)
        }
    }

    @Test fun explicitSuccessFalseIsARejectionThatReleasesTheJournal() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setBody("""{"success":false,"message":"등록 거절","data":null}"""))
        vm.save(application)
        request()
        awaitState { it is PostWriteUiState.Error }
        assertEquals("등록 거절", (vm.uiState as PostWriteUiState.Error).message)
        assertTrue(journalValues.isEmpty())
        vm.resetUiState()
        server.enqueue(success(""""indoorSpots":[],"outdoorPoints":[]"""))
        vm.save(application)
        request()
        awaitState { it is PostWriteUiState.Saved }
        assertTrue(journalValues.isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test fun sessionChangeBeforeDispatchClearsTheJournalWithoutSending() {
        val first = TokenManager.accessToken!!
        // Retrofit tags the call with the old session; switching accounts before execution trips the interceptor.
        val switching = Retrofit.Builder().baseUrl(server.url("/")).callFactory { request ->
            login(jwt(6, email = "other@example.invalid"))
            RetrofitClient.newCall(request)
        }.addConverterFactory(GsonConverterFactory.create()).build()
        vm.viewModelScope.cancel()
        vm = PostWriteViewModel(switching.create(FoundItemApiService::class.java),
            switching.create(LostItemApiService::class.java), main)
        vm.title = "검은 지갑"; vm.category = "지갑"; vm.content = "사진 속 물건"
        vm.changePostType(PostType.LOST)
        vm.setLostOutdoorPins(listOf(GeoPoint(127.076, 37.5418)))
        vm.save(application)
        awaitState { it == PostWriteUiState.Idle }
        assertEquals(0, server.requestCount)
        assertTrue(journalValues.isEmpty())
        login(first)
        vm.restoreCreationStatus(application)
        assertEquals(PostWriteUiState.Idle, vm.uiState)
    }

    @Test fun staleSessionMutationIsRejectedBeforeDispatch() {
        val stale = RequestSession(TokenManager.sessionGeneration())
        login(jwt(6))
        val request = okhttp3.Request.Builder().url(server.url("/api/v1/lost-items"))
            .post("{}".toRequestBody()).tag(RequestSession::class.java, stale).build()
        assertThrows(RequestNotSentException::class.java) { RetrofitClient.newCall(request).execute() }
        assertEquals(0, server.requestCount)
    }

    @Test fun authenticationFailureOnUnauthorizedMutationNeverReissuesOrReplays() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403)
            .setBody("""{"timestamp":"2026-11-26T00:00:00Z","status":403,"error":"Forbidden"}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"accessToken":"${jwt(5)}","refreshToken":"r"}}"""))
        val request = okhttp3.Request.Builder().url(server.url("/api/v1/lost-items"))
            .post("{}".toRequestBody())
            .tag(RequestSession::class.java, RequestSession(TokenManager.sessionGeneration())).build()
        withLocalRefresh {
            RetrofitClient.newCall(request).execute().use { assertEquals(403, it.code) }
        }
        val sent = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("/api/v1/lost-items", sent.path)
        assertNotNull("the interceptor attached the session token", sent.getHeader("Authorization"))
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
        assertEquals(1, server.requestCount)
    }

    @Test fun failedJournalWriteNeverSendsAndDoesNotBlockTheNextAttempt() {
        `when`(journalEditor.commit()).thenReturn(false, true)
        vm.save(application)
        awaitState { it is PostWriteUiState.Error }
        assertEquals(0, server.requestCount)
        assertTrue("an unsaved record must not linger in memory", journalValues.isEmpty())
        vm.resetUiState()
        `when`(journalEditor.commit()).thenReturn(true)
        server.enqueue(success(""""indoorSpots":[],"outdoorPoints":[]"""))
        vm.save(application)
        request()
        awaitState { it is PostWriteUiState.Saved }
        assertEquals(1, server.requestCount)
    }

    @Test fun cleanupFailureAfterSuccessKeepsSavedAndRetriesTheSameEntry() {
        server.enqueue(success(""""indoorSpots":[],"outdoorPoints":[]"""))
        // begin succeeds; both success-path clear attempts fail to reach disk.
        `when`(journalEditor.commit()).thenReturn(true, false, false)
        vm.save(application)
        request()
        awaitState { it is PostWriteUiState.Saved }
        assertEquals(1, server.requestCount)
        verify(journalEditor, times(3)).commit()
    }

    @Test fun journalRetriesAFailedDeletionButNeverRemovesAnotherAttempt() {
        val entry = checkNotNull(PostCreationJournal.begin(application, "user:5", PostType.LOST))
        `when`(journalEditor.commit()).thenReturn(false)
        assertFalse(PostCreationJournal.clear(application, entry))
        `when`(journalEditor.commit()).thenReturn(true)
        clearInvocations(journalEditor)
        assertTrue(PostCreationJournal.clear(application, entry))
        verify(journalEditor).commit()
        clearInvocations(journalEditor)
        assertTrue(PostCreationJournal.clear(application, entry))
        verify(journalEditor, never()).commit()

        val other = checkNotNull(PostCreationJournal.begin(application, "user:5", PostType.FOUND))
        assertTrue(PostCreationJournal.clear(application, entry))
        assertEquals(other, PostCreationJournal.pending(application, "user:5"))
        assertTrue(PostCreationJournal.clear(application, other))
        assertNull(PostCreationJournal.pending(application, "user:5"))
    }

    private fun jwt(id: Long, expiry: Long = 4102444800, email: String = "new@example.invalid"): String {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"$email","user_id":$id,"exp":$expiry}""".toByteArray(),
        )
        return "e30.$payload.fixture"
    }

    private suspend fun withLocalRefresh(test: suspend () -> Unit) {
        val client = RetrofitClient::class.java.getDeclaredField("refreshClient").apply { isAccessible = true }
            .get(RetrofitClient) as OkHttpClient
        val field = OkHttpClient::class.java.getDeclaredField("interceptors").apply { isAccessible = true }
        val previous = field.get(client)
        try {
            field.set(client, listOf(Interceptor { chain ->
                val request = chain.request()
                chain.proceed(request.newBuilder().url(server.url(request.url.encodedPath)).build())
            }))
            test()
        } finally {
            field.set(client, previous)
        }
    }

    private fun request(): RecordedRequest {
        dispatch()
        return checkNotNull(server.takeRequest(4, TimeUnit.SECONDS)) { "No write reached the test server" }
    }

    private fun awaitState(predicate: (PostWriteUiState) -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        do {
            dispatch()
            if (predicate(vm.uiState)) return
            Thread.sleep(10)
        } while (System.nanoTime() < deadline)
        fail("Unexpected writer state: ${vm.uiState}")
    }

    private fun success(location: String, id: Long = 7L): MockResponse = MockResponse().setHeader("Content-Type", "application/json")
        .setBody("""{"success":true,"message":"ok","data":{"id":$id,"userId":5,"title":"검은 지갑",
            "kind":"지갑","content":"사진 속 물건","itemStatus":"SEARCHING","createdAt":"2026-11-26",
            "updatedAt":"2026-11-26",$location}}""")

    @Test fun cancellationAfterTheJournalWriteButBeforeThePostClearsOnlyThatAttempt() {
        journalValues["user:9"] = "FOUND:other-account"
        var afterIo: (() -> Unit)? = null
        // Runs IO work in place, then lets the test act before the writer resumes on main.
        val io = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                block.run()
                afterIo?.also { afterIo = null }?.invoke()
            }
        }
        val writer = PostWriteViewModel(retrofit.create(FoundItemApiService::class.java),
            retrofit.create(LostItemApiService::class.java), io)
        writer.title = "검은 지갑"; writer.category = "지갑"; writer.content = "사진 속 물건"
        writer.changePostType(PostType.LOST)
        writer.setLostOutdoorPins(listOf(GeoPoint(127.076, 37.5418)))
        var recordedBeforeCancel = false
        afterIo = {
            recordedBeforeCancel = journalValues.containsKey("user:5")
            writer.reset()
        }
        try {
            writer.save(application)
            dispatch()
            assertTrue("the journal was written before the cancel", recordedBeforeCancel)
            assertEquals(0, server.requestCount)
            assertFalse("an unsent attempt must not block the next post", journalValues.containsKey("user:5"))
            assertEquals("another account's record is untouched", "FOUND:other-account", journalValues["user:9"])
            writer.restoreCreationStatus(application)
            assertEquals(PostWriteUiState.Idle, writer.uiState)
        } finally {
            writer.viewModelScope.cancel()
        }
    }

    @Test fun cancellationAfterThePostWasSentKeepsTheUnconfirmedRecord() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        vm.save(application)
        request()
        vm.reset()
        dispatch()
        assertTrue(journalValues.containsKey("user:5"))
        vm.restoreCreationStatus(application)
        assertEquals(PostWriteUiState.Unconfirmed, vm.uiState)
        assertEquals(1, server.requestCount)
    }

    @Test fun cancellationWhileTheWriterWaitsToResumeAfterAsyncJournalWriteClearsThatAttempt() {
        val queued = ArrayDeque<Runnable>()
        // Like Dispatchers.IO: the writer suspends first, the IO work runs later.
        val io = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { queued.addLast(block) }
        }
        fun runIo() { while (queued.isNotEmpty()) queued.removeFirst().run() }
        val writer = PostWriteViewModel(retrofit.create(FoundItemApiService::class.java),
            retrofit.create(LostItemApiService::class.java), io)
        writer.title = "검은 지갑"; writer.category = "지갑"; writer.content = "사진 속 물건"
        writer.changePostType(PostType.LOST)
        writer.setLostOutdoorPins(listOf(GeoPoint(127.076, 37.5418)))
        try {
            writer.save(application)
            dispatch()
            runIo()
            assertTrue("the journal was written", journalValues.containsKey("user:5"))
            writer.reset()
            dispatch()
            runIo()
            dispatch()
            assertEquals(0, server.requestCount)
            assertFalse(journalValues.containsKey("user:5"))
            writer.restoreCreationStatus(application)
            assertEquals(PostWriteUiState.Idle, writer.uiState)
        } finally {
            writer.viewModelScope.cancel()
        }
    }
}
