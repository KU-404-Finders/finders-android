package com.ku.lostandfound.ui.chat.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
private val FieldGray = Color(0xFFF4F4F4)
private val TextGray = Color(0xFF8A8A8A)
private val MyMessageColor = Color(0xFFE4F6E5)

private data class ChatMessageUiModel(
    val id: Long,
    val message: String,
    val isMine: Boolean,
    val time: String,
)

@Composable
fun ChatRoomScreen(
    postId: String,
    onBackClick: () -> Unit,
) {
    val messages = remember {
        mutableStateListOf(
            ChatMessageUiModel(
                id = 1L,
                message = "안녕하세요. 혹시 이 물건 본인 물건 맞으신가요?",
                isMine = false,
                time = "15:21",
            ),
            ChatMessageUiModel(
                id = 2L,
                message = "네! 제가 잃어버린 물건 같습니다.",
                isMine = true,
                time = "15:22",
            ),
            ChatMessageUiModel(
                id = 3L,
                message = "물건 특징을 한번 말씀해주시겠어요?",
                isMine = false,
                time = "15:23",
            ),
        )
    }

    var messageText by remember {
        mutableStateOf("")
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

        Text(
            text = "게시글 #${postId}에서 시작된 채팅",
            color = TextGray,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF9F9F9))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                vertical = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = messages,
                key = { it.id }
            ) { message ->
                ChatMessageBubble(message)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(
                    horizontal = 16.dp,
                    vertical = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = messageText,
                onValueChange = {
                    messageText = it
                },
                placeholder = {
                    Text(
                        text = "메시지를 입력하세요.",
                        color = TextGray,
                        fontSize = 13.sp,
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
                    cursorColor = DeepGreen,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            )

            Spacer(
                modifier = Modifier.size(10.dp)
            )

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (messageText.isBlank()) {
                            Color(0xFFB7CBB9)
                        } else {
                            DeepGreen
                        }
                    )
                    .noRippleClickable {
                        val trimmedMessage = messageText.trim()

                        if (trimmedMessage.isNotBlank()) {
                            messages.add(
                                ChatMessageUiModel(
                                    id = System.currentTimeMillis(),
                                    message = trimmedMessage,
                                    isMine = true,
                                    time = "방금",
                                )
                            )

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
    message: ChatMessageUiModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
    ) {
        Column(
            horizontalAlignment = if (message.isMine) {
                Alignment.End
            } else {
                Alignment.Start
            }
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isMine) 16.dp else 4.dp,
                            bottomEnd = if (message.isMine) 4.dp else 16.dp,
                        )
                    )
                    .background(
                        if (message.isMine) {
                            MyMessageColor
                        } else {
                            FieldGray
                        }
                    )
                    .padding(
                        horizontal = 14.dp,
                        vertical = 10.dp
                    )
            ) {
                Text(
                    text = message.message,
                    color = Color(0xFF222222),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = message.time,
                color = TextGray,
                fontSize = 10.sp,
            )
        }
    }
}