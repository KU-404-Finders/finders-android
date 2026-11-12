
package com.ku.lostandfound.ui.chat.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.network.ChatRoomResponse
import com.ku.lostandfound.ui.chat.ChatTimeFormatter
import com.ku.lostandfound.ui.component.BackTitleBar
import com.ku.lostandfound.ui.component.noRippleClickable

private val DeepGreen = Color(0xFF1B6425)
private val LostPink = Color(0xFFEF5B80)
private val LostPinkLight = Color(0xFFFFEDF1)
private val FoundBlue = Color(0xFF246FB5)
private val FoundBlueLight = Color(0xFFE6F2FB)
private val TextGray = Color(0xFF929292)

@Composable
fun ChatListScreen(
    rooms: List<ChatRoomResponse>,
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onRoomClick: (Long) -> Unit,
    authorInfoByRoomId: Map<Long, Pair<Long, String>> = emptyMap(),
    currentUserId: Long? = null,
    currentUserName: String? = null,
    onLeaveRoom: (Long) -> Unit = {},
) {
    var leaveTargetId by remember { mutableStateOf<Long?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        BackTitleBar(
            title = "채팅",
            onBackClick = onBackClick
        )

        when {
            isLoading && rooms.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = DeepGreen)
                }
            }

            errorMessage != null && rooms.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFD32F2F)
                    )
                }
            }

            rooms.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "아직 참여 중인 채팅방이 없습니다.",
                        color = TextGray
                    )
                }
            }

            else -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(
                        items = rooms,
                        key = { it.id }
                    ) { room ->
                        SwipeToLeaveRoom(
                            room = room,
                            onRequestLeave = { leaveTargetId = it },
                        ) {
                            ChatRoomItem(
                                room = room,
                                authorInfo = authorInfoByRoomId[room.id],
                                currentUserId = currentUserId,
                                currentUserName = currentUserName,
                                onClick = { onRoomClick(room.id) }
                            )
                        }

                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = Color(0xFFEEEEEE)
                        )
                    }
                }
            }
        }
    }
    leaveTargetId?.let { roomId ->
        ChatActionConfirmDialog(
            action = ChatConfirmAction.Leave,
            onDismiss = { leaveTargetId = null },
            onConfirm = { leaveTargetId = null; onLeaveRoom(roomId) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToLeaveRoom(
    room: ChatRoomResponse,
    onRequestLeave: (Long) -> Unit,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target != SwipeToDismissBoxValue.Settled) onRequestLeave(room.id)
            false // Keep the item until the membership API confirms success.
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFFB3261E)).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text("나가기", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        content = { content() },
    )
}

@Composable
private fun ChatRoomItem(
    room: ChatRoomResponse,
    authorInfo: Pair<Long, String>?,
    currentUserId: Long?,
    currentUserName: String?,
    onClick: () -> Unit,
) {
    val type = room.postType?.uppercase()

    val iconColor = when (type) {
        "LOST" -> LostPink
        "FOUND" -> FoundBlue
        else -> Color(0xFF999999)
    }

    val iconBackground = when (type) {
        "LOST" -> LostPinkLight
        "FOUND" -> FoundBlueLight
        else -> Color(0xFFEEEEEE)
    }

    val typeLabel = when (type) {
        "LOST" -> "분실물"
        "FOUND" -> "습득물"
        else -> null
    }

    val title = room.postTitle
        ?.takeIf { it.isNotBlank() }
        ?: room.roomName?.takeIf { it.isNotBlank() }
        ?: "이전 채팅"

    val senderName = when {
        room.lastMessageSenderId == null -> null

        currentUserId != null &&
                room.lastMessageSenderId == currentUserId ->
            currentUserName?.takeIf { it.isNotBlank() }

        authorInfo != null &&
                room.lastMessageSenderId == authorInfo.first ->
            authorInfo.second

        else -> null
    }

    val snippet = room.lastMessage
        ?.takeIf { it.isNotBlank() }
        ?: "아직 메시지가 없습니다."

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .noRippleClickable(onClick)
            .padding(
                horizontal = 16.dp,
                vertical = 16.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(
                    iconBackground,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val image = when (type) {
                "LOST" -> Icons.Default.Search
                "FOUND" -> Icons.Default.CheckCircle
                else -> Icons.Default.ChatBubble
            }

            Icon(
                imageVector = image,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(27.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                color = Color(0xFF242424),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                if (typeLabel != null) {
                    Text(
                        text = typeLabel,
                        color = iconColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                iconBackground,
                                CircleShape
                            )
                            .padding(
                                horizontal = 7.dp,
                                vertical = 3.dp
                            )
                    )
                }

                Text(
                    text = if (senderName.isNullOrBlank()) {
                        snippet
                    } else {
                        "$senderName  $snippet"
                    },
                    color = Color(0xFF777777),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = ChatTimeFormatter.formatListTime(
                    room.lastMessageAt ?: room.createdAt
                ),
                color = TextGray,
                fontSize = 11.sp
            )

            room.unreadMessageCount
                ?.takeIf { it > 0 }
                ?.let { count ->
                    Text(
                        text = count.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                DeepGreen,
                                CircleShape
                            )
                            .padding(
                                horizontal = 7.dp,
                                vertical = 2.dp
                            )
                    )
                }
        }
    }
}
