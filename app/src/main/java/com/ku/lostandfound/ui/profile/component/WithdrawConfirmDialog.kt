package com.ku.lostandfound.ui.profile.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun WithdrawConfirmDialog(
    onCancelClick: () -> Unit,
    onWithdrawClick: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancelClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "정말 탈퇴하시겠습니까?",
                color = Color(0xFF222222),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "등록하신 모든 게시물과\n정보가 영구적으로 삭제됩니다.",
                color = Color(0xFF9A9A9A),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp,
                modifier = Modifier.padding(top = 14.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCancelClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF1F1F1),
                        contentColor = Color(0xFF555555)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text(
                        text = "취소",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onWithdrawClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC35555),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text(
                        text = "탈퇴하기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun WithdrawCompleteDialog(
    onConfirmClick: () -> Unit,
) {
    Dialog(
        onDismissRequest = { }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "탈퇴가 완료되었습니다.",
                color = Color(0xFF222222),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "확인을 누르면 로그인 화면으로 이동합니다.",
                color = Color(0xFF9A9A9A),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp,
                modifier = Modifier.padding(top = 14.dp)
            )

            Button(
                onClick = onConfirmClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1B6425),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp)
            ) {
                Text(
                    text = "확인",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}