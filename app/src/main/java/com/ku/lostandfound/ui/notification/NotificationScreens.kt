package com.ku.lostandfound.ui.notification

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ku.lostandfound.ui.chat.ChatTimeFormatter
import com.ku.lostandfound.ui.component.FinderConfirmDialog
import com.ku.lostandfound.network.InboxNotificationData
import com.ku.lostandfound.network.NotificationSettingsPatch
import com.ku.lostandfound.notification.FindersNotificationHelper
import com.ku.lostandfound.notification.RemoteNotificationKind
import com.ku.lostandfound.ui.component.BackTitleBar
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInboxScreen(vm: NotificationViewModel, onBack: () -> Unit, onOpenRoom: (Long) -> Unit,
    onOpenPost: (String, Long) -> Unit,
    accountError: String? = null, onRetryAccount: () -> Unit = {}) {
    val context = LocalContext.current
    var confirmDeleteAll by remember { mutableStateOf(false) }
    fun read(item: InboxNotificationData, open: () -> Unit = {}) {
        vm.markRead(item, onError = { android.widget.Toast.makeText(context, "알림 읽음 처리에 실패했습니다.", android.widget.Toast.LENGTH_SHORT).show() }, onOpen = open)
    }
    androidx.compose.runtime.DisposableEffect(vm) {
        vm.setInboxOpen(true)
        onDispose { vm.setInboxOpen(false) }
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        BackTitleBar(title = "알림함", onBackClick = onBack) {
            if (vm.items.any { it.readAt == null }) TextButton(onClick = vm::markAllRead) {
                Text("모두 읽음", color = InboxGreen, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            if (vm.items.isNotEmpty()) TextButton(onClick = { confirmDeleteAll = true }) {
                Text("전체 삭제", color = Color(0xFFD32F2F), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        vm.refreshError?.let { Text(it, Modifier.padding(16.dp), color = Color(0xFFB3261E), fontSize = 13.sp) }
        PullToRefreshBox(isRefreshing = vm.state is InboxState.Loading, onRefresh = {
            if (vm.account == null) onRetryAccount()
            else if (vm.state !is InboxState.Loading) vm.load(true)
        }, modifier = Modifier.weight(1f)) {
            when (val state = vm.state) {
                is InboxState.Unsupported -> InboxMessage("알림함을 사용할 수 없습니다", state.message, icon = Icons.Outlined.NotificationsOff)
                is InboxState.Error -> InboxMessage("알림을 불러오지 못했습니다", state.message, vm::load, Icons.Outlined.ErrorOutline)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (vm.account == null) item { InboxMessage("계정 정보", accountError ?: "계정 정보를 기다리고 있습니다.", onRetryAccount) }
                    else if (vm.items.isEmpty() && state !is InboxState.Loading) item {
                        InboxMessage("받은 알림이 없습니다", "새 채팅과 내 글에 달린 댓글이 여기에 모입니다.", icon = Icons.Outlined.Notifications)
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
                    if (vm.nextBeforeSequence != null) item { TextButton(onClick = { vm.load(false) }, modifier = Modifier.fillMaxWidth(), enabled = !vm.loadingMore) { Text(if (vm.loadingMore) "불러오는 중…" else "이전 알림 더 보기", color = InboxGreen) } }
                }
            }
        }
    }
    // Android copy keeps the exact scope of hideAll (older unloaded rows included).
    if (confirmDeleteAll) FinderConfirmDialog(
        title = "알림을 모두 삭제할까요?",
        message = "목록의 기준 시점까지 받은 알림을 모두 읽음 처리하고 삭제합니다. 아직 불러오지 않은 이전 알림도 포함하며, 기준 시점 이후 새로 도착한 알림은 남을 수 있습니다.",
        confirmText = "전체 삭제",
        confirmColor = Color(0xFFD32F2F),
        onDismiss = { confirmDeleteAll = false },
        onConfirm = { confirmDeleteAll = false; vm.hideAll() },
    )
}

private val InboxGreen = Color(0xFF1B6425)
private val InboxSecondary = Color(0xFF8E8E93)

// iOS NotificationInboxView.row: unread dot, type symbol on a tinted circle, title + relative time, body.
@Composable private fun InboxRow(item: InboxNotificationData, onClick: () -> Unit) {
    val unread = item.readAt == null
    val unreadMessages = item.unreadMessages?.takeIf { unread && item.type != "COMMENT" && item.roomId != null && it > 1 }
    Row(
        Modifier.fillMaxWidth().background(Color.White).clickable(onClick = onClick).padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.padding(top = 16.dp).size(9.dp).background(if (unread) InboxGreen else Color.Transparent, CircleShape)
            .semantics { if (unread) contentDescription = "읽지 않음" })
        Box(Modifier.size(40.dp).background(if (unread) Color(0xFFE4F6E5) else Color(0xFFEFEFF1), CircleShape), contentAlignment = Alignment.Center) {
            Icon(inboxIcon(item.type), contentDescription = null, tint = if (unread) InboxGreen else InboxSecondary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title?.takeIf { it.isNotBlank() } ?: "새 알림", Modifier.weight(1f), fontSize = 15.sp,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ChatTimeFormatter.formatRelative(item.createdAt), Modifier.padding(start = 8.dp), fontSize = 12.sp, color = InboxSecondary)
            }
            item.body?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 14.sp, color = if (unread) Color(0xFF3C3C43) else InboxSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            unreadMessages?.let {
                Text("새 메시지 ${if (it > 99) "99+" else it.toString()}개", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = InboxGreen)
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 0.5.dp, color = Color(0xFFE5E5EA))
}

private fun inboxIcon(type: String): ImageVector = when (type.uppercase()) {
    "CHAT" -> Icons.AutoMirrored.Filled.Chat
    "COMMENT" -> Icons.AutoMirrored.Filled.Comment
    "MATCH", "SIMILAR_ITEM" -> Icons.Filled.AutoAwesome
    else -> Icons.Filled.Notifications
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
    val density = LocalDensity.current
    val state = remember(item.id, density) {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            positionalThreshold = { with(density) { 56.dp.toPx() } },
        )
    }
    val settled = state.settledValue
    LaunchedEffect(settled) {
        when (settled) {
            SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
            SwipeToDismissBoxValue.StartToEnd -> if (currentItem.readAt == null) currentRead()
            SwipeToDismissBoxValue.EndToStart -> currentHide()
        }
        state.reset()
    }
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

@Composable private fun InboxMessage(title: String, message: String, retry: (() -> Unit)? = null, icon: ImageVector? = null) =
    Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 90.dp, bottom = 24.dp), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            icon?.let { Icon(it, contentDescription = null, tint = Color(0xFFC7C7CC), modifier = Modifier.size(34.dp)) }
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(message, color = InboxSecondary, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
            retry?.let { TextButton(onClick = it) { Text("다시 시도", color = InboxGreen) } }
        }
    }

// iOS NotificationSettingsView: grouped canvas, plain rows (no tile icons), changes saved per toggle.
@Composable
fun NotificationSettingsScreen(vm: NotificationViewModel, onBack: () -> Unit,
    accountError: String? = null, onRetryAccount: () -> Unit = {}) {
    val context = LocalContext.current
    LaunchedEffect(vm.account) { vm.loadSettings() }
    var systemEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    fun open(intent: android.content.Intent) {
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, "설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show() }
    }
    Column(Modifier.fillMaxSize().background(SettingsCanvas)) {
        BackTitleBar(title = "알림 설정", onBackClick = onBack)
        val settings = vm.settings
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!systemEnabled) item {
                SettingsGroupBox {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            open(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }.heightIn(min = 52.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("기기 알림 켜기", Modifier.weight(1f), fontSize = 16.sp)
                        Text("설정 열기", color = InboxGreen, fontSize = 15.sp)
                    }
                }
                SettingsFootnote("기기 설정에서 찾을건대 알림이 꺼져 있어 푸시가 표시되지 않습니다.")
            }
            item {
                if (settings == null) {
                    InboxMessage("알림 설정", accountError ?: vm.settingsError ?: if (vm.account == null) "계정 정보를 기다리고 있습니다." else "불러오는 중입니다.", if (vm.account == null) onRetryAccount else if (vm.settingsError != null) ({ vm.loadSettings() }) else null)
                } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    vm.settingsError?.let { SettingsGroupBox { Text(it, Modifier.padding(16.dp), color = Color(0xFFB3261E), fontSize = 14.sp) } }
                    Column {
                        SettingsHeading("채팅")
                        SettingsGroupBox {
                            SettingSwitch("채팅 알림", settings.chatEnabled) {
                                vm.updateSettings(NotificationSettingsPatch(chatEnabled = it))
                            }
                        }
                    }
                    Column {
                        SettingsHeading("게시글")
                        SettingsGroupBox {
                            SettingSwitch("댓글 알림", settings.commentEnabled) {
                                vm.updateSettings(NotificationSettingsPatch(commentEnabled = it))
                            }
                            SettingsRowDivider()
                            SettingSwitch("유사 게시글 알림", settings.similarItemEnabled) {
                                vm.updateSettings(NotificationSettingsPatch(similarItemEnabled = it))
                            }
                            if (settings.similarItemEnabled) {
                                SettingsRowDivider()
                                // A failed save leaves `settings` unchanged; the revision still resets the draft to it.
                                var threshold by remember(settings.similarityThreshold, vm.settingsRevision) {
                                    mutableFloatStateOf(settings.similarityThreshold.toFloat())
                                }
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("알림 기준 유사도", Modifier.weight(1f), fontSize = 16.sp)
                                        Text("${(threshold * 100).roundToInt()}% 이상", color = InboxSecondary, fontSize = 15.sp)
                                    }
                                    Slider(
                                        value = threshold,
                                        onValueChange = { threshold = it },
                                        onValueChangeFinished = {
                                            val normalized = (threshold * 20).roundToInt() / 20.0
                                            if (kotlin.math.abs(normalized - settings.similarityThreshold) >= 0.001) {
                                                vm.updateSettings(NotificationSettingsPatch(similarityThreshold = normalized))
                                            }
                                        },
                                        valueRange = 0.05f..1f,
                                        steps = 18,
                                        modifier = Modifier.semantics { contentDescription = "알림 기준 유사도" },
                                        colors = SliderDefaults.colors(thumbColor = InboxGreen, activeTrackColor = InboxGreen),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Android only: sound, vibration and heads-up live in each notification channel.
            item {
                SettingsHeading("기기 알림 표시")
                SettingsGroupBox {
                    RemoteNotificationKind.entries.forEachIndexed { index, kind ->
                        if (index > 0) SettingsRowDivider()
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { open(FindersNotificationHelper.systemSettingsIntent(context, kind)) }
                                .heightIn(min = 52.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${kind.title} 표시 설정", Modifier.weight(1f), fontSize = 16.sp)
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFC7C7CC))
                        }
                    }
                }
                SettingsFootnote("소리, 진동, 화면 상단 팝업은 시스템 알림 설정에서 관리해요.")
            }
        }
    }
}

private val SettingsCanvas = Color(0xFFF2F2F7)

@Composable private fun SettingsHeading(text: String) =
    Text(text, Modifier.padding(start = 16.dp, bottom = 7.dp), color = InboxSecondary, fontSize = 13.sp)

@Composable private fun SettingsFootnote(text: String) =
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp), color = InboxSecondary, fontSize = 12.sp, lineHeight = 17.sp)

@Composable private fun SettingsGroupBox(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White), content = content)

@Composable private fun SettingsRowDivider() =
    HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = Color(0xFFC6C6C8))

@Composable private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp)
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = InboxGreen))
    }
