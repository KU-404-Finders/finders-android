package com.ku.lostandfound.ui.profile.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.ui.chat.ChatTimeFormatter
import com.ku.lostandfound.ui.component.BackTitleBar
import com.ku.lostandfound.ui.component.FinderConfirmDialog
import com.ku.lostandfound.ui.component.FinderGreen
import com.ku.lostandfound.ui.component.FinderPersonAvatar
import com.ku.lostandfound.ui.profile.viewmodel.BlockedUserRow
import com.ku.lostandfound.ui.profile.viewmodel.BlockedUsersViewModel

private val Canvas = Color(0xFFF4F4F4)
private val Secondary = Color(0xFF888888)
private val StateGray = Color(0xFF777777)
private val ErrorRed = Color(0xFFB3261E)

// Mirrors iOS AccountViews.swift BlockedUsersView.
@Composable
fun BlockedUsersScreen(vm: BlockedUsersViewModel, accountId: Long?, onBack: () -> Unit, onUnblocked: () -> Unit) {
    LaunchedEffect(accountId) { vm.attachAccount(accountId); vm.load() }
    var confirmation by remember(accountId) { mutableStateOf<BlockedUserRow?>(null) }
    Column(Modifier.fillMaxSize().background(Canvas)) {
        BackTitleBar(title = "차단한 사용자", onBackClick = onBack)
        val users = vm.users
        when {
            users.isEmpty() && (vm.loading || accountId == null) ->
                StateText(if (accountId == null) "계정 정보를 기다리고 있습니다." else "불러오는 중입니다.", StateGray)
            users.isEmpty() && vm.error != null -> Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(vm.error.orEmpty(), color = ErrorRed, fontSize = 14.sp, textAlign = TextAlign.Center)
                TextButton(onClick = vm::retry, enabled = !vm.loading) { Text("다시 시도", color = FinderGreen) }
            }
            users.isEmpty() -> StateText("차단한 사용자가 없습니다.", StateGray)
            else -> LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "차단을 해제하면 그 사용자의 게시글이 다시 보이고 서로 채팅할 수 있습니다.",
                        color = Secondary, fontSize = 12.sp, lineHeight = 18.sp,
                    )
                }
                vm.error?.let { message ->
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message, color = ErrorRed, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = vm::retry, enabled = !vm.loading) { Text("다시 시도", color = FinderGreen, fontSize = 12.sp) }
                        }
                    }
                }
                items(users, key = { it.id }) { user ->
                    BlockedUserCard(user, unblocking = user.id in vm.unblocking) { confirmation = user }
                }
                if (vm.hasMore && vm.error == null) item {
                    // iOS loads the next page when the end of the list appears.
                    LaunchedEffect(users.size) { vm.load(reset = false) }
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = FinderGreen, strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
    confirmation?.let { user ->
        FinderConfirmDialog(
            title = "차단을 해제하시겠습니까?",
            message = "해당 사용자의 게시글이 다시 보이고 서로 채팅할 수 있게 됩니다.",
            confirmText = "차단 해제",
            onDismiss = { confirmation = null },
            onConfirm = {
                confirmation = null
                vm.unblock(user.id, onUnblocked)
            },
        )
    }
}

@Composable
private fun ColumnScope.StateText(text: String, color: Color) {
    Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BlockedUserCard(user: BlockedUserRow, unblocking: Boolean, onUnblock: () -> Unit) {
    // Server timestamps that do not parse show no date rather than a guessed one.
    val blockedAt = remember(user.createdAt) { ChatTimeFormatter.formatFullDateTime(user.createdAt) }
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FinderPersonAvatar(40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(user.name, color = Color(0xFF222222), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (blockedAt.isNotEmpty()) Text("차단일 $blockedAt", color = Secondary, fontSize = 11.sp)
        }
        // 32dp capsule like iOS, inside a 48dp touch target.
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .clickable(enabled = !unblocking, role = Role.Button, onClick = onUnblock),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .height(32.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, FinderGreen.copy(alpha = if (unblocking) 0.3f else 0.6f), CircleShape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (unblocking) "해제 중…" else "차단 해제",
                    color = FinderGreen.copy(alpha = if (unblocking) 0.5f else 1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
