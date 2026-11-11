package com.ku.lostandfound.ui.admin.screen

import androidx.compose.ui.Alignment
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
private val ManagementRed = Color(0xFFC43C3C)
private val ManagementGray = Color(0xFF5E7063)
private val ManagementText = Color(0xFF202A22)

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("이름 또는 이메일") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ManagementGreen,
                        unfocusedBorderColor = Color(0xFFA9BDAD),
                        focusedLabelColor = ManagementGreen,
                        unfocusedLabelColor = ManagementGray,
                        cursorColor = ManagementGreen,
                        focusedTextColor = ManagementText,
                        unfocusedTextColor = ManagementText,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                )
                Button(
                    onClick = { onSearch(keyword.trim()) },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ManagementGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("검색", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (!isLoading && users.isEmpty()) {
            item { EmptyText("조회된 사용자가 없습니다.") }
        }
        items(users, key = { it.id }) { user ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onUserClick(user.id) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(user.name, color = ManagementText, fontWeight = FontWeight.Bold)
                    Text(user.email, color = ManagementGray, fontSize = 13.sp)
                    Text(
                        text = if (user.suspended) "이용 제한" else user.role,
                        color = if (user.suspended) ManagementRed else ManagementGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
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
        if (isLoading) {
            EmptyText("사용자 정보를 불러오는 중입니다.")
        } else if (errorMessage != null && user == null) {
            EmptyText(errorMessage, ManagementRed)
        } else if (user == null) {
            EmptyText("사용자 정보를 찾을 수 없습니다.")
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = ManagementRed,
                        fontSize = 14.sp,
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(user.name, color = ManagementText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Detail("이메일", user.email)
                        Detail("권한", user.role)
                        Detail("상태", if (user.suspended) "이용 제한" else "정상")
                        user.suspensionReason?.let { Detail("사유", it) }
                        Detail("가입일", ChatTimeFormatter.formatFullDateTime(user.createdAt))
                    }
                }

                if (user.role != "ADMIN") {
                    Button(
                        onClick = { showConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (user.suspended) ManagementGreen else ManagementRed,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(
                            text = if (user.suspended) "이용 제한 해제" else "이용 제한",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (showConfirm && user != null) {
        val isRelease = user.suspended
        val accent = if (isRelease) ManagementGreen else ManagementRed
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = {
                Text(
                    text = if (isRelease) "이용 제한 해제" else "이용 제한",
                    color = ManagementText,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = if (isRelease) {
                        "${user.name}님의 이용 제한을 해제하시겠습니까?"
                    } else {
                        "${user.name}님의 서비스 이용을 제한하시겠습니까?"
                    },
                    color = ManagementGray,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        onSuspensionChange(!user.suspended)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(if (isRelease) "해제하기" else "제한하기")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("취소", color = ManagementGray)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            titleContentColor = ManagementText,
            textContentColor = ManagementGray,
        )
    }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFFE7F2E9),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                listOf(
                    "FOUND" to "습득물",
                    "LOST" to "분실물"
                ).forEach { (type, label) ->

                    val isSelected = postType == type

                    Button(
                        onClick = {
                            if (!isSelected) {
                                onTypeChange(type)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) {
                                ManagementGreen
                            } else {
                                Color.Transparent
                            },
                            contentColor = if (isSelected) {
                                Color.White
                            } else {
                                ManagementGreen
                            }
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Medium
                            }
                        )
                    }
                }
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
    selectedForDeletion?.let { post ->
        AlertDialog(
            onDismissRequest = { selectedForDeletion = null },
            title = { Text("게시글 삭제", color = ManagementText, fontWeight = FontWeight.Bold) },
            text = { Text("삭제하면 이미지·댓글·매칭도 서버에서 정리됩니다.", color = ManagementGray) },
            confirmButton = {
                Button(
                    onClick = {
                        selectedForDeletion = null
                        onDelete(post)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ManagementRed, contentColor = Color.White),
                ) {
                    Text("삭제하기")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedForDeletion = null }) {
                    Text("취소", color = ManagementGray)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            titleContentColor = ManagementText,
            textContentColor = ManagementGray,
        )
    }


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
                    Text(ChatTimeFormatter.formatFullDateTime(audit.createdAt), fontSize = 12.sp, color = ManagementGray)
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
@Composable
private fun Detail(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$label:", modifier = Modifier.width(62.dp), color = ManagementGray, fontSize = 13.sp)
        Text(value, modifier = Modifier.weight(1f), color = ManagementText, fontSize = 13.sp)
    }
}
