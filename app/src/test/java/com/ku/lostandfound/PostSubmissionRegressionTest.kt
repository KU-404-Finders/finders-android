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
        assertEquals(PostWriteUiState.Idle, vm.uiState)
    }

    @Test fun retryAfterZeroAndAuthenticationFailureDoNotReplayPost() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        vm.save(application)
        request()
        awaitState { it == PostWriteUiState.Unconfirmed }
        assertEquals(1, server.requestCount)

        vm.acknowledgeUnconfirmed()
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
}
