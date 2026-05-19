package com.ku.lostandfound.ui.signup.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.ui.component.BottomFixedButton
import com.ku.lostandfound.ui.component.GetBackTopAppBar
import com.ku.lostandfound.ui.signup.viewmodel.SignupViewmodel

@Composable
fun SignupCodeScreen(
    viewModel: SignupViewmodel,
    onNavigateToFinish: () -> Unit = {},
    onNavigateToBack: () -> Unit = {},
) {
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        GetBackTopAppBar(onClick = onNavigateToBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "메일로 보내드린\n인증번호를 입력해주세요",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.Black,
                lineHeight = 34.sp,
                modifier = Modifier.padding(top = 60.dp)
            )

            Text(
                text = "${viewModel.email.ifBlank { "학교 이메일" }} 로 보냈어요.",
                modifier = Modifier.padding(top = 15.dp),
                color = Color(0xFF919191),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            TextField(
                value = viewModel.verificationCode,
                onValueChange = { input ->
                    if (input.length <= 6 && input.all { it.isDigit() }) {
                        viewModel.verificationCode = input
                        isError = false
                    }
                },
                placeholder = {
                    Text(
                        text = "6자리 숫자",
                        color = Color(0xFFD9D9D9),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    letterSpacing = 8.sp
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = if (isError) Color(0xFFD32F2F) else Color(0xFF4A6741),
                    unfocusedIndicatorColor = if (isError) Color(0xFFD32F2F) else Color(0xFFD9D9D9),
                    cursorColor = Color(0xFF4A6741)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 50.dp)
            )

            if (isError) {
                Text(
                    text = "인증번호가 올바르지 않습니다.",
                    color = Color(0xFFD32F2F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(220.dp))

            BottomFixedButton(
                text = "인증하기",
                onClick = {
                    // 현재는 UI 테스트용 코드. 서버 연결 시 이 부분에서 viewModel.name/password/email을 한 번에 넘기면 된다.
                    if (viewModel.verificationCode == "202613") {
                        isError = false
                        onNavigateToFinish()
                    } else {
                        isError = true
                    }
                },
                enabled = viewModel.isVerificationCodeValid,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }
}

@Preview
@Composable
private fun SignupAuthNumPreview() {
    SignupCodeScreen(viewModel = SignupViewmodel().apply { emailPrefix = "konkuk26" })
}
