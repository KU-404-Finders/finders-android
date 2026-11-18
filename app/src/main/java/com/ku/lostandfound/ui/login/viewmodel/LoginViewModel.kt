package com.ku.lostandfound.ui.login.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ku.lostandfound.network.AuthErrorResponse
import com.ku.lostandfound.network.LoginResponse
import com.ku.lostandfound.network.LoginRequest
import com.ku.lostandfound.network.NetworkLog
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
    object Suspended : LoginUiState()
    object WithdrawalComplete : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel : ViewModel() {
    // 탈퇴 전용 토큰은 메모리에서만 보관하며 일반 로그인 저장소에 넣지 않는다.
    private var withdrawalToken: String? = null
    var isWithdrawing by mutableStateOf(false)
        private set
    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Idle)
        private set
    private var loginAttemptId = 0L

    fun login(emailPrefix: String, password: String) {
        val attemptId = ++loginAttemptId

        viewModelScope.launch {
            val sessionGeneration = TokenManager.sessionGeneration()
            withdrawalToken = null
            uiState = LoginUiState.Loading

            try {
                val response = RetrofitClient.authApi.login(
                    LoginRequest(
                        email = toKonkukEmail(emailPrefix),
                        password = password,
                    )
                )
                if (attemptId != loginAttemptId || !TokenManager.isCurrentSession(sessionGeneration)) return@launch
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data?.accessToken != null && body.data.refreshToken != null) {
                        if (!TokenManager.saveNewSessionTokens(
                                sessionGeneration,
                                body.data.accessToken,
                                body.data.refreshToken,
                            )) {
                            if (attemptId == loginAttemptId) {
                                uiState = LoginUiState.Idle
                            }
                            return@launch
                        }

                        // 로그인된 사용자와 Firebase 기기 연결
                        PushDeviceManager.onLoginSucceeded()

                        uiState = LoginUiState.Success
                    } else {
                        uiState = LoginUiState.Error(body?.message ?: "로그인에 실패했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val suspended = if (response.code() == 403) {
                        runCatching { Gson().fromJson(errorBody, LoginResponse::class.java).data }.getOrNull()
                    } else null
                    if (suspended?.suspended == true && !suspended.withdrawalToken.isNullOrBlank()) {
                        TokenManager.clear()
                        withdrawalToken = suspended.withdrawalToken
                        uiState = LoginUiState.Suspended
                        return@launch
                    }
                    // 정지 응답의 탈퇴 토큰을 오류 로그에 남기지 않는다.
                    NetworkLog.httpError("login", response.code(), null)
                    uiState = LoginUiState.Error(
                        httpErrorMessage(
                            response.code(),
                            parseErrorMessage(errorBody)
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attemptId != loginAttemptId || !TokenManager.isCurrentSession(sessionGeneration)) return@launch
                NetworkLog.exception("login", e)
                uiState = LoginUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun logout(onFinished: () -> Unit) {
        viewModelScope.launch {
            // Invalidate before any suspend call so an in-flight refresh cannot restore this
            // session while server-side device cleanup is still running.
            val authorization = TokenManager.accessToken?.let { "Bearer $it" }
            TokenManager.clear()
            uiState = LoginUiState.Idle
            onFinished()

            try {
                // The captured header permits best-effort server cleanup after the local
                // session has been invalidated.
                if (authorization != null) {
                    withTimeoutOrNull(4000L.milliseconds) {
                        PushDeviceManager.unregisterBeforeLogout(authorization)
                    }
                    RetrofitClient.authApi.logout(authorization)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                NetworkLog.exception("logout", e)
            }
        }
    }

    fun withdraw(onFinished: () -> Unit) {
        if (isWithdrawing) return
        isWithdrawing = true
        val sessionGeneration = TokenManager.sessionGeneration()
        val suspendedWithdrawal = withdrawalToken != null
        uiState = LoginUiState.Loading
        viewModelScope.launch {
            try {
                // 기기 해제는 서버의 탈퇴 트랜잭션에서 처리한다.
                val authorization = withdrawalToken?.let { "Bearer $it" } ?: bearerTokenOrThrow()
                val response = RetrofitClient.authApi.withdraw(authorization)
                if (!TokenManager.isCurrentSession(sessionGeneration)) return@launch
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        TokenManager.clear()
                        withdrawalToken = null
                        uiState = if (suspendedWithdrawal) LoginUiState.WithdrawalComplete else LoginUiState.Idle
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                NetworkLog.exception("withdraw", e)
                uiState = LoginUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            } finally {
                withdrawalToken = null
                isWithdrawing = false
            }
        }
    }

    fun resetUiState() {
        withdrawalToken = null
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
