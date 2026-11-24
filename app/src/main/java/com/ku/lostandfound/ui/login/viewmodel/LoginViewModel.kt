package com.ku.lostandfound.ui.login.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ku.lostandfound.network.*
import com.ku.lostandfound.notification.PushDeviceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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

class LoginViewModel(
    private val authApi: AuthApiService = RetrofitClient.authApi,
    private val unregisterPush: suspend (String, Long) -> Unit = { authorization, generation ->
        PushDeviceManager.unregisterBeforeLogout(authorization, generation)
    },
) : ViewModel() {
    // Regular classes deliberately have no generated toString containing credentials.
    private class SuspendedAccount(val generation: Long, val subject: String?, val token: String)
    private class WithdrawalOperation(val attempt: Long, val generation: Long,
        val suspended: SuspendedAccount?, val authorization: String) {
        val request = WithdrawalRequest(generation,
            if (suspended == null) WithdrawalTokenKind.SESSION else WithdrawalTokenKind.DEDICATED)
    }
    // Dedicated credentials exist only in this ViewModel, never in TokenManager/preferences.
    private var suspendedAccount by mutableStateOf<SuspendedAccount?>(null)
    private var activeWithdrawal: WithdrawalOperation? = null
    private var withdrawalJob: Job? = null
    // Losing/cancelling a dedicated credential must never fall back to a preserved ordinary session.
    private var requiresDedicatedWithdrawal = false
    var isWithdrawing by mutableStateOf(false)
        private set
    val hasSuspendedWithdrawal: Boolean get() = suspendedAccount?.let {
        TokenManager.isCurrentSession(it.generation)
    } == true
    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Idle)
        private set
    private var loginAttemptId = 0L

    private fun abandonWithdrawal() {
        val oldJob = withdrawalJob
        activeWithdrawal = null
        withdrawalJob = null
        suspendedAccount = null
        isWithdrawing = false
        oldJob?.cancel()
    }

    fun login(emailPrefix: String, password: String) {
        val sessionGeneration = TokenManager.sessionGeneration()
        val attemptId = ++loginAttemptId
        abandonWithdrawal()
        fun valid() = attemptId == loginAttemptId && TokenManager.isCurrentSession(sessionGeneration)
        viewModelScope.launch {
            if (!valid()) return@launch
            uiState = LoginUiState.Loading
            try {
                val response = AuthRequestOrder.shared.login {
                    if (!valid()) return@login null
                    authApi.login(LoginRequest(toKonkukEmail(emailPrefix), password))
                }
                if (response == null || !valid()) return@launch
                val errorBody = if (response.isSuccessful) null else response.errorBody()?.string()
                val body = if (response.isSuccessful) response.body() else if (response.code() == 403) {
                    runCatching { Gson().fromJson(errorBody, LoginResponse::class.java) }.getOrNull()
                } else null
                val data = body?.data
                synchronized(TokenManager) {
                    if (!valid()) return@launch
                    if (data?.suspended == true) {
                        requiresDedicatedWithdrawal = true
                        val dedicated = data.withdrawalToken?.takeIf { it.isNotBlank() }
                        if (dedicated == null) {
                            uiState = LoginUiState.Error("탈퇴 인증정보를 받지 못했습니다. 다시 로그인해주세요.")
                        } else {
                            val subject = TokenManager.tokenSubject(dedicated)
                            if (subject != null && subject != toKonkukEmail(emailPrefix)) {
                                uiState = LoginUiState.Error("탈퇴 인증정보의 계정이 일치하지 않습니다. 다시 로그인해주세요.")
                                return@launch
                            }
                            val generation = TokenManager.clearIfOwnedBy(sessionGeneration, subject)
                                ?: sessionGeneration
                            suspendedAccount = SuspendedAccount(generation, subject, dedicated)
                            uiState = LoginUiState.Suspended
                        }
                        return@launch
                    }
                    if (response.isSuccessful && body?.success == true &&
                        !data?.accessToken.isNullOrBlank() && !data?.refreshToken.isNullOrBlank()) {
                        if (!TokenManager.saveNewSessionTokens(sessionGeneration, data!!.accessToken!!, data.refreshToken!!)) {
                            uiState = LoginUiState.Idle
                            return@launch
                        }
                        PushDeviceManager.onLoginSucceeded()
                        requiresDedicatedWithdrawal = false
                        uiState = LoginUiState.Success
                    } else {
                        if (!response.isSuccessful) NetworkLog.httpError("login", response.code())
                        uiState = LoginUiState.Error(if (response.isSuccessful) body?.message ?: "로그인에 실패했습니다."
                            else httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (!valid()) return@launch
                NetworkLog.exception("login", e)
                uiState = LoginUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun retrySavedSession() {
        if (uiState == LoginUiState.Loading || !TokenManager.hasTokens()) return
        val session = TokenManager.sessionGeneration()
        val attempt = ++loginAttemptId
        abandonWithdrawal()
        uiState = LoginUiState.Loading
        viewModelScope.launch {
            val result = RetrofitClient.refreshSession()
            if (attempt != loginAttemptId || !TokenManager.isCurrentSession(session)) return@launch
            uiState = if (result == RefreshSessionResult.Success) {
                requiresDedicatedWithdrawal = false
                PushDeviceManager.onLoginSucceeded()
                LoginUiState.Success
            } else LoginUiState.Idle
        }
    }

    fun logout(onFinished: () -> Unit) {
        val expectedGeneration = TokenManager.sessionGeneration()
        resetUiState()
        viewModelScope.launch {
            var authorization: String? = null
            var closedGeneration = 0L
            try {
                AuthRequestOrder.shared.logout(
                    invalidate = {
                        synchronized(TokenManager) {
                            if (TokenManager.isCurrentSession(expectedGeneration)) {
                                authorization = TokenManager.accessToken?.let { "Bearer $it" }
                                closedGeneration = TokenManager.clear()
                                resetUiState()
                                onFinished()
                            }
                        }
                    },
                    cleanup = {
                        authorization?.let { header ->
                            withTimeoutOrNull(4000L.milliseconds) {
                                unregisterPush(header, closedGeneration)
                            }
                            authApi.logout(header)
                        }
                    },
                )
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { NetworkLog.exception("logout", e) }
        }
    }

    private fun owns(operation: WithdrawalOperation) = activeWithdrawal === operation &&
        loginAttemptId == operation.attempt && TokenManager.isCurrentSession(operation.generation)

    fun withdraw(onFinished: () -> Unit) {
        if (isWithdrawing) return
        val operation = synchronized(TokenManager) {
            val session = TokenManager.credentials()
            val dedicated = suspendedAccount
            if (requiresDedicatedWithdrawal && dedicated == null) {
                uiState = LoginUiState.Error("탈퇴 인증정보가 없습니다. 다시 로그인해주세요.")
                return
            }
            if (dedicated != null && dedicated.generation != session.generation) {
                abandonWithdrawal()
                uiState = LoginUiState.Error("로그인 상태가 변경되었습니다. 다시 로그인해주세요.")
                return
            }
            val token = dedicated?.token ?: session.access?.takeIf { it.isNotBlank() } ?: run {
                uiState = LoginUiState.Error("로그인이 필요합니다.")
                return
            }
            WithdrawalOperation(++loginAttemptId, session.generation, dedicated, "Bearer $token")
        }
        activeWithdrawal = operation
        isWithdrawing = true
        uiState = LoginUiState.Loading
        withdrawalJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (!owns(operation)) return@launch
                // PR #12: the server withdrawal transaction owns push-device cleanup.
                // Keep logout's separate bounded cleanup; do not pre-delete devices here.
                val authorization = if (operation.suspended != null) operation.authorization else {
                    val latest = TokenManager.credentials()
                    if (latest.generation != operation.generation || latest.access.isNullOrBlank()) return@launch
                    "Bearer ${latest.access}"
                }
                val response = authApi.withdraw(authorization, operation.request)
                synchronized(TokenManager) {
                    if (!owns(operation)) return@launch
                    if (response.isSuccessful && response.body()?.success == true) {
                        if (operation.suspended == null) TokenManager.clear()
                        else TokenManager.clearIfOwnedBy(operation.generation, operation.suspended.subject)
                        suspendedAccount = null
                        activeWithdrawal = null
                        withdrawalJob = null
                        isWithdrawing = false
                        uiState = if (operation.suspended != null) LoginUiState.WithdrawalComplete else LoginUiState.Idle
                        onFinished()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        if (!response.isSuccessful) NetworkLog.httpError("withdraw", response.code())
                        uiState = LoginUiState.Error(if (response.isSuccessful)
                            response.body()?.message ?: "회원 탈퇴에 실패했습니다."
                            else httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (!owns(operation)) return@launch
                NetworkLog.exception("withdraw", e)
                uiState = LoginUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            } finally {
                // An earlier request cannot erase a newer dedicated credential or progress.
                if (activeWithdrawal === operation) {
                    activeWithdrawal = null
                    withdrawalJob = null
                    isWithdrawing = false
                    if (uiState == LoginUiState.Loading) {
                        uiState = if (operation.suspended != null && hasSuspendedWithdrawal)
                            LoginUiState.Suspended else LoginUiState.Idle
                    }
                }
            }
        }
        withdrawalJob?.start()
    }

    fun resetUiState() {
        loginAttemptId++
        abandonWithdrawal()
        uiState = LoginUiState.Idle
    }

    override fun onCleared() {
        abandonWithdrawal()
        super.onCleared()
    }

    private fun parseErrorMessage(errorBody: String?): String {
        if (errorBody == null) return "오류가 발생했습니다."
        return try {
            Gson().fromJson(errorBody, AuthErrorResponse::class.java).message ?: "요청을 처리할 수 없습니다."
        } catch (_: Exception) { "오류가 발생했습니다." }
    }

    private fun toKonkukEmail(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.endsWith("@konkuk.ac.kr", ignoreCase = true)) trimmed else "$trimmed@konkuk.ac.kr"
    }
}
