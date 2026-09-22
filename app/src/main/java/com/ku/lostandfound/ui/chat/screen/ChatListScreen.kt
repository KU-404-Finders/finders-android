package com.ku.lostandfound.ui.chat.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.ui.component.BackTitleBar
import com.ku.lostandfound.ui.component.noRippleClickable

private val DeepGreen = Color(0xFF1B6425)
private val ScreenGray = Color(0xFFF4F4F4)
private val TextGray = Color(0xFF8A8A8A)

private data class ChatRoomUiModel(
    val id: String,
    val postId: String,
    val userName: String,
    val postTitle: String,
    val lastMessage: String,
    val timeText: String,
    val unreadCount: Int = 0,
)

@Composable
fun ChatListScreen(
    onBackClick: () -> Unit,
    onRoomClick: (String) -> Unit,
) {
    val rooms = remember {
        listOf(
            ChatRoomUiModel(
                id = "1",
                postId = "101",
                userName = "습득자01",
                postTitle = "왼쪽 에어팟 분실",
                lastMessage = "네, 5시에 뵙겠습니다!",
                timeText = "오후 2:34",
                unreadCount = 1,
            ),
            ChatRoomUiModel(
                id = "2",
                postId = "102",
                userName = "습득자02",
                postTitle = "검정 우산을 찾습니다",
                lastMessage = "도서관 안내데스크에 맡겼어요.",
                timeText = "어제",
            ),
            ChatRoomUiModel(
                id = "3",
                postId = "103",
                userName = "습득자03",
                postTitle = "파란 카드지갑 분실",
                lastMessage = "감사합니다. 잘 받았습니다!",
                timeText = "9월 9일",
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenGray)
    ) {
        BackTitleBar(
            title = "채팅",
            onBackClick = onBackClick,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            items(
                items = rooms,
                key = { it.id }
            ) { room ->
                ChatRoomItem(
                    room = room,
                    onClick = {
                        onRoomClick(room.postId)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatRoomItem(
    room: ChatRoomUiModel,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .noRippleClickable(onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier.size(62.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFEDEDED)
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "사진",
                    color = TextGray,
                    fontSize = 11.sp,
                )
            }
        }

        Spacer(
            modifier = Modifier.size(12.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = room.postTitle,
                color = Color(0xFF222222),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )

            Text(
                text = room.userName,
                color = TextGray,
                fontSize = 12.sp,
            )

            Text(
                text = room.lastMessage,
                color = Color(0xFF555555),
                fontSize = 13.sp,
                maxLines = 1,
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = room.timeText,
                color = TextGray,
                fontSize = 11.sp,
            )

            if (room.unreadCount > 0) {
                Spacer(
                    modifier = Modifier.size(8.dp)
                )

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(DeepGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = room.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}