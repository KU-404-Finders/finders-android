package com.ku.lostandfound.ui.profile.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.reminder.LeaveReminderController
import com.ku.lostandfound.ui.component.HomeTopBar
import com.ku.lostandfound.ui.profile.component.LogoutConfirmDialog
import com.ku.lostandfound.ui.profile.component.MyWrittenPostSection
import com.ku.lostandfound.ui.profile.component.WithdrawCompleteDialog
import com.ku.lostandfound.ui.profile.component.WithdrawConfirmDialog
import com.ku.lostandfound.ui.profile.viewmodel.MyPostsUiState
import com.ku.lostandfound.ui.signup.screen.LegalLinks

private val DeepGreen = Color(0xFF1B6425)
private val SettingsCanvas = Color(0xFFF2F2F7)
private val SettingsSecondary = Color(0xFF8E8E93)
private val LightGreen = Color(0xFFCFF6D6)

@Composable
fun ProfileScreen(
    userName: String,
    userEmail: String,
    myPosts: List<BoardPost>,
    myPostCount: Int = myPosts.size,
    myLostPostCount: Int? = null,
    myFoundPostCount: Int? = null,
    myPostCountState: MyPostsUiState = MyPostsUiState.Idle,
    onPostClick: (BoardPost) -> Unit,
    onShowAllClick: () -> Unit,
    onMyLostPostsClick: () -> Unit = {},
    onMyFoundPostsClick: () -> Unit = {},
    onLogoutClick: () -> Unit,
    onWithdrawClick: (onSuccess: () -> Unit) -> Unit,
    onWithdrawCompleteConfirm: () -> Unit,
    isWithdrawing: Boolean = false,
    withdrawalError: String? = null,
    onHomeClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    selectedType: PostType = PostType.FOUND,
    onFoundTabClick: () -> Unit,
    onLostTabClick: () -> Unit,
    onAddClick: () -> Unit,
    showAdminMenu: Boolean = false,
    onAdminClick: () -> Unit = {},
    showSimilarityScores: Boolean = false,
    onShowSimilarityScoresChange: (Boolean) -> Unit = {},
    onNotificationSettingsClick: () -> Unit = {},
    onBlockedUsersClick: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    onLeaveReminderClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    notificationCount: Int = 0,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val leaveReminder = remember(context) { LeaveReminderController.get(context) }
    val reminderSettings by leaveReminder.settings.collectAsState()
    val appVersion = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    var showWithdrawConfirmDialog by remember { mutableStateOf(false) }
    var showWithdrawCompleteDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsCanvas),
    ) {
        HomeTopBar(
            title = "마이페이지",
            onProfileClick = onHomeClick,
            onSearchClick = onSearchClick,
            onNotificationClick = onNotificationClick,
            notificationCount = notificationCount,
            leadingIcon = Icons.Outlined.Home,
            leadingDescription = "습득물 게시판으로 이동",
            leadingIconSize = 27.dp,
        )

        // iOS ProfileView: account row and post counts on top, then Settings-style inset groups.
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SettingsGroup { ProfileRow(userName, userEmail) }
            }

            item {
                SettingsHeader("내가 작성한 게시물")
                MyWrittenPostSection(
                    lostCount = myLostPostCount,
                    foundCount = myFoundPostCount,
                    isLoading = myPostCountState == MyPostsUiState.Loading,
                    onLostClick = onMyLostPostsClick,
                    onFoundClick = onMyFoundPostsClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (myPostCountState is MyPostsUiState.Error) {
                item {
                    SettingsGroup {
                        Text(myPostCountState.message, Modifier.padding(16.dp), color = Color(0xFFB3261E), fontSize = 14.sp)
                    }
                }
            }

            item {
                SettingsHeader("알림")
                SettingsGroup {
                    SettingsRow(Icons.Filled.NotificationsActive, Color(0xFFFF3B30), "알림 설정", onClick = onNotificationSettingsClick)
                    SettingsDivider()
                    SettingsRow(
                        Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFFFF9500), "분실물 예방 알림",
                        value = if (reminderSettings.enabled) "켬" else "끔", onClick = onLeaveReminderClick,
                    )
                }
            }

            item {
                SettingsHeader("표시")
                SettingsGroup {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = showSimilarityScores, role = Role.Switch, onValueChange = onShowSimilarityScoresChange)
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsIcon(Icons.Filled.Percent, Color(0xFF34C759))
                        Text("유사도 표시 모드", Modifier.weight(1f), fontSize = 16.sp, color = Color.Black, maxLines = 1)
                        Switch(checked = showSimilarityScores, onCheckedChange = null,
                            colors = SwitchDefaults.colors(checkedTrackColor = DeepGreen))
                    }
                }
                SettingsFooter("추천 글에 위치·텍스트·이미지·종합 점수를 표시합니다. 점수가 없는 항목은 —로 표시합니다.")
            }

            item {
                SettingsHeader("개인정보 보호")
                SettingsGroup {
                    SettingsRow(Icons.Filled.PanTool, SettingsSecondary, "차단한 사용자", onClick = onBlockedUsersClick)
                }
            }

            if (showAdminMenu) {
                item {
                    SettingsHeader("관리")
                    SettingsGroup {
                        SettingsRow(Icons.Filled.Shield, DeepGreen, "관리자 페이지", onClick = onAdminClick)
                    }
                    SettingsFooter("신고, 사용자 및 게시글을 관리합니다.")
                }
            }

            item {
                SettingsHeader("지원")
                SettingsGroup {
                    val open = { url: String ->
                        runCatching { uriHandler.openUri(url) }.onFailure {
                            Toast.makeText(context, "링크를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                        Unit
                    }
                    SettingsRow(Icons.Filled.Lock, Color(0xFF007AFF), "개인정보 처리방침", chevron = false) { open(LegalLinks.PRIVACY) }
                    SettingsDivider()
                    SettingsRow(Icons.Filled.Description, Color(0xFF5856D6), "이용약관", chevron = false) { open(LegalLinks.TERMS) }
                    SettingsDivider()
                    SettingsRow(Icons.Filled.Email, Color(0xFF34C759), "문의하기", chevron = false) { showSupportDialog = true }
                }
            }

            item {
                SettingsGroup {
                    CenteredAction("로그아웃", DeepGreen) { showLogoutConfirmDialog = true }
                }
            }

            item {
                SettingsGroup {
                    CenteredAction(if (isWithdrawing) "처리 중..." else "탈퇴하기", Color(0xFFFF3B30), enabled = !isWithdrawing) {
                        showWithdrawConfirmDialog = true
                    }
                }
                if (withdrawalError != null) {
                    Text(withdrawalError, Modifier.padding(start = 16.dp, top = 8.dp), color = Color(0xFFB3261E), fontSize = 13.sp)
                }
                SettingsFooter(WITHDRAWAL_FOOTER)
                Text(
                    text = "앱 버전 $appVersion",
                    color = SettingsSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                )
            }
        }
    }

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            title = { Text("문의하기") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("서비스 이용 중 불편한 사항이나 오류를 알려주세요.")
                    Text(SupportContact.EMAIL)
                    TextButton(onClick = {
                        if (!SupportContact.openEmail(context)) {
                            Toast.makeText(context, "이메일 앱을 열 수 없습니다. 주소를 복사해 문의해주세요.", Toast.LENGTH_LONG).show()
                        }
                    }) { Text("이메일 보내기") }
                    TextButton(onClick = {
                        val copied = SupportContact.copyEmail(context)
                        SupportContact.copyFeedback(copied, Build.VERSION.SDK_INT)?.let {
                            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("이메일 주소 복사") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSupportDialog = false }) { Text("닫기") }
            },
        )
    }

    if (showLogoutConfirmDialog) {
        LogoutConfirmDialog(
            onCancelClick = {
                showLogoutConfirmDialog = false
            },
            onLogoutClick = {
                showLogoutConfirmDialog = false
                onLogoutClick()
            }
        )
    }

    if (showWithdrawConfirmDialog) {
        WithdrawConfirmDialog(
            onCancelClick = {
                showWithdrawConfirmDialog = false
            },
            onWithdrawClick = {
                showWithdrawConfirmDialog = false
                onWithdrawClick {
                    showWithdrawCompleteDialog = true
                }
            }
        )
    }

    if (showWithdrawCompleteDialog) {
        WithdrawCompleteDialog(
            onConfirmClick = {
                showWithdrawCompleteDialog = false
                onWithdrawCompleteConfirm()
            }
        )
    }
}

internal object SupportContact {
    const val EMAIL = "michelle03212@gmail.com"

    // Android 13+ provides its own clipboard confirmation overlay.
    fun copyFeedback(copied: Boolean, sdkInt: Int): String? = when {
        !copied -> "이메일 주소를 복사할 수 없습니다."
        sdkInt < Build.VERSION_CODES.TIRAMISU -> "이메일 주소를 복사했습니다."
        else -> null
    }

    fun openEmail(context: Context): Boolean = try {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: RuntimeException) {
        // No mail app, managed-profile restrictions, or another launch failure.
        false
    }

    fun copyEmail(context: Context): Boolean = try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) false else {
            clipboard.setPrimaryClip(ClipData.newPlainText("문의 이메일", EMAIL))
            true
        }
    } catch (_: RuntimeException) {
        false
    }
}

private const val WITHDRAWAL_FOOTER =
    "계정과 작성한 게시글·댓글이 삭제됩니다.\n채팅·신고 기록은 탈퇴한 사용자로 남으며,\n같은 이메일로 다시 가입할 수 있습니다."

@Composable
private fun SettingsHeader(text: String) {
    Text(text, Modifier.padding(start = 16.dp, bottom = 7.dp).semantics { heading() }, color = SettingsSecondary, fontSize = 13.sp)
}

@Composable
private fun SettingsFooter(text: String) {
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp), color = SettingsSecondary, fontSize = 12.sp, lineHeight = 17.sp)
}

/** iOS inset-grouped section: white, 10dp corners. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White), content = content)
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = Color(0xFFC6C6C8))
}

/** White symbol on a rounded colour tile, like the iOS Settings icons. */
@Composable
private fun SettingsIcon(icon: ImageVector, color: Color) {
    Box(Modifier.size(30.dp).background(color, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    color: Color,
    title: String,
    value: String? = null,
    chevron: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsIcon(icon, color)
        Text(title, Modifier.weight(1f), fontSize = 16.sp, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        value?.let { Text(it, color = SettingsSecondary, fontSize = 15.sp, maxLines = 1) }
        if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFC7C7CC))
    }
}

@Composable
private fun CenteredAction(text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).heightIn(min = 52.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontSize = 16.sp) }
}

/** Like the account row at the top of the Settings app: initial, name, e-mail. */
@Composable
private fun ProfileRow(userName: String, userEmail: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(LightGreen), contentAlignment = Alignment.Center) {
            Text(userName.take(1), color = DeepGreen, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(userName, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(userEmail, fontSize = 13.sp, color = SettingsSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
