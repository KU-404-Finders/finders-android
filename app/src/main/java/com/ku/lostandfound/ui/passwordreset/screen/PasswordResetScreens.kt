package com.ku.lostandfound.ui.passwordreset.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
@Composable fun PasswordResetEmailScreen(vm: PasswordResetViewModel, next:()->Unit, back:()->Unit) = ResetLayout("비밀번호를 재설정할\n학교 이메일을 입력해주세요", "가입된 이메일이라면 인증번호가 도착합니다. 메일함과 스팸함을 확인해 주세요.", vm, back) {
    OutlinedTextField(vm.emailInput, vm::updateEmail, Modifier.fillMaxWidth(), label={Text("학교 이메일")}, suffix={Text("@konkuk.ac.kr")}, singleLine=true, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email), isError=vm.uiState.errors["email"]!=null)
    ErrorText(vm.uiState.errors["email"])
    Spacer(Modifier.weight(1f)); BottomFixedButton(if(vm.uiState.isLoading) "발송 중..." else "인증 메일 받기", { vm.requestCode(next) }, enabled=vm.isEmailValid && !vm.uiState.isLoading, modifier=Modifier.padding(vertical=20.dp))
}
@Composable fun PasswordResetCodeScreen(vm: PasswordResetViewModel, next:()->Unit, back:()->Unit) = ResetLayout("이메일로 받은\n인증번호를 입력해주세요", "인증번호는 발급 후 5분간 유효합니다.", vm, back) {
    OutlinedTextField(vm.authCode, vm::updateAuthCode, Modifier.fillMaxWidth(), label={Text("인증번호")}, singleLine=true, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword), isError=vm.uiState.errors["authCode"]!=null)
    ErrorText(vm.uiState.errors["authCode"])
    TextButton(onClick={vm.requestCode({})}, enabled=vm.resendRemainingSeconds==0 && !vm.uiState.isLoading) { Text(if(vm.resendRemainingSeconds>0) "재전송 (${vm.resendRemainingSeconds}초)" else "인증번호 재전송", color=Green) }
    Spacer(Modifier.weight(1f)); BottomFixedButton(if(vm.uiState.isLoading) "확인 중..." else "인증번호 확인", {vm.verifyCode(next)}, enabled=vm.isCodeValid && !vm.uiState.isLoading, modifier=Modifier.padding(vertical=20.dp))
}
@Composable fun PasswordResetPasswordScreen(vm: PasswordResetViewModel, done:()->Unit, back:()->Unit, restart:()->Unit) { var visible by remember { mutableStateOf(false) }; ResetLayout("새 비밀번호를\n입력해주세요", "영문과 숫자를 각각 포함한 6~72자로 설정해주세요.", vm, back) {
    PasswordField("새 비밀번호", vm.newPassword, vm::updateNewPassword, visible) { visible=!visible }; ErrorText(vm.uiState.errors["newPassword"])
    Spacer(Modifier.height(12.dp)); PasswordField("새 비밀번호 확인", vm.newPasswordConfirm, vm::updateNewPasswordConfirm, visible) { visible=!visible }; ErrorText(vm.uiState.errors["newPasswordConfirm"])
    if(vm.uiState.shouldRestart) TextButton(onClick={vm.restart();restart()}) { Text("처음부터 다시 진행", color=Green) }
    Spacer(Modifier.weight(1f)); BottomFixedButton(if(vm.uiState.isLoading) "변경 중..." else "비밀번호 변경", {vm.confirmPassword(done)}, enabled=!vm.uiState.isLoading, modifier=Modifier.padding(vertical=20.dp))
} }
@Composable private fun ResetLayout(title:String, guide:String, vm:PasswordResetViewModel, back:()->Unit, content:@Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxSize().background(Color.White)) { GetBackTopAppBar(onClick=back); Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp)) { Text(title,fontSize=24.sp,lineHeight=34.sp); Spacer(Modifier.height(14.dp)); Text(guide,color=Color(0xFF777777),fontSize=14.sp); Spacer(Modifier.height(42.dp)); content(); vm.uiState.message?.let { ErrorText(it) } } } }
@Composable private fun PasswordField(label:String,value:String,onValue:(String)->Unit,visible:Boolean,toggle:()->Unit) { OutlinedTextField(value,onValue,Modifier.fillMaxWidth(),label={Text(label)},singleLine=true,visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),trailingIcon={IconButton(onClick=toggle){Icon(if(visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,null)}}) }
@Composable private fun ErrorText(text:String?) { if(!text.isNullOrBlank()) Text(text,color=Color(0xFFD32F2F),fontSize=12.sp,modifier=Modifier.padding(top=8.dp)) }
