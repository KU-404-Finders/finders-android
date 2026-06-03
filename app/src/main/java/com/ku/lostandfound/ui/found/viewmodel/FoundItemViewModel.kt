package com.ku.lostandfound.ui.found.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.FoundLocationSelection
import com.ku.lostandfound.data.GeoPoint
import com.ku.lostandfound.data.IndoorPlace
import com.ku.lostandfound.data.OutdoorPin
import com.ku.lostandfound.data.PostStatus
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.network.FoundItemDetailData
import com.ku.lostandfound.network.FoundItemErrorResponse
import com.ku.lostandfound.network.FoundItemMatchData
import com.ku.lostandfound.network.FoundItemLocationType
import com.ku.lostandfound.network.FoundItemSummaryData
import com.ku.lostandfound.network.ItemStatus
import com.ku.lostandfound.network.MatchStatus
import com.ku.lostandfound.network.NetworkLog
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.network.httpErrorMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class FoundItemUiState {
    object Idle : FoundItemUiState()
    object Loading : FoundItemUiState()
    data class Error(val message: String) : FoundItemUiState()
}

sealed class FoundItemMatchUiState {
    object Idle : FoundItemMatchUiState()
    object Calculating : FoundItemMatchUiState()
    data class Completed(val matches: List<FoundItemMatchData>) : FoundItemMatchUiState()
    data class Failed(val message: String) : FoundItemMatchUiState()
    data class Error(val message: String) : FoundItemMatchUiState()
}

class FoundItemViewModel : ViewModel() {
    var uiState by mutableStateOf<FoundItemUiState>(FoundItemUiState.Idle)
        private set

    var selectedBuildingName by mutableStateOf<String?>(null)
        private set

    var foundItems by mutableStateOf<List<FoundItemSummaryData>>(emptyList())
        private set

    var listVersion by mutableStateOf(0)
        private set

    var detailPost by mutableStateOf<BoardPost?>(null)
        private set

    var matchUiState by mutableStateOf<FoundItemMatchUiState>(FoundItemMatchUiState.Idle)
        private set

    fun loadFoundItemsByBuilding(buildingName: String) {
        selectedBuildingName = buildingName
        viewModelScope.launch {
            uiState = FoundItemUiState.Loading
            try {
                val response = RetrofitClient.foundItemApi.getFoundItems(buildingName)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        foundItems = body.data.orEmpty().filter { it.itemStatus == ItemStatus.SEARCHING }
                        listVersion++
                        uiState = FoundItemUiState.Idle
                    } else {
                        uiState = FoundItemUiState.Error(body?.message ?: "습득물 목록을 불러오지 못했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("loadFoundItemsByBuilding", response.code(), errorBody)
                    uiState = FoundItemUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("loadFoundItemsByBuilding", e)
                uiState = FoundItemUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun loadFoundItems() {
        selectedBuildingName = null
        viewModelScope.launch {
            uiState = FoundItemUiState.Loading
            try {
                val response = RetrofitClient.foundItemApi.getFoundItems()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        foundItems = body.data.orEmpty().filter { it.itemStatus == ItemStatus.SEARCHING }
                        listVersion++
                        uiState = FoundItemUiState.Idle
                    } else {
                        uiState = FoundItemUiState.Error(body?.message ?: "습득물 목록을 불러오지 못했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("loadFoundItems", response.code(), errorBody)
                    uiState = FoundItemUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("loadFoundItems", e)
                uiState = FoundItemUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun clearBuildingFilter() {
        selectedBuildingName = null
        foundItems = emptyList()
        uiState = FoundItemUiState.Idle
    }

    fun loadFoundItemDetail(id: Long) {
        viewModelScope.launch {
            loadFoundItemDetailNow(id)
        }
    }

    suspend fun loadFoundItemDetailNow(id: Long): BoardPost? {
            uiState = FoundItemUiState.Loading
            try {
                val response = RetrofitClient.foundItemApi.getFoundItemDetail(id)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        detailPost = body.data.toBoardPost()
                        uiState = FoundItemUiState.Idle
                        return detailPost
                    } else {
                        uiState = FoundItemUiState.Error(body?.message ?: "습득물 상세 정보를 불러오지 못했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("loadFoundItemDetail", response.code(), errorBody)
                    uiState = FoundItemUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("loadFoundItemDetail", e)
                uiState = FoundItemUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
            return null
    }

    fun updateFoundItemStatus(id: Long, onSuccess: (BoardPost) -> Unit) {
        viewModelScope.launch {
            uiState = FoundItemUiState.Loading
            try {
                val authorization = bearerTokenOrThrow()
                val response = RetrofitClient.foundItemApi.updateFoundItemStatus(authorization, id)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        val updatedPost = body.data.toBoardPost()
                        detailPost = updatedPost
                        foundItems = foundItems.filterNot { it.id == id }
                        listVersion++
                        uiState = FoundItemUiState.Idle
                        onSuccess(updatedPost)
                    } else {
                        uiState = FoundItemUiState.Error(body?.message ?: "상태 변경에 실패했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("updateFoundItemStatus", response.code(), errorBody)
                    uiState = FoundItemUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("updateFoundItemStatus", e)
                uiState = FoundItemUiState.Error(e.message ?: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun deleteFoundItem(id: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            uiState = FoundItemUiState.Loading
            try {
                val response = RetrofitClient.foundItemApi.deleteFoundItem(bearerTokenOrThrow(), id)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body == null || body.success) {
                        foundItems = foundItems.filterNot { it.id == id }
                        if (detailPost?.id == id.toString()) {
                            detailPost = null
                        }
                        uiState = FoundItemUiState.Idle
                        onSuccess()
                    } else {
                        uiState = FoundItemUiState.Error(body?.message ?: "습득물 삭제에 실패했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("deleteFoundItem", response.code(), errorBody)
                    uiState = FoundItemUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("deleteFoundItem", e)
                uiState = FoundItemUiState.Error(e.message ?: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun loadFoundItemMatches(id: Long) {
        viewModelScope.launch {
            loadFoundItemMatchesNow(id)
        }
    }

    suspend fun loadFoundItemMatchesNow(id: Long) {
            matchUiState = FoundItemMatchUiState.Calculating
            try {
                repeat(MAX_MATCH_POLL_COUNT) { attempt ->
                    val response = RetrofitClient.foundItemApi.getFoundItemMatches(id)
                    val body = response.body()
                    val result = body?.data
                    when {
                        body?.success == true && result?.status == MatchStatus.COMPLETED -> {
                            matchUiState = FoundItemMatchUiState.Completed(result.matches)
                            return
                        }
                        body?.success == true && result?.status == MatchStatus.CALCULATING -> {
                            if (attempt < MAX_MATCH_POLL_COUNT - 1) {
                                delay(MATCH_POLL_DELAY_MS)
                            }
                        }
                        body?.success == true && result?.status == MatchStatus.FAILED -> {
                            matchUiState = FoundItemMatchUiState.Failed(body.message)
                            return
                        }
                        else -> {
                            val errorBody = response.errorBody()?.string()
                            NetworkLog.httpError("loadFoundItemMatches", response.code(), errorBody)
                            matchUiState = FoundItemMatchUiState.Error(
                                httpErrorMessage(response.code(), body?.message ?: parseErrorMessage(errorBody))
                            )
                            return
                        }
                    }
                }
                matchUiState = FoundItemMatchUiState.Failed("매칭 계산이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.")
            } catch (e: Exception) {
                NetworkLog.exception("loadFoundItemMatches", e)
                matchUiState = FoundItemMatchUiState.Failed("매칭 계산에 실패했습니다. 잠시 후 다시 시도해 주세요.")
            }
    }

    fun asBoardPosts(): List<BoardPost> {
        return foundItems.map { item ->
            BoardPost(
                id = item.id.toString(),
                type = PostType.FOUND,
                status = if (item.itemStatus == ItemStatus.RETURNED) PostStatus.RESOLVED else PostStatus.OPEN,
                title = item.title,
                category = item.kind,
                content = item.associatedBuildingNames.joinToString(", ").ifBlank { "습득물 상세 조회가 필요합니다." },
                imageUri = item.imageUrl,
                createdAtText = item.createdAt.take(10),
                foundLocation = item.toFoundLocationSelection(),
                associatedBuildingNames = item.associatedBuildingNames,
            )
        }
    }

    private fun FoundItemSummaryData.toFoundLocationSelection(): FoundLocationSelection {
        return if (locationType == FoundItemLocationType.INDOOR) {
            FoundLocationSelection(
                indoorPlace = associatedBuildingNames.firstOrNull()?.let {
                    IndoorPlace(buildingId = it, buildingName = it, floor = 1)
                }
            )
        } else {
            FoundLocationSelection()
        }
    }

    private fun FoundItemDetailData.toBoardPost(): BoardPost {
        return BoardPost(
            id = id.toString(),
            type = PostType.FOUND,
            status = if (itemStatus == ItemStatus.RETURNED) PostStatus.RESOLVED else PostStatus.OPEN,
            authorUserId = userId,
            authorName = authorName?.takeIf { it.isNotBlank() } ?: "익명",
            title = title,
            category = kind,
            content = content,
            imageUri = imageUrl,
            createdAtText = createdAt.take(10),
            foundLocation = toFoundLocationSelection(),
            associatedBuildingNames = associatedBuildingNames,
        )
    }

    private fun FoundItemDetailData.toFoundLocationSelection(): FoundLocationSelection {
        return when (locationType) {
            FoundItemLocationType.INDOOR -> FoundLocationSelection(
                indoorPlace = buildingName?.let {
                    IndoorPlace(
                        buildingId = it,
                        buildingName = it,
                        floor = floor ?: 1,
                    )
                }
            )
            FoundItemLocationType.OUTDOOR -> FoundLocationSelection(
                outdoorPin = if (latitude != null && longitude != null) {
                    OutdoorPin(
                        order = 1,
                        point = GeoPoint(longitude = longitude, latitude = latitude),
                    )
                } else {
                    null
                }
            )
        }
    }

    private fun bearerTokenOrThrow(): String {
        val token = TokenManager.accessToken ?: throw IllegalStateException("로그인이 필요합니다.")
        return "Bearer $token"
    }

    private fun parseErrorMessage(errorBody: String?): String {
        if (errorBody == null) return "오류가 발생했습니다."
        return try {
            val errorResponse = Gson().fromJson(errorBody, FoundItemErrorResponse::class.java)
            errorResponse.errors?.values?.firstOrNull() ?: errorResponse.message ?: "요청을 처리할 수 없습니다."
        } catch (e: Exception) {
            "오류가 발생했습니다."
        }
    }

    private companion object {
        const val MAX_MATCH_POLL_COUNT = 30
        const val MATCH_POLL_DELAY_MS = 2_000L
    }
}
