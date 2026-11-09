package com.ku.lostandfound.ui.admin.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.network.AdminAudit
import com.ku.lostandfound.network.AdminPost
import com.ku.lostandfound.network.AdminUser
import com.ku.lostandfound.ui.component.BackTitleBar
import com.ku.lostandfound.ui.chat.ChatTimeFormatter

private val ManagementBackground = Color(0xFFF5F5F5)
private val ManagementGreen = Color(0xFF1B6425)
private val ManagementRed = Color(0xFFD32F2F)
private val ManagementGray = Color(0xFF777777)

@Composable
fun AdminUserListScreen(
    users: List<AdminUser>,
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onSearch: (String) -> Unit,
    onUserClick: (Long) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    AdminListLayout("사용자 관리", isLoading, errorMessage, onBackClick) {
        item {
            Row {
                OutlinedTextField(keyword, { keyword = it }, label = { Text("이름 또는 이메일") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSearch(keyword) }, modifier = Modifier.padding(top = 8.dp)) { Text("검색") }
            }
        }
        if (!isLoading && users.isEmpty()) item { EmptyText("조회된 사용자가 없습니다.") }
        items(users, key = { it.id }) { user ->
            Card(Modifier.fillMaxWidth().clickable { onUserClick(user.id) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Text(user.name, fontWeight = FontWeight.Bold)
                    Text(user.email, fontSize = 13.sp, color = ManagementGray)
                    Text(if (user.suspended) "이용 제한" else user.role, color = if (user.suspended) ManagementRed else ManagementGreen, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AdminUserDetailScreen(
    user: AdminUser?,
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onSuspensionChange: (Boolean) -> Unit,
) {
    var showConfirm by remember(user?.id, user?.suspended) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(ManagementBackground)) {
        BackTitleBar("사용자 상세", onBackClick)
        if (isLoading) EmptyText("사용자 정보를 불러오는 중입니다.")
        else if (errorMessage != null) EmptyText(errorMessage, ManagementRed)
        else if (user == null) EmptyText("사용자 정보를 찾을 수 없습니다.")
        else Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(user.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Detail("이메일", user.email)
            Detail("권한", user.role)
            Detail("상태", if (user.suspended) "이용 제한" else "정상")
            user.suspensionReason?.let { Detail("사유", it) }
            Detail("가입일", ChatTimeFormatter.formatListTime(user.createdAt))
            if (user.role != "ADMIN") {
                Button(
                    onClick = { showConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = if (user.suspended) ManagementGreen else ManagementRed),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (user.suspended) "이용 제한 해제" else "이용 제한") }
            }
        }
    }
    if (showConfirm && user != null) AlertDialog(
        onDismissRequest = { showConfirm = false },
        title = { Text(if (user.suspended) "이용 제한 해제" else "이용 제한") },
        text = { Text("서버에 사용자 상태 변경을 요청합니다.") },
        confirmButton = { TextButton(onClick = { showConfirm = false; onSuspensionChange(!user.suspended) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("취소") } },
    )
}

@Composable
fun AdminPostListScreen(
    posts: List<AdminPost>,
    postType: String,
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onTypeChange: (String) -> Unit,
    onDelete: (AdminPost) -> Unit,
) {
    var selectedForDeletion by remember { mutableStateOf<AdminPost?>(null) }
    AdminListLayout("게시글 관리", isLoading, errorMessage, onBackClick) {
        item {
            Row {
                TextButton(onClick = { onTypeChange("FOUND") }) { Text("습득물") }
                TextButton(onClick = { onTypeChange("LOST") }) { Text("분실물") }
                Text(if (postType == "FOUND") "습득물 목록" else "분실물 목록", modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (!isLoading && posts.isEmpty()) item { EmptyText("조회된 게시글이 없습니다.") }
        items(posts, key = { "${it.postType}-${it.id}" }) { post ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Text(post.title, fontWeight = FontWeight.Bold)
                    Text("작성자: ${post.userName} · ${post.itemStatus}", fontSize = 12.sp, color = ManagementGray)
                    Text(post.content, fontSize = 13.sp, maxLines = 2)
                    TextButton(onClick = { selectedForDeletion = post }) { Text("게시글 삭제", color = ManagementRed) }
                }
            }
        }
    }
    selectedForDeletion?.let { post -> AlertDialog(
        onDismissRequest = { selectedForDeletion = null }, title = { Text("게시글 삭제") },
        text = { Text("삭제하면 이미지·댓글·매칭도 서버에서 정리됩니다.") },
        confirmButton = { TextButton(onClick = { selectedForDeletion = null; onDelete(post) }) { Text("삭제", color = ManagementRed) } },
        dismissButton = { TextButton(onClick = { selectedForDeletion = null }) { Text("취소") } },
    ) }
}

@Composable
fun AdminAuditLogScreen(audits: List<AdminAudit>, isLoading: Boolean, errorMessage: String?, onBackClick: () -> Unit) {
    AdminListLayout("관리자 작업 이력", isLoading, errorMessage, onBackClick) {
        if (!isLoading && audits.isEmpty()) item { EmptyText("작업 이력이 없습니다.") }
        items(audits, key = { it.id }) { audit ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Text(audit.action, fontWeight = FontWeight.Bold)
                    Text("${audit.targetType} #${audit.targetId} · 관리자 #${audit.adminId}", fontSize = 12.sp, color = ManagementGray)
                    audit.note?.let { Text(it, fontSize = 13.sp) }
                    Text(ChatTimeFormatter.formatListTime(audit.createdAt), fontSize = 12.sp, color = ManagementGray)
                }
            }
        }
    }
}

@Composable
private fun AdminListLayout(title: String, loading: Boolean, error: String?, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(ManagementBackground)) {
        BackTitleBar(title, onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loading) item { EmptyText("불러오는 중입니다.") }
            if (error != null) item { EmptyText(error, ManagementRed) }
            content()
        }
    }
}

@Composable private fun EmptyText(text: String, color: Color = ManagementGray) = Text(text, color = color, modifier = Modifier.padding(8.dp))
@Composable private fun Detail(label: String, value: String) = Row { Text("$label: ", color = ManagementGray); Text(value) }
