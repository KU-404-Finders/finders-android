package com.ku.lostandfound.ui.signup.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.R
import com.ku.lostandfound.ui.component.BottomFixedButton

@Composable
fun SignupFinishScreen(onNavigateToLogin: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.size(200.dp))
        Image(
            painter = painterResource(id = R.drawable.img_complete_signup_mark),
            contentDescription = "회원가입완료",
            modifier = Modifier.size(130.dp)
        )
        Spacer(modifier = Modifier.size(35.dp))
        Text(
            "가입이 성공적으로\n완료되었습니다",
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.weight(1f))
        BottomFixedButton(
            text = "로그인하기",
            onClick = onNavigateToLogin,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.size(54.dp))
    }
}

@Preview
@Composable
private fun SignupFinishScreenPreview() {
    SignupFinishScreen()
}

