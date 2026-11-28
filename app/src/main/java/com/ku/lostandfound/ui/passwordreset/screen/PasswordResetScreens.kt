package com.ku.lostandfound.ui.passwordreset.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.ui.component.BottomFixedButton
import com.ku.lostandfound.ui.component.GetBackTopAppBar
import com.ku.lostandfound.ui.passwordreset.viewmodel.PasswordResetViewModel

private val Green = Color(0xFF4A6741)
private val Line = Color(0xFFD9D9D9)
private val Gray = Color(0xFF919191)
@Composable fun PasswordResetEmailScreen(vm: PasswordResetViewModel, next:()->Unit, back:()->Unit) = ResetLayout("비밀번호를 재설정할\n학교 이메일을 입력해주세요", "가입된 이메일이라면 인증번호가 도착합니다. 메일함과 스팸함을 확인해 주세요.", vm, back) {
    val focusManager = LocalFocusManager.current
    TextField(vm.emailInput, vm::updateEmail, Modifier.fillMaxWidth(), placeholder={Text("아이디", color=Line, fontSize=16.sp, fontWeight=FontWeight.SemiBold)}, suffix={Text("@konkuk.ac.kr", color=Color(0xFF555555), fontSize=12.sp, fontWeight=FontWeight.Medium)}, textStyle=TextStyle(fontSize=18.sp, fontWeight=FontWeight.Bold), colors=underlineColors(), singleLine=true, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email, imeAction=ImeAction.Done), keyboardActions=KeyboardActions(onDone={ focusManager.clearFocus(); if (vm.isEmailValid && !vm.uiState.isLoading) vm.requestCode(next) }), isError=vm.uiState.errors["email"]!=null)
    ErrorText(vm.uiState.errors["email"])
    Spacer(Modifier.weight(1f)); BottomFixedButton(if(vm.uiState.isLoading) "발송 중..." else "인증 메일 받기", { vm.requestCode(next) }, enabled=vm.isEmailValid && !vm.uiState.isLoading, modifier=Modifier.padding(vertical=20.dp))
}
@Composable fun PasswordResetCodeScreen(vm: PasswordResetViewModel, next:()->Unit, back:()->Unit) = ResetLayout("이메일로 받은\n인증번호를 입력해주세요", "인증번호는 발급 후 5분간 유효합니다.", vm, back) {
    val focusManager = LocalFocusManager.current
    TextField(vm.authCode, vm::updateAuthCode, Modifier.fillMaxWidth(), placeholder={Text("6자리 숫자", color=Line, fontSize=24.sp, fontWeight=FontWeight.Bold)}, textStyle=TextStyle(fontSize=24.sp, fontWeight=FontWeight.Bold, letterSpacing=8.sp), colors=underlineColors(), singleLine=true, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword, imeAction=ImeAction.Done), keyboardActions=KeyboardActions(onDone={ focusManager.clearFocus(); if (vm.isCodeValid && !vm.uiState.isLoading) vm.verifyCode(next) }), isError=vm.uiState.errors["authCode"]!=null)
    ErrorText(vm.uiState.errors["authCode"])
    TextButton(onClick={vm.requestCode({})}, enabled=vm.resendRemainingSeconds==0 && !vm.uiState.isLoading) { Text(if(vm.resendRemainingSeconds>0) "재전송 (${vm.resendRemainingSeconds}초)" else "인증번호 재전송", color=if(vm.resendRemainingSeconds>0) Gray else Green, fontSize=13.sp, fontWeight=FontWeight.Bold) }
    Spacer(Modifier.weight(1f)); BottomFixedButton(if(vm.uiState.isLoading) "확인 중..." else "인증번호 확인", {vm.verifyCode(next)}, enabled=vm.isCodeValid && !vm.uiState.isLoading, modifier=Modifier.padding(vertical=20.dp))
}
@Composable fun PasswordResetPasswordScreen(vm: PasswordResetViewModel, done:()->Unit, back:()->Unit, restart:()->Unit) { if (vm.resetCompleted) { PasswordResetDoneScreen(vm, done); return }; val finish = {}; var visible by remember { mutableStateOf(false) }; val confirmFocus = remember { FocusRequester() }; val focusManager = LocalFocusManager.current; ResetLayout("새 비밀번호를\n입력해주세요", "영문과 숫자를 각각 포함한 6~72자로 설정해주세요.", vm, back) {
    PasswordField("새 비밀번호", vm.newPassword, vm::updateNewPassword, visible, KeyboardOptions(keyboardType=KeyboardType.Password, imeAction=ImeAction.Next), KeyboardActions(onNext={confirmFocus.requestFocus()}), toggle={ visible=!visible }); ErrorText(vm.uiState.errors["newPassword"])
    Spacer(Modifier.height(12.dp)); PasswordField("새 비밀번호 확인", vm.newPasswordConfirm, vm::updateNewPasswordConfirm, visible, KeyboardOptions(keyboardType=KeyboardType.Password, imeAction=ImeAction.Done), KeyboardActions(onDone={ focusManager.clearFocus(); if (!vm.uiState.isLoading) vm.confirmPassword(finish) }), Modifier.focusRequester(confirmFocus)) { visible=!visible }; ErrorText(vm.uiState.errors["newPasswordConfirm"])
    if(vm.uiState.shouldRestart) TextButton(onClick={vm.restart();restart()}) { Text("처음부터 다시 진행", color=Green) }
    Spacer(Modifier.weight(1f)); BottomFixedButton(if(vm.uiState.isLoading) "변경 중..." else "비밀번호 변경", {vm.confirmPassword(finish)}, enabled=!vm.uiState.isLoading, modifier=Modifier.padding(vertical=20.dp))
} }
// iOS PasswordResetView step .done: a closing screen before returning to the login.
@Composable private fun PasswordResetDoneScreen(vm: PasswordResetViewModel, done:()->Unit) {
    BackHandler(onBack = done)
    ResetLayout("비밀번호가\n변경되었습니다", "새 비밀번호로 다시 로그인해주세요.", vm, done) {
        Icon(Icons.Filled.CheckCircle, contentDescription = "변경 완료", tint = Green, modifier = Modifier.fillMaxWidth().size(64.dp))
        Spacer(Modifier.weight(1f)); BottomFixedButton("로그인하기", done, modifier=Modifier.padding(vertical=20.dp))
    }
}
@Composable private fun underlineColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent, errorContainerColor = Color.Transparent,
    focusedIndicatorColor = Green, unfocusedIndicatorColor = Line, errorIndicatorColor = Color(0xFFD32F2F),
    cursorColor = Green,
)
@Composable private fun ResetLayout(title:String, guide:String, vm:PasswordResetViewModel, back:()->Unit, content:@Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxSize().background(Color.White)) { GetBackTopAppBar(onClick=back); Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp)) { Spacer(Modifier.height(60.dp)); Text(title,fontSize=24.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(15.dp)); Text(guide,color=Gray,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium); Spacer(Modifier.height(42.dp)); content(); vm.uiState.message?.let { ErrorText(it) } } } }
@Composable private fun PasswordField(label:String,value:String,onValue:(String)->Unit,visible:Boolean,keyboardOptions: KeyboardOptions,keyboardActions: KeyboardActions,modifier: Modifier = Modifier,toggle:()->Unit) { TextField(value,onValue,modifier.fillMaxWidth(),placeholder={Text(label, color=Line, fontSize=20.sp, fontWeight=FontWeight.SemiBold)},textStyle=TextStyle(fontSize=20.sp, fontWeight=FontWeight.SemiBold),colors=underlineColors(),singleLine=true,visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=keyboardOptions,keyboardActions=keyboardActions,trailingIcon={IconButton(onClick=toggle){Icon(if(visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if(visible) "비밀번호 숨기기" else "비밀번호 보기", tint=Gray)}}) }
@Composable private fun ErrorText(text:String?) { if(!text.isNullOrBlank()) Text(text,color=Color(0xFFD32F2F),fontSize=12.sp,modifier=Modifier.padding(top=8.dp)) }
