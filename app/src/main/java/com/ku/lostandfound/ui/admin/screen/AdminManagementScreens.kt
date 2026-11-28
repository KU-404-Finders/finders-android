package com.ku.lostandfound.ui.admin.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.data.PostStatus
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.network.AdminAudit
import com.ku.lostandfound.network.AdminPost
import com.ku.lostandfound.network.AdminUser
import com.ku.lostandfound.ui.chat.ChatTimeFormatter
import com.ku.lostandfound.ui.component.BackTitleBar
import com.ku.lostandfound.ui.component.PostStatusBadge
import com.ku.lostandfound.ui.component.PostTypeBadge

// Mirrors iOS AdminViews.swift: AdminUserListView, AdminUserDetailView, AdminPostListView, AdminAuditLogView.

private val AdminUser.isAdmin get() = role.equals("ADMIN", ignoreCase = true)
private val AdminUser.nameLabel get() = name.ifBlank { "이름 없음" }
private fun adminDate(raw: String?) = ChatTimeFormatter.formatFullDateTime(raw).ifBlank { "-" }

@Composable
fun AdminUserListScreen(
    users: List<AdminUser>,
    hasNext: Boolean,
    onMore: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onSearch: (String) -> Unit,
    onUserClick: (Long) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    AdminListLayout("사용자 관리", "회원 목록", "이름·이메일로 검색하고 이용 제한 상태를 바꿀 수 있습니다.", onBackClick) {
        item {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .border(1.dp, AdminColor.Line, RoundedCornerShape(10.dp))
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = AdminColor.TextGray, modifier = Modifier.size(20.dp))
                BasicTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = AdminColor.Ink),
                    cursorBrush = SolidColor(AdminColor.DeepGreen),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); onSearch(keyword.trim()) }),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (keyword.isEmpty()) Text("이름 또는 이메일", color = Color(0xFFB0B0B0), fontSize = 14.sp)
                            inner()
                        }
                    },
                )
                if (keyword.isNotEmpty()) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { keyword = ""; onSearch("") },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Cancel, "검색어 지우기", tint = Color(0xFFC4C4C4), modifier = Modifier.size(18.dp)) }
                }
            }
        }
        listState(errorMessage, isLoading, users.isEmpty(), "표시할 회원이 없습니다.") { onSearch(keyword.trim()) }
        items(users, key = { it.id }) { user ->
            AdminCard(Modifier.clickable(role = Role.Button) { onUserClick(user.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).background(if (user.isAdmin) AdminColor.DeepGreen else Color(0xFFECEFEC), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (user.isAdmin) Icons.Filled.Shield else Icons.Filled.Person, null,
                            tint = if (user.isAdmin) Color.White else AdminColor.TextGray, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(user.nameLabel, color = AdminColor.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (user.isAdmin) Text("관리자", color = AdminColor.DeepGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(user.email.ifBlank { "-" }, color = AdminColor.TextGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("가입 ${adminDate(user.createdAt)}", color = AdminColor.TextGray, fontSize = 11.sp)
                    }
                    AdminStatusText(!user.suspended, if (user.suspended) "이용 제한" else "정상")
                }
            }
        }
        adminPaging(hasNext, isLoading, users.size, errorMessage != null, onMore)
    }
}

@Composable
fun AdminUserDetailScreen(
    user: AdminUser?,
    onRetry: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onSuspensionChange: (suspended: Boolean, reason: String) -> Unit,
) {
    var confirming by remember(user?.id, user?.suspended) { mutableStateOf(false) }
    var reason by remember(user?.id, user?.suspended) { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(AdminColor.ScreenGray)) {
        BackTitleBar("회원 상세", onBackClick)
        when {
            user == null && isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdminColor.DeepGreen)
            }
            user == null -> AdminMessage(errorMessage ?: "사용자 정보를 찾을 수 없습니다.", onRetry)
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AdminCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.nameLabel, Modifier.weight(1f), color = AdminColor.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        AdminStatusText(!user.suspended, if (user.suspended) "이용 제한" else "정상", fontSizeSp = 13)
                    }
                    Box(Modifier.height(12.dp))
                    AdminDetailRow("이메일", user.email.ifBlank { "-" })
                    AdminDetailRow("권한", if (user.isAdmin) "관리자" else "일반 회원")
                    AdminDetailRow("가입일", adminDate(user.createdAt))
                    AdminDetailRow("회원 ID", user.id.toString())
                }
                if (user.suspended) {
                    AdminCard {
                        Text("이용 제한 정보", color = AdminColor.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp))
                        AdminDetailRow("사유", user.suspensionReason?.takeIf { it.isNotBlank() } ?: "기록 없음")
                        AdminDetailRow("제한 일시", adminDate(user.suspendedAt))
                    }
                }
                errorMessage?.let { Text(it, color = AdminColor.Red, fontSize = 12.sp) }
                if (user.isAdmin) {
                    Text("관리자 계정은 이용 제한할 수 없습니다.", color = AdminColor.TextGray, fontSize = 12.sp)
                } else {
                    AdminFilledButton(
                        text = if (user.suspended) "이용 제한 해제" else "이용 제한",
                        color = if (user.suspended) AdminColor.DeepGreen else AdminColor.Red,
                        enabled = !isLoading,
                        modifier = Modifier.padding(top = 12.dp),
                    ) { reason = ""; confirming = true }
                }
            }
        }
    }

    if (confirming && user != null) {
        val suspend = !user.suspended
        AdminAlert(
            title = if (suspend) "이용 제한" else "이용 제한 해제",
            message = if (suspend) "이 회원의 글쓰기·채팅을 막습니다. 계속하시겠습니까?" else "이 회원의 이용 제한을 해제하시겠습니까?",
            confirmText = "확인",
            confirmColor = if (suspend) AdminColor.Red else AdminColor.DeepGreen,
            onDismiss = { confirming = false },
            onConfirm = { confirming = false; onSuspensionChange(suspend, reason) },
            working = isLoading,
            note = reason,
            onNoteChange = { reason = it },
            notePlaceholder = "사유 (선택, 1000자 이하)",
        )
    }
}

@Composable
fun AdminPostListScreen(
    posts: List<AdminPost>,
    hasNext: Boolean,
    onMore: () -> Unit,
    postType: String,
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onTypeChange: (String) -> Unit,
    onDelete: (AdminPost) -> Unit,
    isDeleting: Boolean = false,
    onRetry: () -> Unit = {},
) {
    var selectedForDeletion by remember { mutableStateOf<AdminPost?>(null) }
    AdminListLayout("게시글 관리", "게시글 목록", "해결된 글을 포함한 전체 게시글입니다. 삭제는 되돌릴 수 없습니다.", onBackClick) {
        item {
            // iOS segmented Picker over ItemType.allCases (분실물, 습득물).
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFE9E9EB), RoundedCornerShape(9.dp)).padding(2.dp).selectableGroup(),
            ) {
                listOf("LOST" to "분실물", "FOUND" to "습득물").forEach { (type, label) ->
                    val selected = postType == type
                    Box(
                        Modifier.weight(1f).heightIn(min = 36.dp).clip(RoundedCornerShape(7.dp))
                            .background(if (selected) Color.White else Color.Transparent)
                            .selectable(selected = selected, role = Role.Tab) { if (!selected) onTypeChange(type) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = AdminColor.Ink, fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
        listState(errorMessage, isLoading, posts.isEmpty(), "표시할 게시글이 없습니다.", onRetry)
        items(posts, key = { "${it.postType}-${it.id}" }) { post ->
            val type = runCatching { PostType.valueOf(post.postType.uppercase()) }.getOrNull()
                ?: runCatching { PostType.valueOf(postType) }.getOrDefault(PostType.FOUND)
            AdminCard {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PostTypeBadge(type)
                            PostStatusBadge(if (post.itemStatus.equals("RETURNED", true)) PostStatus.RESOLVED else PostStatus.OPEN)
                        }
                        Text(post.title.ifBlank { "제목 없음" }, color = AdminColor.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("작성자 ${post.userName.ifBlank { "탈퇴한 사용자" }} · ${adminDate(post.createdAt)}",
                            color = AdminColor.TextGray, fontSize = 11.sp)
                    }
                    Box(
                        Modifier.padding(start = 12.dp).heightIn(min = 48.dp).clip(CircleShape)
                            .clickable(enabled = !isDeleting, role = Role.Button) { selectedForDeletion = post },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.height(32.dp).border(1.dp, AdminColor.Red.copy(alpha = 0.5f), CircleShape).padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("삭제", color = AdminColor.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        adminPaging(hasNext, isLoading, posts.size, errorMessage != null, onMore)
    }
    selectedForDeletion?.let { post ->
        AdminAlert(
            title = "게시글 삭제",
            message = "'${post.title.ifBlank { "제목 없음" }}' 글을 삭제합니다. 댓글·매칭도 함께 정리되며 되돌릴 수 없습니다.",
            confirmText = "삭제",
            confirmColor = AdminColor.Red,
            onDismiss = { selectedForDeletion = null },
            onConfirm = { selectedForDeletion = null; onDelete(post) },
            working = isDeleting,
        )
    }
}

@Composable
fun AdminAuditLogScreen(hasNext: Boolean, onMore: () -> Unit, onRetry: () -> Unit, audits: List<AdminAudit>, isLoading: Boolean, errorMessage: String?, onBackClick: () -> Unit) {
    AdminListLayout("관리자 작업 이력", "작업 이력", "신고 처리, 이용 제한, 게시글 삭제 기록입니다.", onBackClick) {
        listState(errorMessage, isLoading, audits.isEmpty(), "작업 이력이 없습니다.", onRetry)
        items(audits, key = { it.id }) { audit ->
            AdminCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(adminActionLabel(audit.action).orEmpty(), Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (audit.action == "DELETE_POST" || audit.action == "SUSPEND_USER") AdminColor.Red else AdminColor.Ink)
                    Text(adminDate(audit.createdAt), color = AdminColor.TextGray, fontSize = 11.sp)
                }
                Text("${auditTarget(audit)} · 관리자 #${audit.adminId}", color = AdminColor.TextGray, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp))
                audit.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color(0xFF444444), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        adminPaging(hasNext, isLoading, audits.size, errorMessage != null, onMore)
    }
}

private fun auditTarget(audit: AdminAudit): String {
    val kind = when (audit.targetType.uppercase()) {
        "REPORT" -> "신고"
        "USER" -> "회원"
        "POST" -> when (audit.postType?.uppercase()) { "LOST" -> "분실물 게시글"; "FOUND" -> "습득물 게시글"; else -> "게시글" }
        else -> audit.targetType
    }
    return "$kind #${audit.targetId}"
}

/** Error/empty rows above a list, as in iOS (full message only while the list is empty). */
private fun LazyListScope.listState(error: String?, loading: Boolean, empty: Boolean, emptyText: String, retry: () -> Unit) {
    when {
        error != null && empty -> item { AdminMessage(error, retry, AdminColor.Red) }
        error != null -> item { Text(error, color = AdminColor.Red, fontSize = 12.sp) }
        !loading && empty -> item { AdminMessage(emptyText) }
    }
}

@Composable
internal fun AdminFilledButton(text: String, color: Color, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(10.dp))
            .background(if (enabled) color else color.copy(alpha = 0.38f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun AdminListLayout(
    barTitle: String,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(AdminColor.ScreenGray)) {
        BackTitleBar(barTitle, onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AdminHeader(title, subtitle) }
            content()
        }
    }
}
