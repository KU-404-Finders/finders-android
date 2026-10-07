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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.ui.component.BackTitleBar

private val DeepGreen = Color(0xFF1B6425)
private val ScreenGray = Color(0xFFF5F5F5)
private val TextGray = Color(0xFF888888)
private val DangerRed = Color(0xFFD32F2F)

@Composable
fun AdminReportDetailScreen(
    report: AdminReportUiModel?,
    onBackClick: () -> Unit,
    onActionConfirmed: (String) -> Unit,
) {

    if (report == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenGray)
        ) {
            BackTitleBar(
                title = "신고 상세",
                onBackClick = onBackClick,
            )

            Text(
                text = "신고 정보를 찾을 수 없습니다.",
                modifier = Modifier.padding(20.dp),
                color = TextGray,
            )
        }

        return
    }

    var selectedAction by remember(report.id) {
        mutableStateOf<String?>(null)
    }

    val completedAction = report.processedAction
        ?: if (report.isProcessed) "처리 완료" else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenGray)
    ) {
        BackTitleBar(
            title = "신고 상세",
            onBackClick = onBackClick,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "신고 #${report.id}",
                    color = Color(0xFF222222),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = if (completedAction != null) {
                        "처리 완료"
                    } else {
                        "미처리"
                    },
                    color = if (completedAction != null) {
                        DeepGreen
                    } else {
                        DangerRed
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            AdminDetailCard(
                title = "신고된 게시글"
            ) {
                DetailRow(
                    label = "게시글",
                    value = report.postTitle,
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            AdminDetailCard(
                title = "신고 정보"
            ) {
                DetailRow(
                    label = "신고자",
                    value = report.reporterName,
                )

                DetailRow(
                    label = "피신고자",
                    value = report.reportedUserName,
                )

                DetailRow(
                    label = "신고 사유",
                    value = report.reason,
                )

                DetailRow(
                    label = "신고 일시",
                    value = report.createdAt,
                )
            }

            if (completedAction != null) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                AdminDetailCard(
                    title = "처리 결과"
                ) {
                    Text(
                        text = completedAction!!,
                        color = DeepGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Text(
                text = "관리자 처리",
                color = Color(0xFF222222),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Button(
                onClick = {
                    selectedAction = "신고 반려"
                },
                enabled = completedAction == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF666666),
                )
            ) {
                Text(
                    text = "신고 반려",
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Button(
                onClick = {
                    selectedAction = "게시글 삭제"
                },
                enabled = completedAction == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DangerRed,
                )
            ) {
                Text(
                    text = "게시글 삭제",
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Button(
                onClick = {
                    selectedAction = "사용자 이용 제한"
                },
                enabled = completedAction == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepGreen,
                )
            ) {
                Text(
                    text = "사용자 이용 제한",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    selectedAction?.let { action ->
        AlertDialog(
            onDismissRequest = {
                selectedAction = null
            },

            title = {
                Text(
                    text = "$action 처리",
                    fontWeight = FontWeight.Bold,
                )
            },

            text = {
                Text(
                    text = "이 신고를 '$action' 상태로 처리하시겠습니까?"
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        onActionConfirmed(action)
                        selectedAction = null
                    }
                ) {
                    Text(
                        text = "처리",
                        color = if (action == "게시글 삭제") {
                            DangerRed
                        } else {
                            DeepGreen
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        selectedAction = null
                    }
                ) {
                    Text(
                        text = "취소",
                        color = TextGray,
                    )
                }
            },

            containerColor = Color.White,
        )
    }
}

@Composable
private fun AdminDetailCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF222222),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.3f),
            color = TextGray,
            fontSize = 13.sp,
        )

        Text(
            text = value,
            modifier = Modifier.weight(0.7f),
            color = Color(0xFF333333),
            fontSize = 13.sp,
        )
    }
}