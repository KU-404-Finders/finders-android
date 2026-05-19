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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.ui.component.BottomFixedButton
import com.ku.lostandfound.ui.component.GetBackTopAppBar
import com.ku.lostandfound.ui.signup.viewmodel.SignupViewmodel

@Composable
fun SignupPwScreen(
    viewModel: SignupViewmodel,
    onNavigateToEmail: () -> Unit = {},
    onNavigateToBack: () -> Unit = {},
) {
    val isError = viewModel.password.isNotEmpty() && !viewModel.isPasswordValid

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
                text = "사용할 비밀번호를\n입력해주세요",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.Black,
                lineHeight = 34.sp,
                modifier = Modifier.padding(top = 60.dp)
            )

            TextField(
                value = viewModel.password,
                onValueChange = { viewModel.password = it },
                placeholder = {
                    Text(
                        text = "비밀번호",
                        color = Color(0xFFD9D9D9),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E1E1E),
                    letterSpacing = 4.sp
                ),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                    text = "비밀번호는 6자 이상이며, 영문과 숫자를 모두 포함해야 합니다.",
                    color = Color(0xFFD32F2F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(220.dp))

            BottomFixedButton(
                text = "다음",
                onClick = onNavigateToEmail,
                enabled = viewModel.isPasswordValid,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }
}

@Preview
@Composable
private fun SignupPwPreview() {
    SignupPwScreen(viewModel = SignupViewmodel())
}
