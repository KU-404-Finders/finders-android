package com.ku.lostandfound.ui.login.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.R
import com.ku.lostandfound.ui.login.viewmodel.LoginUiState
import com.ku.lostandfound.ui.login.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit = {},
    onNavigateToSignUp: () -> Unit = {},
) {
    var emailPrefix by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val uiState = viewModel.uiState
    val isLoading = uiState is LoginUiState.Loading
    val errorMessage = (uiState as? LoginUiState.Error)?.message

    val isLoginEnabled = emailPrefix.isNotBlank() && password.isNotBlank() && !isLoading

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            viewModel.resetUiState()
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
            .padding(horizontal = 24.dp)
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Image(
            painter = painterResource(id = R.drawable.img_lost_and_found_ku),
            contentDescription = "찾을건대 로고",
            modifier = Modifier.size(300.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "학교 이메일",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF555555),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = emailPrefix,
                onValueChange = {
                    emailPrefix = it
                    if (uiState is LoginUiState.Error) viewModel.resetUiState()
                },
                placeholder = {
                    Text("아이디", color = Color.LightGray, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = "이메일 아이콘",
                        tint = Color.Gray
                    )
                },
                trailingIcon = {
                    Text(
                        text = "@konkuk.ac.kr",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                singleLine = true,
                isError = errorMessage != null,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4A6741),
                    unfocusedBorderColor = Color(0xFFD9D9D9),
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    errorBorderColor = Color.Red
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "비밀번호",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF555555),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (uiState is LoginUiState.Error) viewModel.resetUiState()
                },
                placeholder = {
                    Text("비밀번호를 입력하세요", color = Color.LightGray, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "비밀번호 아이콘",
                        tint = Color.Gray
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 보기",
                        tint = Color.Gray,
                        modifier = Modifier.clickable { passwordVisible = !passwordVisible }
                    )
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = errorMessage != null,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4A6741),
                    unfocusedBorderColor = Color(0xFFD9D9D9),
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    errorBorderColor = Color.Red
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.login(emailPrefix, password) },
            enabled = isLoginEnabled,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4A6741),
                disabledContainerColor = Color(0xFFD9D9D9),
                contentColor = Color.White,
                disabledContentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = if (isLoading) "로그인 중..." else "로그인 →",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "아직 회원이 아니신가요?",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "회원가입",
                color = Color(0xFF4A6741),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.clickable {
                    onNavigateToSignUp()
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LoginScreen(viewModel = LoginViewModel())
}
