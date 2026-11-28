package com.ku.lostandfound.ui.chat.screen

import androidx.compose.runtime.Composable
import com.ku.lostandfound.ui.component.FinderConfirmDialog
import com.ku.lostandfound.ui.component.FinderDangerAction

enum class ChatConfirmAction { Leave, Block }

// Copy from iOS ChatLeaveCopy and the chat block alert.
@Composable
fun ChatActionConfirmDialog(action: ChatConfirmAction, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val isLeave = action == ChatConfirmAction.Leave
    FinderConfirmDialog(
        title = if (isLeave) "채팅방을 나가시겠습니까?" else "이 사용자를 차단하시겠습니까?",
        message = if (isLeave) "내 채팅 목록에서만 사라지고 대화 내용은 지워지지 않습니다. 상대가 새 메시지를 보내면 다시 나타납니다."
        else "해당 사용자의 게시글이 보이지 않고 서로 채팅할 수 없게 됩니다. 마이페이지의 차단한 사용자에서 해제할 수 있습니다.",
        confirmText = if (isLeave) "나가기" else "차단하기",
        confirmColor = FinderDangerAction,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
