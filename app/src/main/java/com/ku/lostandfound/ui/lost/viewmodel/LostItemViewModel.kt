package com.ku.lostandfound.ui.lost.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.GeoPoint
import com.ku.lostandfound.data.IndoorPlace
import com.ku.lostandfound.data.LostLocationSelection
import com.ku.lostandfound.data.OutdoorPin
import com.ku.lostandfound.data.PostStatus
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.network.AuthErrorResponse
import com.ku.lostandfound.network.LostItemData
import com.ku.lostandfound.network.LostItemMatchData
import com.ku.lostandfound.network.LostItemSummaryData
import com.ku.lostandfound.network.MatchStatus
import com.ku.lostandfound.network.NetworkLog
import com.ku.lostandfound.network.LostItemApiService
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.network.httpErrorMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class LostItemListUiState {
    object Idle : LostItemListUiState()
    object Loading : LostItemListUiState()
    data class Error(val message: String) : LostItemListUiState()
}

class LostItemViewModel(
    private val lostItemApi: LostItemApiService = RetrofitClient.lostItemApi,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {
    var uiState by mutableStateOf<LostItemListUiState>(LostItemListUiState.Idle)
        private set

    var lostItems by mutableStateOf<List<LostItemSummaryData>>(emptyList())
        private set

    var listVersion by mutableStateOf(0)
        private set

    var detailPost by mutableStateOf<BoardPost?>(null)
        private set

    var matchesStatus by mutableStateOf<String?>(null)
        private set

    var matches by mutableStateOf<List<LostItemMatchData>>(emptyList())
        private set

    var listState by mutableStateOf<LostItemListUiState>(LostItemListUiState.Idle)
        private set
    private var listRequestId = 0L
    private var lastLoadedAt: Long? = null
    private val resolutionVersions = mutableMapOf<Long, Int>()
    private var resolving by mutableStateOf<Set<Pair<Long, Long>>>(emptySet())

    fun isResolving(id: Long): Boolean = (TokenManager.sessionGeneration() to id) in resolving
    private var listSession = -1L

    fun ensureLoaded() {
        if (!TokenManager.isCurrentSession(listSession)) {
            loadLostItems()
            return
        }
        if (listState != LostItemListUiState.Loading &&
            (listState is LostItemListUiState.Error || lastLoadedAt?.let { nowMillis() - it in 0 until 30_000 } != true)
        ) loadLostItems()
    }

    fun refreshLostItems() {
        if (!TokenManager.isCurrentSession(listSession) || listState != LostItemListUiState.Loading) loadLostItems()
    }

    private fun invalidatePendingList() {
        listRequestId++
        lastLoadedAt = null
        listState = LostItemListUiState.Idle
    }

    fun clearSession() {
        listRequestId++
        listVersion = 0
        lastLoadedAt = null
        listSession = -1L
        lostItems = emptyList()
        detailPost = null
        matches = emptyList()
        matchesStatus = null
        listState = LostItemListUiState.Idle
        uiState = LostItemListUiState.Idle
    }

    fun loadLostItems() {
        val session = TokenManager.sessionGeneration()
        if (listSession != -1L && listSession != session) {
            clearSession()
        }
        listSession = session
        val request = ++listRequestId
        // Set before launching so multiple gestures/entries in the same frame coalesce.
        listState = LostItemListUiState.Loading
        viewModelScope.launch {
            try {
                val response = lostItemApi.getLostItems()
                if (request != listRequestId || !TokenManager.isCurrentSession(session)) return@launch
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        lostItems = body.data.orEmpty().filter { it.itemStatus == "SEARCHING" }
                        listVersion++
                        lastLoadedAt = nowMillis()
                        listState = LostItemListUiState.Idle
                    } else {
                        listState = LostItemListUiState.Error(body?.message ?: "분실물 목록을 불러오지 못했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("loadLostItems", response.code())
                    listState = LostItemListUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request != listRequestId || !TokenManager.isCurrentSession(session)) return@launch
                NetworkLog.exception("loadLostItems", e)
                listState = LostItemListUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun asBoardPosts(): List<BoardPost> {
        return lostItems.map { item ->
            BoardPost(
                id = item.id.toString(),
                type = PostType.LOST,
                status = if (item.itemStatus == "RETURNED") PostStatus.RESOLVED else PostStatus.OPEN,
                title = item.title,
                category = item.kind,
                content = "분실물 상세 정보를 불러오려면 상세 조회 API가 필요합니다.",
                imageUri = item.imageUrl,
                createdAtText = item.createdAt.take(10),
            )
        }
    }

    fun loadLostItemDetail(id: Long) {
        viewModelScope.launch {
            loadLostItemDetailNow(id)
        }
    }

    suspend fun loadLostItemDetailNow(id: Long): BoardPost? {
            val session = TokenManager.sessionGeneration()
            val version = resolutionVersions[id]
            uiState = LostItemListUiState.Loading
            try {
                val response = lostItemApi.getLostItemDetail(id)
                if (!TokenManager.isCurrentSession(session) || resolutionVersions[id] != version) return null
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        detailPost = body.data.toBoardPost()
                        uiState = LostItemListUiState.Idle
                        return detailPost
                    } else {
                        uiState = LostItemListUiState.Error(body?.message ?: "분실물 상세 정보를 불러오지 못했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("loadLostItemDetail", response.code())
                    uiState = LostItemListUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!TokenManager.isCurrentSession(session) || resolutionVersions[id] != version) return null
                NetworkLog.exception("loadLostItemDetail", e)
                uiState = LostItemListUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
            return null
    }

    fun markReturned(id: Long, onError: (String) -> Unit = {}, onSuccess: (BoardPost) -> Unit) {
        val session = TokenManager.sessionGeneration()
        val key = session to id
        if (id <= 0 || key in resolving) return
        resolving = resolving + key
        viewModelScope.launch {
            try {
                if (!TokenManager.isCurrentSession(session)) return@launch
                val requestSession = com.ku.lostandfound.network.RequestSession(session)
                val authorization = bearerTokenOrThrow()
                val updatedPost = com.ku.lostandfound.network.confirmPostResolution(
                    PostType.LOST, id, requestSession,
                    send = { lostItemApi.markLostItemReturned(authorization, id, requestSession) },
                    verify = { lostItemApi.getLostItemDetail(id, requestSession) },
                    decode = { body -> body.takeIf { it.success }?.data?.toBoardPost() },
                    httpError = { code, body -> httpErrorMessage(code, parseErrorMessage(body)) },
                )
                if (!TokenManager.isCurrentSession(session)) return@launch
                resolutionVersions[id] = (resolutionVersions[id] ?: 0) + 1
                detailPost = updatedPost
                invalidatePendingList()
                lostItems = lostItems.filterNot { it.id == id }
                listVersion++
                uiState = LostItemListUiState.Idle
                onSuccess(updatedPost)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!TokenManager.isCurrentSession(session)) return@launch
                NetworkLog.exception("markLostItemReturned", e)
                val message = e.message ?: "해결 완료 처리 중 오류가 발생했습니다."
                uiState = LostItemListUiState.Error(message)
                onError(message)
            } finally {
                resolving = resolving - key
            }
        }
    }

    fun deleteLostItem(id: Long, onSuccess: () -> Unit) {
        val session = TokenManager.sessionGeneration()
        viewModelScope.launch {
            uiState = LostItemListUiState.Loading
            try {
                val response = lostItemApi.deleteLostItem(bearerTokenOrThrow(), id)
                if (!TokenManager.isCurrentSession(session)) return@launch
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body == null || body.success) {
                        invalidatePendingList()
                        lostItems = lostItems.filterNot { it.id == id }
                        if (detailPost?.id == id.toString()) {
                            detailPost = null
                        }
                        uiState = LostItemListUiState.Idle
                        onSuccess()
                    } else {
                        uiState = LostItemListUiState.Error(body?.message ?: "분실물 삭제에 실패했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("deleteLostItem", response.code())
                    uiState = LostItemListUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!TokenManager.isCurrentSession(session)) return@launch
                NetworkLog.exception("deleteLostItem", e)
                uiState = LostItemListUiState.Error(e.message ?: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun loadMatches(id: Long) {
        viewModelScope.launch {
            loadMatchesNow(id)
        }
    }

    suspend fun loadMatchesNow(id: Long) {
            val session = TokenManager.sessionGeneration()
            matchesStatus = MatchStatus.CALCULATING.name
            matches = emptyList()
            try {
                repeat(MAX_MATCH_POLL_COUNT) { attempt ->
                    if (!TokenManager.isCurrentSession(session)) return
                    val response = lostItemApi.getLostItemMatches(id)
                    if (!TokenManager.isCurrentSession(session)) return
                    val body = response.body()
                    val result = body?.data
                    when {
                        body?.success == true && result?.status == MatchStatus.COMPLETED -> {
                            matchesStatus = MatchStatus.COMPLETED.name
                            matches = result.matches
                            return
                        }
                        body?.success == true && result?.status == MatchStatus.CALCULATING -> {
                            if (attempt < MAX_MATCH_POLL_COUNT - 1) {
                                delay(MATCH_POLL_DELAY_MS)
                            }
                        }
                        body?.success == true && result?.status == MatchStatus.FAILED -> {
                            matchesStatus = MatchStatus.FAILED.name
                            matches = emptyList()
                            return
                        }
                        else -> {
                            NetworkLog.httpError("loadLostItemMatches", response.code())
                            matchesStatus = null
                            matches = emptyList()
                            return
                        }
                    }
                }
                matchesStatus = MatchStatus.FAILED.name
                matches = emptyList()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!TokenManager.isCurrentSession(session)) return
                NetworkLog.exception("loadLostItemMatches", e)
                matchesStatus = "FAILED"
                matches = emptyList()
            }
    }

    private fun parseErrorMessage(errorBody: String?): String {
        if (errorBody == null) return "오류가 발생했습니다."
        return try {
            Gson().fromJson(errorBody, AuthErrorResponse::class.java).message ?: "요청을 처리할 수 없습니다."
        } catch (e: Exception) {
            "오류가 발생했습니다."
        }
    }

    private fun bearerTokenOrThrow(): String {
        val token = TokenManager.accessToken ?: throw IllegalStateException("로그인이 필요합니다.")
        return "Bearer $token"
    }

    private fun LostItemData.toBoardPost(): BoardPost {
        return BoardPost(
            id = id.toString(),
            type = PostType.LOST,
            status = if (itemStatus == "RETURNED") PostStatus.RESOLVED else PostStatus.OPEN,
            authorUserId = userId,
            authorName = userName?.takeIf { it.isNotBlank() } ?: authorName?.takeIf { it.isNotBlank() } ?: "익명",
            title = title,
            category = kind,
            content = content,
            imageUri = imageUrl,
            createdAtText = createdAt.take(10),
            lostLocation = LostLocationSelection(
                indoorPlaces = indoorSpots
                    .sortedBy { it.sortOrder ?: Int.MAX_VALUE }
                    .map {
                        IndoorPlace(
                            buildingId = it.buildingName,
                            buildingName = it.buildingName,
                            floor = it.floor,
                        )
                    },
                outdoorPins = outdoorPoints
                    .sortedBy { it.sortOrder }
                    .map {
                        OutdoorPin(
                            order = it.sortOrder + 1,
                            point = GeoPoint(
                                longitude = it.longitude,
                                latitude = it.latitude,
                            ),
                        )
                    },
            ),
        )
    }

    private companion object {
        const val MAX_MATCH_POLL_COUNT = 30
        const val MATCH_POLL_DELAY_MS = 2_000L
    }
}
