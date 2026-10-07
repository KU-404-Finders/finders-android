
package com.ku.lostandfound.ui.chat.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.network.ChatMessageResponse
import com.ku.lostandfound.ui.component.BackTitleBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ku.lostandfound.ui.component.noRippleClickable

private val DeepGreen = Color(0xFF1B6425)
private val FieldGray = Color(0xFFF4F4F4)
private val TextGray = Color(0xFF8A8A8A)
private val MyMessageColor = Color(0xFFE4F6E5)

@Composable
fun ChatRoomScreen(
    roomId: Long,
    messages: List<ChatMessageResponse>,
    currentUserId: Long?,
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    isConnected: Boolean,
    connectionMessage: String?,
    onSendMessage: (String) -> Boolean,
) {
    val listState = rememberLazyListState()

    var messageText by remember(roomId) {
        mutableStateOf("")
    }

    val canSend = isConnected && messageText.isNotBlank()

    // 채팅방 입장 또는 메시지 변경 시 최신 메시지로 이동
    LaunchedEffect(roomId, messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        BackTitleBar(
            title = "1:1 채팅",
            onBackClick = onBackClick,
        )
        if (!isConnected || connectionMessage != null) {
            Text(
                text = connectionMessage ?: "실시간 채팅 연결 중...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color(0xFFD32F2F),
                fontSize = 12.sp,
            )
        }

        Text(
            text = "채팅방 #${roomId}",
            color = TextGray,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF9F9F9))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = DeepGreen,
                    )
                }

                errorMessage != null -> {
                    Text(
                        text = errorMessage,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(20.dp),
                        color = Color(0xFFD32F2F),
                    )
                }

                messages.isEmpty() -> {
                    Text(
                        text = "아직 메시지가 없습니다.",
                        modifier = Modifier.align(Alignment.Center),
                        color = TextGray,
                        fontSize = 14.sp,
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = messages,
                            key = { it.id },
                        ) { message ->
                            val isMine =
                                currentUserId != null &&
                                        message.senderId == currentUserId

                            ChatMessageBubble(
                                message = message,
                                isMine = isMine,
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = messageText,
                onValueChange = {
                    if (it.length <= 2000) {
                        messageText = it
                    }
                },
                enabled = isConnected,
                placeholder = {
                    Text(
                        text = if (isConnected) {
                            "메시지를 입력하세요."
                        } else {
                            "서버 연결을 기다리는 중..."
                        },
                        fontSize = 13.sp,
                        color = TextGray,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = FieldGray,
                    unfocusedContainerColor = FieldGray,
                    disabledContainerColor = FieldGray,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            )

            Spacer(Modifier.size(10.dp))

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (canSend) DeepGreen
                        else Color(0xFFB7CBB9)
                    )
                    .noRippleClickable {
                        if (canSend && onSendMessage(messageText)) {
                            messageText = ""
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "➤",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessageResponse,
    isMine: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
    ) {
        Column(
            horizontalAlignment = if (isMine) {
                Alignment.End
            } else {
                Alignment.Start
            },
        ) {
            if (!isMine) {
                Text(
                    text = "사용자 #${message.senderId}",
                    color = TextGray,
                    fontSize = 11.sp,
                )

                Spacer(Modifier.height(4.dp))
            }

            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMine) 16.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 16.dp,
                        )
                    )
                    .background(
                        if (isMine) MyMessageColor else FieldGray
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.content,
                    color = Color(0xFF222222),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = message.createdAt
                    .substringAfter("T", "")
                    .take(5),
                color = TextGray,
                fontSize = 10.sp,
            )
        }
    }
}
