package com.ku.lostandfound.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.network.InboxNotificationData
import com.ku.lostandfound.network.NotificationSettingsPatch
import com.ku.lostandfound.ui.component.BackTitleBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInboxScreen(vm: NotificationViewModel, onBack: () -> Unit, onOpenRoom: (Long) -> Unit) {
    LaunchedEffect(Unit) { vm.load(true) }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        BackTitleBar(title = "알림함", onBackClick = onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
            if (vm.items.any { it.readAt == null }) TextButton(onClick = vm::markAllRead) { Text("모두 읽음") }
            if (vm.items.isNotEmpty()) TextButton(onClick = vm::hideAll) { Text("전체 숨기기", color = Color(0xFFD32F2F)) }
        }
        PullToRefreshBox(isRefreshing = vm.state is InboxState.Loading, onRefresh = { vm.load(true) }, modifier = Modifier.weight(1f)) {
            when (val state = vm.state) {
                is InboxState.Unsupported -> InboxMessage("알림함을 사용할 수 없습니다", state.message)
                is InboxState.Error -> InboxMessage("알림을 불러오지 못했습니다", state.message, vm::load)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (vm.items.isEmpty() && state !is InboxState.Loading) item { InboxMessage("받은 알림이 없습니다", "새 채팅이 오면 여기에 표시됩니다.") }
                    items(vm.items, key = { it.id }) { item ->
                        SwipeToHideNotification(item = item, onHide = { vm.hide(item) }) {
                            InboxRow(item, onClick = { vm.markRead(item) { item.roomId?.let(onOpenRoom) } })
                        }
                    }
                    if (vm.nextBeforeSequence != null) item { TextButton(onClick = { vm.load(false) }, modifier = Modifier.fillMaxWidth(), enabled = !vm.loadingMore) { Text(if (vm.loadingMore) "불러오는 중…" else "이전 알림 더 보기") } }
                }
            }
        }
    }
}

@Composable private fun InboxRow(item: InboxNotificationData, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.title ?: "새 알림", fontWeight = if (item.readAt == null) FontWeight.Bold else FontWeight.Normal) },
        supportingContent = { Text(listOfNotNull(item.body, item.unreadMessages?.takeIf { it > 1 }?.let { "새 메시지 ${it.coerceAtMost(99)}개" }).joinToString("\n")) },
        leadingContent = { Text(if (item.readAt == null) "●" else "○", color = if (item.readAt == null) Color(0xFF1B6425) else Color.Gray) },
        modifier = Modifier.fillMaxWidth(),
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToHideNotification(
    item: InboxNotificationData,
    onHide: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target != SwipeToDismissBoxValue.Settled) onHide()
            false // The list updates only after the account-local hide state is persisted.
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFFD32F2F)).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text("숨기기", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        content = { content() },
    )
}

@Composable private fun InboxMessage(title: String, message: String, retry: (() -> Unit)? = null) = Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(message, color = Color.Gray, fontSize = 13.sp); retry?.let { TextButton(onClick = it) { Text("다시 시도") } } } }

@Composable
fun NotificationSettingsScreen(vm: NotificationViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadSettings() }
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F4F4))) {
        BackTitleBar(title = "알림 설정", onBackClick = onBack)
        val settings = vm.settings
        if (settings == null) InboxMessage("알림 설정", vm.settingsError ?: "불러오는 중입니다.", if (vm.settingsError != null) ({ vm.loadSettings() }) else null)
        else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.settingsError?.let { Text(it, color = Color(0xFFD32F2F)) }
            SettingSwitch("채팅 알림", settings.chatEnabled) { vm.updateSettings(NotificationSettingsPatch(chatEnabled = it)) }
            SettingSwitch("댓글 알림", settings.commentEnabled) { vm.updateSettings(NotificationSettingsPatch(commentEnabled = it)) }
            SettingSwitch("유사 게시글 알림", settings.similarItemEnabled) { vm.updateSettings(NotificationSettingsPatch(similarItemEnabled = it)) }
            if (settings.similarItemEnabled) {
                var threshold by remember(settings.similarityThreshold) { mutableFloatStateOf(settings.similarityThreshold.toFloat()) }
                Text("알림 기준 유사도 ${(threshold * 100).toInt()}% 이상")
                Slider(value = threshold, onValueChange = { threshold = it }, onValueChangeFinished = { vm.updateSettings(NotificationSettingsPatch(similarityThreshold = threshold.toDouble())) }, valueRange = 0.05f..1f)
            }
        }
    }
}

@Composable private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) = Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange) } }
