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
import com.ku.lostandfound.network.NetworkLog
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.network.httpErrorMessage
import kotlinx.coroutines.launch

sealed class LostItemListUiState {
    object Idle : LostItemListUiState()
    object Loading : LostItemListUiState()
    data class Error(val message: String) : LostItemListUiState()
}

class LostItemViewModel : ViewModel() {
    var uiState by mutableStateOf<LostItemListUiState>(LostItemListUiState.Idle)
        private set

    var lostItems by mutableStateOf<List<LostItemSummaryData>>(emptyList())
        private set

    var detailPost by mutableStateOf<BoardPost?>(null)
        private set

    var matchesStatus by mutableStateOf<String?>(null)
        private set

    var matches by mutableStateOf<List<LostItemMatchData>>(emptyList())
        private set

    fun loadLostItems() {
        viewModelScope.launch {
            uiState = LostItemListUiState.Loading
            try {
                val response = RetrofitClient.lostItemApi.getLostItems()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        lostItems = body.data.orEmpty().filter { it.itemStatus == "SEARCHING" }
                        uiState = LostItemListUiState.Idle
                    } else {
                        uiState = LostItemListUiState.Error(body?.message ?: "분실물 목록을 불러오지 못했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("loadLostItems", response.code(), errorBody)
                    uiState = LostItemListUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("loadLostItems", e)
                uiState = LostItemListUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun asBoardPosts(): List<BoardPost> {
        return lostItems.map { item ->
            BoardPost(
                id = item.id.toString(),
                type = PostType.LOST,
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
            uiState = LostItemListUiState.Loading
            try {
                val response = RetrofitClient.lostItemApi.getLostItemDetail(id)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        detailPost = body.data.toBoardPost()
                        uiState = LostItemListUiState.Idle
                    } else {
                        uiState = LostItemListUiState.Error(body?.message ?: "분실물 상세 정보를 불러오지 못했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("loadLostItemDetail", response.code(), errorBody)
                    uiState = LostItemListUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("loadLostItemDetail", e)
                uiState = LostItemListUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun markReturned(id: Long, onSuccess: (BoardPost) -> Unit) {
        viewModelScope.launch {
            uiState = LostItemListUiState.Loading
            try {
                val response = RetrofitClient.lostItemApi.markLostItemReturned(bearerTokenOrThrow(), id)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        val updatedPost = body.data.toBoardPost()
                        detailPost = updatedPost
                        uiState = LostItemListUiState.Idle
                        onSuccess(updatedPost)
                    } else {
                        uiState = LostItemListUiState.Error(body?.message ?: "상태 변경에 실패했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("markReturned", response.code(), errorBody)
                    uiState = LostItemListUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("markReturned", e)
                uiState = LostItemListUiState.Error(e.message ?: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun loadMatches(id: Long) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.lostItemApi.getLostItemMatches(id)
                val body = response.body()
                if (body?.success == true) {
                    matchesStatus = "COMPLETED"
                    matches = body.data.orEmpty()
                } else if (!response.isSuccessful) {
                    NetworkLog.httpError("loadLostItemMatches", response.code(), response.errorBody()?.string())
                    matchesStatus = null
                    matches = emptyList()
                }
            } catch (e: Exception) {
                NetworkLog.exception("loadLostItemMatches", e)
                matchesStatus = "FAILED"
                matches = emptyList()
            }
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
}
