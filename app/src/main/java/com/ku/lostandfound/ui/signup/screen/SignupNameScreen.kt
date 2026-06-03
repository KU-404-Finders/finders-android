package com.ku.lostandfound.ui.signup.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.ui.component.BottomFixedButton
import com.ku.lostandfound.ui.component.GetBackTopAppBar
import com.ku.lostandfound.ui.signup.viewmodel.SignupViewmodel

@Composable
fun SignupNameScreen(
    viewModel: SignupViewmodel,
    onNavigateToPw: () -> Unit = {},
    onNavigateToBack: () -> Unit = {},
) {
    val isError = viewModel.name.isNotEmpty() && !viewModel.isNameValid
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
    ) {
        GetBackTopAppBar(onClick = onNavigateToBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "이름을 알려주세요",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.Black,
                modifier = Modifier.padding(top = 60.dp)
            )
            Text(
                text = "서비스 내 닉네임으로 실명이 사용돼요.",
                modifier = Modifier.padding(top = 15.dp),
                color = Color(0xFF919191),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            TextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                placeholder = {
                    Text(
                        text = "예 ) 김건국",
                        color = Color(0xFFD9D9D9),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E1E1E)
                ),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = if (isError) Color(0xFFD32F2F) else Color(0xFF4A6741),
                    unfocusedIndicatorColor = if (isError) Color(0xFFD32F2F) else Color(0xFFD9D9D9),
                    cursorColor = Color(0xFF4A6741),
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 50.dp)
            )

            if (isError) {
                Text(
                    text = "이름은 한글 2~5자여야 합니다.",
                    color = Color(0xFFD32F2F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        BottomFixedButton(
            text = "다음",
            onClick = onNavigateToPw,
            enabled = viewModel.isNameValid,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        )
    }
}

@Preview
@Composable
private fun SignupNameScreenPreview() {
    SignupNameScreen(viewModel = SignupViewmodel())
}
