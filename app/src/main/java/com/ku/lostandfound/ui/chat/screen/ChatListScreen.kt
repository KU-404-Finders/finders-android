
package com.ku.lostandfound.ui.chat.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.ku.lostandfound.ui.component.HomeTopBar
import com.ku.lostandfound.ui.component.noRippleClickable

private val DeepGreen = Color(0xFF1B6425)
// iOS FinderTheme danger/pink and blue/sky pairs.
private val LostPink = Color(0xFFFF5D7A)
private val LostPinkLight = Color(0xFFFFECEF)
private val FoundBlue = Color(0xFF1565C0)
private val FoundBlueLight = Color(0xFFE3F2FD)
private val TextGray = Color(0xFF8A8A8A)

@Composable
fun ChatListScreen(
    rooms: List<ChatRoomResponse>,
    isLoading: Boolean,
    errorMessage: String?,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    notificationCount: Int,
    onRoomClick: (Long) -> Unit,
    authorInfoByRoomId: Map<Long, Pair<Long, String>> = emptyMap(),
    currentUserId: Long? = null,
    currentUserName: String? = null,
    onLeaveRoom: (Long) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    var leaveTargetId by remember { mutableStateOf<Long?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        HomeTopBar(
            title = "채팅",
            onProfileClick = onProfileClick,
            onSearchClick = onSearchClick,
            onNotificationClick = onNotificationClick,
            notificationCount = notificationCount,
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

            errorMessage != null && rooms.isEmpty() ->
                ChatEmptyState(Icons.Outlined.WifiOff, "채팅 목록을 불러오지 못했습니다", errorMessage)

            rooms.isEmpty() -> ChatEmptyState(
                Icons.Outlined.Forum, "아직 대화가 없습니다",
                "게시글 상세의 채팅 버튼을 누르면 작성자와 대화를 시작할 수 있어요.",
            )

            else -> {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
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
                            modifier = Modifier.padding(start = 86.dp),
                            thickness = 0.5.dp,
                            color = Color.Black.copy(alpha = 0.06f)
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

@Composable
private fun ChatEmptyState(icon: ImageVector, title: String, message: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFC9CFC9), modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = Color(0xFF4A4A4A), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(message, color = TextGray, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
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
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(54.dp).background(iconBackground, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val image = when (type) {
                "LOST" -> Icons.Default.Search
                "FOUND" -> Icons.Default.CheckCircle
                else -> Icons.Default.ChatBubble
            }
            Icon(imageVector = image, contentDescription = null, tint = iconColor, modifier = Modifier.size(23.dp))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color(0xFF222222),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = ChatTimeFormatter.formatListTime(room.lastMessageAt ?: room.createdAt),
                    color = TextGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (typeLabel != null) {
                    Text(
                        text = typeLabel,
                        color = iconColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(iconBackground, CircleShape).padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                if (!senderName.isNullOrBlank()) {
                    Text(senderName, color = Color(0xFF6B6B6B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Text(
                    text = snippet,
                    color = if (room.lastMessage.isNullOrBlank()) TextGray else Color(0xFF555555),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val unread = room.unreadMessageCount ?: 0
        if (unread > 0) {
            Box(
                Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp).background(DeepGreen, CircleShape)
                    .padding(horizontal = 7.dp)
                    .semantics { contentDescription = "안 읽은 메시지 ${unread}개" },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (unread > 99) "99+" else unread.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFC4C4C4), modifier = Modifier.size(20.dp))
        }
    }
}
