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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
private val PendingRed = Color(0xFFD32F2F)

data class AdminReportUiModel(
    val id: String,
    val reporterName: String,
    val reportedUserName: String,
    val postTitle: String,
    val reason: String,
    val createdAt: String,
    val isProcessed: Boolean,
)

@Composable
fun AdminReportListScreen(
    onBackClick: () -> Unit,
    onReportClick: (String) -> Unit,
) {
    val reports = remember {
        listOf(
            AdminReportUiModel(
                id = "1",
                reporterName = "김건국",
                reportedUserName = "습득자01",
                postTitle = "에어팟 습득했습니다",
                reason = "허위 정보",
                createdAt = "2026.09.22 18:30",
                isProcessed = false,
            ),
            AdminReportUiModel(
                id = "2",
                reporterName = "이건국",
                reportedUserName = "사용자02",
                postTitle = "검정 우산 주웠습니다",
                reason = "분실물/습득물과 관련 없는 게시글",
                createdAt = "2026.09.21 14:20",
                isProcessed = false,
            ),
            AdminReportUiModel(
                id = "3",
                reporterName = "박건국",
                reportedUserName = "사용자03",
                postTitle = "학생증 습득",
                reason = "스팸 또는 광고성 게시글",
                createdAt = "2026.09.20 10:15",
                isProcessed = true,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenGray)
    ) {
        BackTitleBar(
            title = "신고 관리",
            onBackClick = onBackClick,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp,
                vertical = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "접수된 신고",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF222222),
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "신고 내용을 확인하고 처리할 수 있습니다.",
                    fontSize = 13.sp,
                    color = TextGray,
                )
            }

            items(
                items = reports,
                key = { it.id },
            ) { report ->
                AdminReportCard(
                    report = report,
                    onClick = {
                        onReportClick(report.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun AdminReportCard(
    report: AdminReportUiModel,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = report.postTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF222222),
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = if (report.isProcessed) {
                        "처리 완료"
                    } else {
                        "미처리"
                    },
                    color = if (report.isProcessed) {
                        DeepGreen
                    } else {
                        PendingRed
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = "신고 사유: ${report.reason}",
                fontSize = 13.sp,
                color = Color(0xFF444444),
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = "신고자: ${report.reporterName}",
                fontSize = 12.sp,
                color = TextGray,
            )

            Text(
                text = "피신고자: ${report.reportedUserName}",
                fontSize = 12.sp,
                color = TextGray,
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = report.createdAt,
                fontSize = 11.sp,
                color = TextGray,
            )
        }
    }
}