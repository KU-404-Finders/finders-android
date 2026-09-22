package com.ku.lostandfound.ui.admin.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.ui.component.BackTitleBar
import com.ku.lostandfound.ui.component.noRippleClickable

private val DeepGreen = Color(0xFF1B6425)
private val ScreenGray = Color(0xFFF5F5F5)
private val TextGray = Color(0xFF888888)

@Composable
fun AdminDashboardScreen(
    onBackClick: () -> Unit,
    onReportManagementClick: () -> Unit = {},
    onUserManagementClick: () -> Unit = {},
    onPostManagementClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenGray)
    ) {
        BackTitleBar(
            title = "관리자 페이지",
            onBackClick = onBackClick,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "관리 메뉴",
                color = Color(0xFF222222),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "서비스 운영 및 신고 내역을 관리할 수 있습니다.",
                color = TextGray,
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(4.dp))

            AdminMenuCard(
                title = "신고 관리",
                description = "접수된 게시글 및 사용자 신고를 확인합니다.",
                countText = "미처리 3건",
                onClick = onReportManagementClick,
            )

            AdminMenuCard(
                title = "사용자 관리",
                description = "사용자 정보와 이용 제한 상태를 확인합니다.",
                onClick = onUserManagementClick,
            )

            AdminMenuCard(
                title = "게시글 관리",
                description = "등록된 분실물·습득물 게시글을 관리합니다.",
                onClick = onPostManagementClick,
            )
        }
    }
}

@Composable
private fun AdminMenuCard(
    title: String,
    description: String,
    countText: String? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = Color(0xFF222222),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Text(
                    text = description,
                    color = TextGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }

            if (countText != null) {
                Text(
                    text = countText,
                    color = DeepGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = "›",
                    color = TextGray,
                    fontSize = 26.sp,
                )
            }
        }
    }
}