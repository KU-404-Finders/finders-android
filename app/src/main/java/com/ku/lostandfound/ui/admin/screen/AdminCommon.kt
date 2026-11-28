package com.ku.lostandfound.ui.admin.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// iOS AdminViews.swift AdminColor.
internal object AdminColor {
    val DeepGreen = Color(0xFF1B6425)
    val ScreenGray = Color(0xFFF5F5F5)
    val TextGray = Color(0xFF888888)
    val Ink = Color(0xFF222222)
    val Red = Color(0xFFD32F2F)
    val Line = Color(0xFFE4E4E4)
    val Body = Color(0xFF333333)
}

/** iOS AdminCard: white, 12dp corners, hairline shadow, 16dp padding. */
@Composable
internal fun AdminCard(modifier: Modifier = Modifier, radius: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(1.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .background(Color.White, shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
internal fun AdminHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = AdminColor.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Text(subtitle, color = AdminColor.TextGray, fontSize = 13.sp)
    }
}

@Composable
internal fun AdminStatusText(processed: Boolean, label: String? = null, fontSizeSp: Int = 12) {
    Text(
        label ?: if (processed) "처리 완료" else "미처리",
        color = if (processed) AdminColor.DeepGreen else AdminColor.Red,
        fontSize = fontSizeSp.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** iOS AdminMessage: centred grey text with an optional 다시 시도 link. */
@Composable
internal fun AdminMessage(text: String, retry: (() -> Unit)? = null, color: Color = AdminColor.TextGray) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, color = color, fontSize = 13.sp, textAlign = TextAlign.Center)
        retry?.let { TextButton(onClick = it) { Text("다시 시도", color = AdminColor.DeepGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold) } }
    }
}

@Composable
internal fun AdminDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, Modifier.width(90.dp), color = AdminColor.TextGray, fontSize = 13.sp)
        Text(value, Modifier.weight(1f), color = AdminColor.Body, fontSize = 13.sp)
    }
}

/** Spinner plus the "load the next page when the end appears" trigger used by every admin list. */
internal fun LazyListScope.adminPaging(hasNext: Boolean, loading: Boolean, size: Int, onMore: () -> Unit) {
    if (hasNext && !loading) item(key = "admin-more") { LaunchedEffect(size) { onMore() } }
    if (loading) item(key = "admin-loading") {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), color = AdminColor.DeepGreen, strokeWidth = 2.dp)
        }
    }
}

/** iOS AndroidAlertCard: Material alert with an optional memo field and text actions. */
@Composable
internal fun AdminAlert(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    working: Boolean = false,
    note: String? = null,
    onNoteChange: (String) -> Unit = {},
    notePlaceholder: String = "",
) {
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(title, color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(message, color = Color(0xFF555555), fontSize = 14.sp, lineHeight = 21.sp)
                if (note != null) {
                    TextField(
                        value = note,
                        onValueChange = { if (it.length <= 1000) onNoteChange(it) },
                        placeholder = { Text(notePlaceholder, fontSize = 13.sp) },
                        maxLines = 4,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = AdminColor.ScreenGray, unfocusedContainerColor = AdminColor.ScreenGray,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = AdminColor.DeepGreen,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !working) {
                Text(if (working) "처리 중…" else confirmText, color = confirmColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text("취소", color = AdminColor.TextGray, fontSize = 14.sp) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
    )
}

internal fun adminActionLabel(action: String?): String? = when (action) {
    null -> null
    "DISMISS_REPORT" -> "신고 반려"
    "DELETE_POST" -> "게시글 삭제"
    "SUSPEND_USER" -> "사용자 이용 제한"
    "RESTORE_USER" -> "이용 제한 해제"
    else -> action
}
