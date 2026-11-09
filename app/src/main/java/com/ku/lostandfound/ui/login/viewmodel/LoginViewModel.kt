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
import com.ku.lostandfound.network.RefreshSessionResult
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.network.httpErrorMessage
import com.ku.lostandfound.notification.PushDeviceManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

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
                        TokenManager.saveTokens(body.data.accessToken, body.data.refreshToken)

                        // 로그인된 사용자와 Firebase 기기 연결
                        PushDeviceManager.onLoginSucceeded()

                        uiState = LoginUiState.Success
                    } else {
                        uiState = LoginUiState.Error(body?.message ?: "로그인에 실패했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("login", response.code(), errorBody)
                    uiState = LoginUiState.Error(
                        httpErrorMessage(
                            response.code(),
                            parseErrorMessage(errorBody)
                        )
                    )
                }
            } catch (e: Exception) {
                NetworkLog.exception("login", e)
                uiState = LoginUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun refreshToken() {
        viewModelScope.launch {
            when (RetrofitClient.refreshSession()) {
                RefreshSessionResult.Success -> Unit
                RefreshSessionResult.InvalidSession -> {
                    uiState = LoginUiState.Error("로그인이 만료되었습니다. 다시 로그인해주세요.")
                }
                RefreshSessionResult.RetryableFailure -> {
                    uiState = LoginUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
                }
            }
        }
    }

    fun logout(onFinished: () -> Unit) {
        viewModelScope.launch {
            try {
                // JWT를 삭제하기 전에 기기 등록 해제
                // 서버 오류로 로그아웃이 지나치게 지연되지 않도록 제한
                withTimeoutOrNull(4000L.milliseconds) {
                    PushDeviceManager.unregisterBeforeLogout()
                }

                RetrofitClient.authApi.logout(
                    bearerTokenOrThrow()
                )

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
                // 탈퇴 전에 현재 기기의 푸시 토큰도 서버에서 해제한다.
                // 실패해도 탈퇴 API는 진행하며, 서버는 탈퇴 처리 시 남은 토큰도 삭제해야 한다.
                withTimeoutOrNull(4000L.milliseconds) {
                    PushDeviceManager.unregisterBeforeLogout()
                }

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
                    uiState = LoginUiState.Error(
                        httpErrorMessage(
                            response.code(),
                            parseErrorMessage(errorBody)
                        )
                    )
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

    private fun bearerTokenOrThrow(): String {
        val token = TokenManager.accessToken ?: throw IllegalStateException("로그인이 필요합니다.")
        return "Bearer $token"
    }
}
