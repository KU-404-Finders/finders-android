package com.ku.lostandfound.ui.chat.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

enum class ChatConfirmAction { Leave, Block }

@Composable
fun ChatActionConfirmDialog(action: ChatConfirmAction, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val isLeave = action == ChatConfirmAction.Leave
    val title = if (isLeave) "채팅방을 나갈까요?" else "사용자를 차단할까요?"
    val message = if (isLeave) "채팅 목록에서 사라지지만, 상대방이 새 메시지를 보내면 다시 표시될 수 있어요." else "차단하면 해당 사용자의 게시글과 채팅을 볼 수 없습니다."
    val confirm = if (isLeave) "나가기" else "차단"
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, tonalElevation = 1.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).background(Color(0xFFE7F3E9), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (isLeave) Icons.Default.ExitToApp else Icons.Default.Block, null, tint = Color(0xFF1B6425), modifier = Modifier.size(27.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF263B2B), textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(message, color = Color(0xFF647568), fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F2F0), contentColor = Color(0xFF263B2B)), modifier = Modifier.weight(1f)) { Text("취소") }
                    Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB54742), contentColor = Color.White), modifier = Modifier.weight(1f)) { Text(confirm) }
                }
            }
        }
    }
}
