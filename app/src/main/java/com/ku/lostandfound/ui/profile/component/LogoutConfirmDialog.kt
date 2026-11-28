package com.ku.lostandfound.ui.profile.component

import androidx.compose.runtime.Composable
import com.ku.lostandfound.ui.component.FinderConfirmDialog

@Composable
fun LogoutConfirmDialog(
    onCancelClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    FinderConfirmDialog(
        title = "로그아웃 하시겠습니까?",
        message = "다시 이용하려면 로그인이 필요합니다.",
        confirmText = "로그아웃",
        onDismiss = onCancelClick,
        onConfirm = onLogoutClick,
    )
}
