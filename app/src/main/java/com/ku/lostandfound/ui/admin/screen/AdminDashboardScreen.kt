package com.ku.lostandfound.ui.admin.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.network.AdminDashboard
import com.ku.lostandfound.ui.component.BackTitleBar

// iOS AdminDashboardView: four menu cards; counts appear once the dashboard has loaded.
@Composable
fun AdminDashboardScreen(
    dashboard: AdminDashboard?,
    errorMessage: String? = null,
    onBackClick: () -> Unit,
    onReportManagementClick: () -> Unit = {},
    onUserManagementClick: () -> Unit = {},
    onPostManagementClick: () -> Unit = {},
    onAuditLogClick: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(AdminColor.ScreenGray)) {
        BackTitleBar(title = "관리자 페이지", onBackClick = onBackClick)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("관리 메뉴", color = AdminColor.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() })
            Text("서비스 운영 및 신고 내역을 관리할 수 있습니다.", color = AdminColor.TextGray, fontSize = 13.sp)
            if (errorMessage != null) Text(errorMessage, color = AdminColor.Red, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            AdminMenuCard("신고 관리", "접수된 게시글 및 사용자 신고를 확인합니다.",
                dashboard?.let { "미처리 ${it.pendingReportCount}건" }, onReportManagementClick)
            AdminMenuCard("사용자 관리", "사용자 정보와 이용 제한 상태를 확인합니다.",
                dashboard?.let { "회원 ${it.userCount}명" }, onUserManagementClick)
            AdminMenuCard("게시글 관리", "등록된 분실물·습득물 게시글을 관리합니다.",
                dashboard?.let { "분실 ${it.lostPostCount} · 습득 ${it.foundPostCount}" }, onPostManagementClick)
            AdminMenuCard("관리자 작업 이력", "관리자 처리 및 삭제 작업의 이력을 확인합니다.", null, onAuditLogClick)
        }
    }
}

@Composable
private fun AdminMenuCard(title: String, description: String, countText: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .background(Color.White)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = AdminColor.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(description, color = AdminColor.TextGray, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (countText != null) {
            Text(countText, color = AdminColor.DeepGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFFC4C4C4), modifier = Modifier.size(20.dp))
        }
    }
}
