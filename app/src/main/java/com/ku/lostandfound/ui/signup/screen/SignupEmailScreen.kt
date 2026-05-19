package com.ku.lostandfound.ui.signup.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
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
fun SignupEmailScreen(
    viewModel: SignupViewmodel,
    onNavigateToCode: () -> Unit = {},
    onNavigateToBack: () -> Unit = {},
) {
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
                text = "학교 이메일을\n입력해주세요",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.Black,
                lineHeight = 34.sp,
                modifier = Modifier.padding(top = 60.dp)
            )

            Text(
                text = "건국인 인증을 위해 필요해요.",
                modifier = Modifier.padding(top = 15.dp),
                color = Color(0xFF919191),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 50.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = viewModel.emailPrefix,
                    onValueChange = { viewModel.emailPrefix = it.trim() },
                    placeholder = {
                        Text(
                            text = "아이디",
                            color = Color(0xFFD9D9D9),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color(0xFF4A6741),
                        unfocusedIndicatorColor = Color(0xFFD9D9D9),
                        cursorColor = Color(0xFF4A6741)
                    ),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "@konkuk.ac.kr",
                    color = Color(0xFF555555),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(220.dp))

            BottomFixedButton(
                text = "인증 메일 받기",
                onClick = onNavigateToCode,
                enabled = viewModel.isEmailValid,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SignupEmailScreenPreview() {
    SignupEmailScreen(viewModel = SignupViewmodel())
}
