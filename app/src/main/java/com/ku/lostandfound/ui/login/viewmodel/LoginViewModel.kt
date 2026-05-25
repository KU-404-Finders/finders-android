package com.ku.lostandfound.ui.login.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ku.lostandfound.network.AuthErrorResponse
import com.ku.lostandfound.network.LoginRequest
import com.ku.lostandfound.network.NetworkLog
import com.ku.lostandfound.network.RefreshTokenRequest
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.network.httpErrorMessage
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel : ViewModel() {
    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Idle)
        private set

    fun login(emailPrefix: String, password: String) {
        viewModelScope.launch {
            uiState = LoginUiState.Loading
            if (isTemporaryLogin(emailPrefix, password)) {
                TokenManager.accessToken = TEMP_ACCESS_TOKEN
                TokenManager.refreshToken = TEMP_REFRESH_TOKEN
                uiState = LoginUiState.Success
                return@launch
            }

            try {
                val response = RetrofitClient.authApi.login(
                    LoginRequest(
                        email = toKonkukEmail(emailPrefix),
                        password = password,
                    )
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        TokenManager.accessToken = body.data.accessToken
                        TokenManager.refreshToken = body.data.refreshToken
                        uiState = LoginUiState.Success
                    } else {
                        uiState = LoginUiState.Error(body?.message ?: "로그인에 실패했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("login", response.code(), errorBody)
                    uiState = LoginUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("login", e)
                uiState = LoginUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun refreshToken() {
        val storedRefreshToken = TokenManager.refreshToken ?: return
        viewModelScope.launch {
            try {
                val response = RetrofitClient.authApi.refreshToken(
                    RefreshTokenRequest(refreshToken = storedRefreshToken)
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        TokenManager.accessToken = body.data.accessToken
                        TokenManager.refreshToken = body.data.refreshToken
                    } else {
                        TokenManager.clear()
                        uiState = LoginUiState.Error(body?.message ?: "토큰 재발급에 실패했습니다.")
                    }
                } else {
                    TokenManager.clear()
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("refreshToken", response.code(), errorBody)
                    uiState = LoginUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("refreshToken", e)
                uiState = LoginUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun logout(onFinished: () -> Unit) {
        viewModelScope.launch {
            try {
                RetrofitClient.authApi.logout(bearerTokenOrThrow())
            } catch (e: Exception) {
                NetworkLog.exception("logout", e)
            } finally {
                TokenManager.clear()
                uiState = LoginUiState.Idle
                onFinished()
            }
        }
    }

    fun withdraw(onFinished: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.authApi.withdraw(bearerTokenOrThrow())
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        TokenManager.clear()
                        uiState = LoginUiState.Idle
                        onFinished()
                    } else {
                        uiState = LoginUiState.Error(body?.message ?: "회원 탈퇴에 실패했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("withdraw", response.code(), errorBody)
                    uiState = LoginUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                NetworkLog.exception("withdraw", e)
                uiState = LoginUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun resetUiState() {
        uiState = LoginUiState.Idle
    }

    private fun parseErrorMessage(errorBody: String?): String {
        if (errorBody == null) return "오류가 발생했습니다."
        return try {
            Gson().fromJson(errorBody, AuthErrorResponse::class.java).message ?: "요청을 처리할 수 없습니다."
        } catch (e: Exception) {
            "오류가 발생했습니다."
        }
    }

    private fun toKonkukEmail(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.endsWith("@konkuk.ac.kr", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed@konkuk.ac.kr"
        }
    }

    private fun isTemporaryLogin(emailPrefix: String, password: String): Boolean {
        val normalizedEmail = emailPrefix.trim().substringBefore("@")
        return normalizedEmail == TEMP_EMAIL_PREFIX && password == TEMP_PASSWORD
    }

    private fun bearerTokenOrThrow(): String {
        val token = TokenManager.accessToken ?: throw IllegalStateException("로그인이 필요합니다.")
        return "Bearer $token"
    }

    private companion object {
        const val TEMP_EMAIL_PREFIX = "test"
        const val TEMP_PASSWORD = "test1234"
        const val TEMP_ACCESS_TOKEN = "temporary-access-token"
        const val TEMP_REFRESH_TOKEN = "temporary-refresh-token"
    }
}
