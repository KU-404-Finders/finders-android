package com.ku.lostandfound.ui.signup.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ku.lostandfound.network.NetworkLog
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.SignupErrorResponse
import com.ku.lostandfound.network.SignupRequest
import com.ku.lostandfound.network.httpErrorMessage
import kotlinx.coroutines.launch

sealed class SignupUiState {
    object Idle : SignupUiState()
    object Loading : SignupUiState()
    object Success : SignupUiState()
    data class Error(val message: String) : SignupUiState()
}

class SignupViewmodel(
    private val signupApi: com.ku.lostandfound.network.SignupApiService = RetrofitClient.signupApi,
) : ViewModel() {
    private var generation = 0L
    var termsAccepted by mutableStateOf(false)
    var privacyAccepted by mutableStateOf(false)
    val hasRequiredConsent get() = termsAccepted && privacyAccepted
    var name by mutableStateOf("")
    var password by mutableStateOf("")
    var emailPrefix by mutableStateOf("")
    var verificationCode by mutableStateOf("")

    var uiState by mutableStateOf<SignupUiState>(SignupUiState.Idle)
        private set

    val email: String
        get() = if (emailPrefix.isBlank()) "" else toKonkukEmail(emailPrefix)

    val isNameValid: Boolean
        get() = name.matches(Regex("^[가-힣]{2,5}$"))

    val isPasswordValid: Boolean
        get() = password.length >= 6 && password.any { it.isLetter() } && password.any { it.isDigit() }

    val isEmailValid: Boolean
        get() = emailPrefix.isNotBlank()

    val isVerificationCodeValid: Boolean
        get() = verificationCode.length == 6 && verificationCode.all { it.isDigit() }

    fun sendVerificationCode(onSuccess: () -> Unit) {
        if (!requireConsent()) return
        val requestGeneration = generation
        viewModelScope.launch {
            if (requestGeneration != generation || !requireConsent()) return@launch
            uiState = SignupUiState.Loading
            try {
                val response = signupApi.sendVerificationCode(email)
                if (!acceptResponse(requestGeneration)) return@launch
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        uiState = SignupUiState.Idle
                        onSuccess()
                    } else {
                        uiState = SignupUiState.Error(body?.message ?: "인증번호 발송에 실패했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("sendVerificationCode", response.code())
                    uiState = SignupUiState.Error(httpErrorMessage(response.code(), parseErrorMessage(errorBody)))
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (!acceptResponse(requestGeneration)) return@launch
                NetworkLog.exception("sendVerificationCode", e)
                uiState = SignupUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun register() {
        if (!requireConsent()) return
        val requestGeneration = generation
        viewModelScope.launch {
            if (requestGeneration != generation || !requireConsent()) return@launch
            uiState = SignupUiState.Loading
            try {
                val response = signupApi.register(
                    SignupRequest(
                        email = email,
                        authCode = verificationCode,
                        password = password,
                        name = name,
                    )
                )
                if (!acceptResponse(requestGeneration)) return@launch
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        uiState = SignupUiState.Success
                    } else {
                        uiState = SignupUiState.Error(body?.message ?: "회원가입에 실패했습니다.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    NetworkLog.httpError("register", response.code())
                    val errorMessage = parseErrorMessage(errorBody)
                    uiState = SignupUiState.Error(httpErrorMessage(response.code(), errorMessage))
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (!acceptResponse(requestGeneration)) return@launch
                NetworkLog.exception("register", e)
                uiState = SignupUiState.Error("서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    fun resetUiState() {
        uiState = SignupUiState.Idle
    }

    private fun requireConsent(): Boolean {
        if (hasRequiredConsent) return true
        uiState = SignupUiState.Error("필수 약관에 동의해주세요.")
        return false
    }

    private fun acceptResponse(requestGeneration: Long): Boolean {
        // Old work cannot clear a newer flow's Loading state. Same-flow revocation
        // must leave Loading and report the missing consent, including on exceptions.
        if (requestGeneration != generation) return false
        return requireConsent()
    }

    fun reset() {
        generation++
        termsAccepted = false
        privacyAccepted = false
        name = ""
        password = ""
        emailPrefix = ""
        verificationCode = ""
        uiState = SignupUiState.Idle
    }

    private fun parseErrorMessage(errorBody: String?): String {
        if (errorBody == null) return "오류가 발생했습니다."
        return try {
            val errorResponse = Gson().fromJson(errorBody, SignupErrorResponse::class.java)
            val fieldError = errorResponse.errors?.values?.firstOrNull()
            fieldError ?: errorResponse.message ?: "요청을 처리할 수 없습니다."
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
}
