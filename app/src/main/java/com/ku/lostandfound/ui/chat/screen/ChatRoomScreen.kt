
package com.ku.lostandfound.ui.chat.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ku.lostandfound.network.ChatMessageResponse
import com.ku.lostandfound.ui.chat.ChatTimeFormatter
import com.ku.lostandfound.ui.component.noRippleClickable

private val DeepGreen = Color(0xFF1B6425)
// iOS ChatColors.
private val FieldGray = Color(0xFFF2F4F2)
private val IncomingColor = Color(0xFFF1F3F1)
private val TextGray = Color(0xFF8A8A8A)

@Composable
fun ChatRoomScreen(
    roomId: Long,
    roomTitle: String?,
    messages: List<ChatMessageResponse>,
    currentUserId: Long?,
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    isConnected: Boolean,
    connectionMessage: String?,
    onSendMessage: (String) -> Boolean,
    postAuthorId: Long? = null,
    postAuthorName: String? = null,
    hasLinkedPost: Boolean = false,
    canBlockUser: Boolean = false,
    onViewPost: () -> Unit = {},
    onBlockUser: () -> Unit = {},
    onLeaveRoom: () -> Unit = {},
    roomSubtitle: String? = null,
) {
    val listState = rememberLazyListState()

    var messageText by remember(roomId) {
        mutableStateOf("")
    }

    val canSend = isConnected && messageText.isNotBlank()
    var menuExpanded by remember(roomId) { mutableStateOf(false) }
    var confirmBlock by remember(roomId) { mutableStateOf(false) }
    var confirmLeave by remember(roomId) { mutableStateOf(false) }

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
        // iOS FinderNavBar with subtitle: chevron, title/subtitle that opens the post, ellipsis menu.
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "뒤로가기", tint = Color.Black, modifier = Modifier.size(30.dp))
            }
            Column(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .clickable(enabled = hasLinkedPost, role = Role.Button, onClickLabel = "게시글 보기") { onViewPost() },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = roomTitle?.takeIf { it.isNotBlank() } ?: "채팅",
                    color = Color(0xFF1E1E1E), fontSize = if (roomSubtitle == null) 20.sp else 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                roomSubtitle?.let {
                    Text(it, color = TextGray, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "더보기", tint = Color(0xFF222222))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("게시글 보기") },
                        enabled = hasLinkedPost,
                        onClick = { menuExpanded = false; onViewPost() },
                    )
                    DropdownMenuItem(
                        text = { Text("사용자 차단") },
                        enabled = canBlockUser,
                        onClick = { menuExpanded = false; confirmBlock = true },
                    )
                    DropdownMenuItem(
                        text = { Text("채팅방 나가기") },
                        onClick = { menuExpanded = false; confirmLeave = true },
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = Color.Black.copy(alpha = 0.06f))

        if (!isConnected || connectionMessage != null) {
            Text(
                text = connectionMessage
                    ?: "실시간 채팅에 연결하는 중입니다…",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF4E0))
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                color = Color(0xFF8A5A00),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = DeepGreen
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
                        text = "첫 메시지를 보내 대화를 시작해보세요.",
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                        color = TextGray,
                        fontSize = 13.sp,
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(
                            vertical = 16.dp
                        ),
                        verticalArrangement =
                            Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(
                            items = messages,
                            key = { _, message -> message.id }
                        ) { index, message ->
                            val previous = messages.getOrNull(index - 1)
                            val sameDay = previous != null &&
                                ChatTimeFormatter.dayKey(previous.createdAt) == ChatTimeFormatter.dayKey(message.createdAt)
                            if (!sameDay) DaySeparator(message.createdAt)
                            ChatMessageBubble(
                                message = message,
                                isMine = currentUserId != null && message.senderId == currentUserId,
                                continued = sameDay && previous?.senderId == message.senderId,
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = Color.Black.copy(alpha = 0.06f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
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
                            "메시지를 입력하세요"
                        } else {
                            "서버 연결을 기다리는 중…"
                        },
                        fontSize = 15.sp,
                        color = TextGray,
                    )
                },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
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
                    .heightIn(min = 48.dp),
            )

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (canSend) DeepGreen else Color(0xFFC8D6CA))
                    .clickable(enabled = canSend, role = Role.Button, onClickLabel = "보내기") {
                        if (onSendMessage(messageText)) messageText = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "보내기", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        if (confirmBlock) {
            ChatActionConfirmDialog(
                action = ChatConfirmAction.Block,
                onDismiss = { confirmBlock = false },
                onConfirm = { confirmBlock = false; onBlockUser() },
            )
        }
        if (confirmLeave) {
            ChatActionConfirmDialog(
                action = ChatConfirmAction.Leave,
                onDismiss = { confirmLeave = false },
                onConfirm = { confirmLeave = false; onLeaveRoom() },
            )
        }
    }
}


@Composable
private fun DaySeparator(createdAt: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            ChatTimeFormatter.formatDayLabel(createdAt),
            color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.background(FieldGray, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

// iOS messageBubble: green outgoing / grey incoming, metadata beside the bubble, tail corner on the first of a run.
@Composable
private fun ChatMessageBubble(
    message: ChatMessageResponse,
    isMine: Boolean,
    continued: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = if (isMine) 48.dp else 0.dp,
            end = if (isMine) 0.dp else 48.dp,
            top = if (continued) 0.dp else 6.dp,
        ),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (isMine) {
            MessageMeta(message, mine = true)
            Spacer(Modifier.width(6.dp))
        }
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (isMine || continued) 18.dp else 6.dp,
                        topEnd = if (isMine && !continued) 6.dp else 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp,
                    )
                )
                .background(if (isMine) DeepGreen else IncomingColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.content,
                color = if (isMine) Color.White else Color(0xFF222222),
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
        }
        if (!isMine) {
            Spacer(Modifier.width(6.dp))
            MessageMeta(message, mine = false)
        }
    }
}

@Composable
private fun MessageMeta(message: ChatMessageResponse, mine: Boolean) {
    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (mine) {
            val read = message.unreadCount == 0
            Text(if (read) "읽음" else "안읽음", color = if (read) DeepGreen else TextGray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
        Text(ChatTimeFormatter.formatTime(message.createdAt), color = TextGray, fontSize = 10.sp)
    }
}
