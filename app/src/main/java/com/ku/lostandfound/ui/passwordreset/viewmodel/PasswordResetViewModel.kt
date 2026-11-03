package com.ku.lostandfound.ui.passwordreset.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ku.lostandfound.network.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Response

enum class PasswordResetStep { EMAIL, CODE, PASSWORD }
data class PasswordResetUiState(val step: PasswordResetStep = PasswordResetStep.EMAIL, val isLoading: Boolean = false, val message: String? = null, val errors: Map<String, String> = emptyMap(), val shouldRestart: Boolean = false)

class PasswordResetViewModel(private val api: PasswordResetApiService = RetrofitClient.passwordResetApi) : ViewModel() {
    var emailInput by mutableStateOf(""); private set
    var authCode by mutableStateOf(""); private set
    var newPassword by mutableStateOf(""); private set
    var newPasswordConfirm by mutableStateOf(""); private set
    private var resetToken: String? = null
    private var cooldownJob: Job? = null
    var resendRemainingSeconds by mutableIntStateOf(0); private set
    var uiState by mutableStateOf(PasswordResetUiState()); private set
    val fullEmail get() = emailInput.trim().let { if (it.endsWith("@konkuk.ac.kr", true)) it else "$it@konkuk.ac.kr" }
    val isEmailValid get() = emailInput.trim().isNotBlank() && fullEmail.endsWith("@konkuk.ac.kr", true)
    val isCodeValid get() = authCode.length == 6 && authCode.all(Char::isDigit)
    val isPasswordValid get() = newPassword.matches(Regex("^(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{6,72}$"))
    val passwordsMatch get() = newPassword == newPasswordConfirm
    fun updateEmail(value: String) { emailInput = value.trim(); authCode = ""; resetToken = null; clearError() }
    fun updateAuthCode(value: String) { authCode = value.filter(Char::isDigit).take(6); clearError() }
    fun updateNewPassword(value: String) { newPassword = value; clearError() }
    fun updateNewPasswordConfirm(value: String) { newPasswordConfirm = value; clearError() }
    fun requestCode(next: () -> Unit) { if (!isEmailValid) { uiState=uiState.copy(message="학교 이메일을 입력해주세요."); return }; launchRequest {
        val response = api.requestReset(PasswordResetRequest(fullEmail))
        if (response.isSuccessful && response.body()?.success == true) { authCode=""; resetToken=null; uiState=PasswordResetUiState(PasswordResetStep.CODE); cooldown(60); next() } else error(response, "인증번호 발송에 실패했습니다.", false)
    }
    } // requestCode 종료
    fun verifyCode(next: () -> Unit) { if (!isCodeValid) { uiState=uiState.copy(message="인증번호 6자리를 입력해주세요."); return }; launchRequest {
        val response = api.verifyCode(PasswordResetVerifyRequest(fullEmail, authCode)); val token=response.body()?.data?.resetToken
        if (response.isSuccessful && response.body()?.success == true && !token.isNullOrBlank()) { resetToken=token; authCode=""; uiState=PasswordResetUiState(PasswordResetStep.PASSWORD); next() }
        else if (response.isSuccessful && response.body()?.success == true) uiState=uiState.copy(isLoading=false, message="인증 정보를 확인할 수 없습니다. 새 인증번호를 요청해주세요.", shouldRestart=true)
        else error(response, "인증번호를 확인해주세요.", false)
    }
    } // verifyCode 종료
    fun confirmPassword(done: () -> Unit) {
        if (!isPasswordValid || !passwordsMatch) { uiState=uiState.copy(errors=buildMap { if(!isPasswordValid) put("newPassword", "영문과 숫자를 포함한 6~72자의 비밀번호를 입력해주세요."); if(!passwordsMatch) put("newPasswordConfirm", "비밀번호가 일치하지 않습니다.") }); return }
        val token=resetToken ?: run { uiState=uiState.copy(message="인증 시간이 만료되었습니다. 처음부터 다시 진행해주세요.", shouldRestart=true); return }
        launchRequest { val response=api.confirmReset(PasswordResetConfirmRequest(token,newPassword,newPasswordConfirm)); if(response.isSuccessful && response.body()?.success==true) { TokenManager.clear(); clearSensitive(); done() } else error(response,"비밀번호 변경에 실패했습니다.",true) }
    }
    fun returnToEmail() { authCode=""; resetToken=null; newPassword=""; newPasswordConfirm=""; uiState=PasswordResetUiState() }
    fun restart() { emailInput=""; cooldownJob?.cancel(); resendRemainingSeconds=0; returnToEmail() }
    fun clearSensitive() { authCode=""; newPassword=""; newPasswordConfirm=""; resetToken=null; cooldownJob?.cancel(); resendRemainingSeconds=0; uiState=PasswordResetUiState() }
    private fun launchRequest(block: suspend () -> Unit) { if(uiState.isLoading) return; viewModelScope.launch { uiState=uiState.copy(isLoading=true,message=null,errors=emptyMap(),shouldRestart=false); try { block() } catch(_:Exception) { uiState=uiState.copy(isLoading=false,message="서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.") } } }
    private fun cooldown(seconds:Int) { cooldownJob?.cancel(); resendRemainingSeconds=seconds; cooldownJob=viewModelScope.launch { while(resendRemainingSeconds>0){ delay(1000); resendRemainingSeconds-- } } }
    private fun error(response: Response<*>, fallback:String, confirm:Boolean) { if(response.code()==429) cooldown(response.headers()["Retry-After"]?.toIntOrNull()?.coerceAtLeast(1) ?: 60); val parsed=try { Gson().fromJson(response.errorBody()?.string(),PasswordResetResponse::class.java) } catch(_:Exception){null}; val body=response.body(); val message=(when(body){ is PasswordResetResponse->body.message; is PasswordResetVerifyResponse->body.message; else->null } ?: parsed?.message ?: when(response.code()){429->"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";503->if(confirm) "일시적인 장애입니다. 새 인증 절차를 진행해주세요." else "일시적인 장애입니다. 잠시 후 다시 시도해주세요.";500->if(confirm) "변경 결과를 확인할 수 없습니다. 새 인증 절차를 진행해주세요." else "오류가 발생했습니다. 잠시 후 다시 시도해주세요.";else->fallback}); val errors=(when(body){is PasswordResetResponse->body.errors;is PasswordResetVerifyResponse->body.errors;else->null} ?: parsed?.errors.orEmpty()); uiState=uiState.copy(isLoading=false,message=message,errors=errors,shouldRestart=confirm && response.code() in setOf(400,500,503)) }
    private fun clearError(){ if(uiState.message!=null || uiState.errors.isNotEmpty()) uiState=uiState.copy(message=null,errors=emptyMap(),shouldRestart=false) }
    override fun onCleared() { clearSensitive(); super.onCleared() }
}
