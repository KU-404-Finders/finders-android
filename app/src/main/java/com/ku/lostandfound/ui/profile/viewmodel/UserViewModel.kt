package com.ku.lostandfound.ui.profile.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ku.lostandfound.network.AuthErrorResponse
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

class UserViewModel : ViewModel() {
    var uiState by mutableStateOf<UserUiState>(UserUiState.Idle)
        private set

    var me by mutableStateOf<UserMeData?>(null)
        private set

    fun loadMe() {
        viewModelScope.launch {
            uiState = UserUiState.Loading
            try {
                val response = RetrofitClient.userApi.getMe()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        me = body.data
                        uiState = UserUiState.Idle
                    } else {
                        uiState = UserUiState.Error(body?.message ?: "사용자 정보를 불러오지 못했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("loadMe", response.code(), errorBody)
                    uiState = UserUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("loadMe", e)
                uiState = UserUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
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
}
