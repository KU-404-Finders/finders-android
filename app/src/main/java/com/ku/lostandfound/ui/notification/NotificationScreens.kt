package com.ku.lostandfound.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import com.ku.lostandfound.network.InboxNotificationData
import com.ku.lostandfound.network.NotificationSettingsPatch
import com.ku.lostandfound.ui.component.BackTitleBar
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInboxScreen(vm: NotificationViewModel, onBack: () -> Unit, onOpenRoom: (Long) -> Unit,
    onOpenPost: (String, Long) -> Unit,
    accountError: String? = null, onRetryAccount: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmDeleteAll by remember { mutableStateOf(false) }
    fun read(item: InboxNotificationData, open: () -> Unit = {}) {
        vm.markRead(item, onError = { android.widget.Toast.makeText(context, "알림 읽음 처리에 실패했습니다.", android.widget.Toast.LENGTH_SHORT).show() }, onOpen = open)
    }
    androidx.compose.runtime.DisposableEffect(vm) {
        vm.setInboxOpen(true)
        onDispose { vm.setInboxOpen(false) }
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        BackTitleBar(title = "알림함", onBackClick = onBack)
        vm.refreshError?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
            if (vm.items.any { it.readAt == null }) TextButton(onClick = vm::markAllRead) { Text("모두 읽음") }
            if (vm.items.isNotEmpty()) TextButton(onClick = { confirmDeleteAll = true }) { Text("전체 삭제", color = Color(0xFFD32F2F)) }
        }
        PullToRefreshBox(isRefreshing = vm.state is InboxState.Loading, onRefresh = {
            if (vm.account == null) onRetryAccount()
            else if (vm.state !is InboxState.Loading) vm.load(true)
        }, modifier = Modifier.weight(1f)) {
            when (val state = vm.state) {
                is InboxState.Unsupported -> InboxMessage("알림함을 사용할 수 없습니다", state.message)
                is InboxState.Error -> InboxMessage("알림을 불러오지 못했습니다", state.message, vm::load)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (vm.account == null) item { InboxMessage("계정 정보", accountError ?: "계정 정보를 기다리고 있습니다.", onRetryAccount) }
                    else if (vm.items.isEmpty() && state !is InboxState.Loading) item {
                        InboxMessage("받은 알림이 없습니다", "새 채팅과 내 글에 달린 댓글이 여기에 모입니다.")
                    }
                    items(vm.items, key = { it.id }) { item ->
                        SwipeToHideNotification(item = item, onHide = { vm.hide(item) }, onRead = { read(item) }) {
                            InboxRow(item, onClick = { vm.openNotification(item,
                                onReadError = { android.widget.Toast.makeText(context, "알림 읽음 처리에 실패했습니다.", android.widget.Toast.LENGTH_SHORT).show() },
                                onMessage = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() },
                                onOpenPost = onOpenPost, onOpenRoom = onOpenRoom)
                            })
                        }
                    }
                    if (vm.nextBeforeSequence != null) item { TextButton(onClick = { vm.load(false) }, modifier = Modifier.fillMaxWidth(), enabled = !vm.loadingMore) { Text(if (vm.loadingMore) "불러오는 중…" else "이전 알림 더 보기") } }
                }
            }
        }
    }
    if (confirmDeleteAll) AlertDialog(
        onDismissRequest = { confirmDeleteAll = false },
        title = { Text("알림을 모두 삭제할까요?") },
        text = { Text("목록의 기준 시점까지 받은 알림을 모두 읽음 처리하고 삭제합니다. 아직 불러오지 않은 이전 알림도 포함하며, 기준 시점 이후 새로 도착한 알림은 남을 수 있습니다.") },
        confirmButton = { TextButton(onClick = { confirmDeleteAll = false; vm.hideAll() }) { Text("전체 삭제", color = Color(0xFFD32F2F)) } },
        dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("취소") } },
    )
}

@Composable private fun InboxRow(item: InboxNotificationData, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.title ?: "새 알림", fontWeight = if (item.readAt == null) FontWeight.Bold else FontWeight.Normal) },
        supportingContent = { Text(listOfNotNull(item.body, item.unreadMessages?.takeIf { item.type != "COMMENT" && item.roomId != null && it > 1 }?.let { "새 메시지 ${it.coerceAtMost(99)}개" }).joinToString("\n")) },
        leadingContent = { Text(if (item.readAt == null) "●" else "○", color = if (item.readAt == null) Color(0xFF1B6425) else Color.Gray) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToHideNotification(
    item: InboxNotificationData,
    onHide: () -> Unit,
    onRead: () -> Unit,
    content: @Composable () -> Unit,
) {
    val currentItem by rememberUpdatedState(item)
    val currentHide by rememberUpdatedState(onHide)
    val currentRead by rememberUpdatedState(onRead)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.EndToStart) currentHide()
            if (target == SwipeToDismissBoxValue.StartToEnd && currentItem.readAt == null) currentRead()
            false // The list updates only after the account-local hide state is persisted.
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Color(0xFF1B6425) else Color(0xFFD32F2F)).padding(horizontal = 20.dp),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) { Text(if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) "읽음" else "삭제", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        enableDismissFromStartToEnd = item.readAt == null,
        content = { content() },
    )
}

@Composable private fun InboxMessage(title: String, message: String, retry: (() -> Unit)? = null) = Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(message, color = Color.Gray, fontSize = 13.sp); retry?.let { TextButton(onClick = it) { Text("다시 시도") } } } }

@Composable
fun NotificationSettingsScreen(vm: NotificationViewModel, onBack: () -> Unit,
    accountError: String? = null, onRetryAccount: () -> Unit = {}) {
    LaunchedEffect(vm.account) { vm.loadSettings() }
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F4F4))) {
        BackTitleBar(title = "알림 설정", onBackClick = onBack)
        val settings = vm.settings
        if (settings == null) InboxMessage("알림 설정", accountError ?: vm.settingsError ?: if (vm.account == null) "계정 정보를 기다리고 있습니다." else "불러오는 중입니다.", if (vm.account == null) onRetryAccount else if (vm.settingsError != null) ({ vm.loadSettings() }) else null)
        else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.settingsError?.let { Text(it, color = Color(0xFFD32F2F)) }
            SettingSwitch("채팅 알림", settings.chatEnabled) { vm.updateSettings(NotificationSettingsPatch(chatEnabled = it)) }
            SettingSwitch("댓글 알림", settings.commentEnabled) { vm.updateSettings(NotificationSettingsPatch(commentEnabled = it)) }
            SettingSwitch("유사 게시글 알림", settings.similarItemEnabled) { vm.updateSettings(NotificationSettingsPatch(similarItemEnabled = it)) }
            if (settings.similarItemEnabled) {
                var threshold by remember(settings.similarityThreshold) { mutableFloatStateOf(settings.similarityThreshold.toFloat()) }
                Text("알림 기준 유사도 ${(threshold * 100).toInt()}% 이상")
                Slider(value = threshold, onValueChange = { threshold = it }, onValueChangeFinished = {
                    val normalized = (threshold * 20).roundToInt() / 20.0
                    if (kotlin.math.abs(normalized - settings.similarityThreshold) >= 0.001) {
                        vm.updateSettings(NotificationSettingsPatch(similarityThreshold = normalized))
                    }
                }, valueRange = 0.05f..1f, steps = 18, colors = SliderDefaults.colors(thumbColor = Color(0xFF1B6425), activeTrackColor = Color(0xFF1B6425)))
            }
        }
    }
}

@Composable private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) = Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange) } }
