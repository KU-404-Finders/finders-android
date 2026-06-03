package com.ku.lostandfound.ui.profile.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ku.lostandfound.network.AuthErrorResponse
import com.ku.lostandfound.network.MyFoundItemData
import com.ku.lostandfound.network.MyLostItemData
import com.ku.lostandfound.network.NetworkLog
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.UserMeData
import com.ku.lostandfound.network.httpErrorMessage
import kotlinx.coroutines.launch

sealed class UserUiState {
    object Idle : UserUiState()
    object Loading : UserUiState()
    data class Error(val message: String) : UserUiState()
}

sealed class MyPostsUiState {
    object Idle : MyPostsUiState()
    object Loading : MyPostsUiState()
    data class Error(val message: String) : MyPostsUiState()
}

class UserViewModel : ViewModel() {
    var uiState by mutableStateOf<UserUiState>(UserUiState.Idle)
        private set

    var me by mutableStateOf<UserMeData?>(null)
        private set

    var myPostCountState by mutableStateOf<MyPostsUiState>(MyPostsUiState.Idle)
        private set

    var myLostCount by mutableStateOf<Int?>(null)
        private set

    var myFoundCount by mutableStateOf<Int?>(null)
        private set

    var myLostItemsState by mutableStateOf<MyPostsUiState>(MyPostsUiState.Idle)
        private set

    var myFoundItemsState by mutableStateOf<MyPostsUiState>(MyPostsUiState.Idle)
        private set

    var myLostItems by mutableStateOf<List<MyLostItemData>>(emptyList())
        private set

    var myFoundItems by mutableStateOf<List<MyFoundItemData>>(emptyList())
        private set

    fun loadMe() {
        viewModelScope.launch {
            loadMeNow()
        }
    }

    suspend fun loadMeNow(): UserMeData? {
        uiState = UserUiState.Loading
        return try {
            val response = RetrofitClient.userApi.getMe()
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {
                    me = body.data
                    uiState = UserUiState.Idle
                    body.data
                } else {
                    uiState = UserUiState.Error(body?.message ?: "사용자 정보를 불러오지 못했습니다.")
                    null
                }
            } else {
                val errorBody = response.errorBody()?.string()
                NetworkLog.httpError("loadMe", response.code(), errorBody)
                uiState = UserUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                null
            }
        } catch (e: Exception) {
            NetworkLog.exception("loadMe", e)
            uiState = UserUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            null
        }
    }

    fun loadMyPostCounts() {
        viewModelScope.launch {
            myPostCountState = MyPostsUiState.Loading
            try {
                val lostResponse = RetrofitClient.userApi.getMyLostItems()
                val foundResponse = RetrofitClient.userApi.getMyFoundItems()

                if (lostResponse.isSuccessful && foundResponse.isSuccessful) {
                    val lostBody = lostResponse.body()
                    val foundBody = foundResponse.body()
                    if (lostBody?.success == true && foundBody?.success == true) {
                        myLostCount = lostBody.data?.count ?: 0
                        myFoundCount = foundBody.data?.count ?: 0
                        myPostCountState = MyPostsUiState.Idle
                    } else {
                        myPostCountState = MyPostsUiState.Error(
                            lostBody?.message ?: foundBody?.message ?: "게시물 개수를 불러오지 못했습니다."
                        )
                    }
                } else {
                    val response = listOf(lostResponse, foundResponse).first { !it.isSuccessful }
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("loadMyPostCounts", response.code(), errorBody)
                    myPostCountState = MyPostsUiState.Error(authAwareMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("loadMyPostCounts", e)
                myPostCountState = MyPostsUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun loadMyLostItems() {
        viewModelScope.launch {
            myLostItemsState = MyPostsUiState.Loading
            try {
                val response = RetrofitClient.userApi.getMyLostItems()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        myLostItems = body.data?.items.orEmpty()
                        myLostCount = body.data?.count ?: myLostItems.size
                        myLostItemsState = MyPostsUiState.Idle
                    } else {
                        myLostItemsState = MyPostsUiState.Error(body?.message ?: "분실물 등록 내역을 불러오지 못했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("loadMyLostItems", response.code(), errorBody)
                    myLostItemsState = MyPostsUiState.Error(authAwareMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("loadMyLostItems", e)
                myLostItemsState = MyPostsUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun loadMyFoundItems() {
        viewModelScope.launch {
            myFoundItemsState = MyPostsUiState.Loading
            try {
                val response = RetrofitClient.userApi.getMyFoundItems()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        myFoundItems = body.data?.items.orEmpty()
                        myFoundCount = body.data?.count ?: myFoundItems.size
                        myFoundItemsState = MyPostsUiState.Idle
                    } else {
                        myFoundItemsState = MyPostsUiState.Error(body?.message ?: "습득물 등록 내역을 불러오지 못했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("loadMyFoundItems", response.code(), errorBody)
                    myFoundItemsState = MyPostsUiState.Error(authAwareMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("loadMyFoundItems", e)
                myFoundItemsState = MyPostsUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun clearSession() {
        me = null
        myLostCount = null
        myFoundCount = null
        myLostItems = emptyList()
        myFoundItems = emptyList()
        uiState = UserUiState.Idle
        myPostCountState = MyPostsUiState.Idle
        myLostItemsState = MyPostsUiState.Idle
        myFoundItemsState = MyPostsUiState.Idle
    }

    private fun parseErrorMessage(errorBody: String?): String {
        if (errorBody == null) return "오류가 발생했습니다."
        return try {
            Gson().fromJson(errorBody, AuthErrorResponse::class.java).message ?: "요청을 처리할 수 없습니다."
        } catch (e: Exception) {
            "오류가 발생했습니다."
        }
    }

    private fun authAwareMessage(code: Int, fallback: String): String {
        return if (code == 403) {
            "로그인이 만료되었습니다. 다시 로그인해주세요."
        } else {
            httpErrorMessage(code, fallback)
        }
    }
}
