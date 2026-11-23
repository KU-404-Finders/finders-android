package com.ku.lostandfound.ui.profile.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ku.lostandfound.ui.component.BackTitleBar
import com.ku.lostandfound.ui.profile.viewmodel.BlockedUserRow
import com.ku.lostandfound.ui.profile.viewmodel.BlockedUsersViewModel

@Composable
fun BlockedUsersScreen(vm: BlockedUsersViewModel, accountId: Long?, onBack: () -> Unit, onUnblocked: () -> Unit) {
    LaunchedEffect(accountId) { vm.attachAccount(accountId); vm.load() }
    var confirmation by remember(accountId) { mutableStateOf<BlockedUserRow?>(null) }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        BackTitleBar(title = "차단한 사용자", onBackClick = onBack)
        vm.error?.let { message ->
            Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = vm::load, enabled = !vm.loading) { Text("다시 시도") }
        }
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (vm.users.isEmpty() && !vm.loading && vm.error == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (accountId == null) "계정 정보를 기다리고 있습니다." else "차단한 사용자가 없습니다.", color = Color.Gray)
            }
        } else LazyColumn(Modifier.weight(1f)) {
            items(vm.users, key = { it.id }) { user ->
                ListItem(
                    headlineContent = { Text(user.name) },
                    trailingContent = {
                        TextButton(onClick = { confirmation = user }, enabled = user.id !in vm.unblocking) {
                            Text(if (user.id in vm.unblocking) "해제 중…" else "차단 해제")
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
    confirmation?.let { user ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("차단을 해제할까요?") },
            text = { Text("${user.name}님의 차단을 해제합니다.") },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                vm.unblock(user.id, onUnblocked)
            }) { Text("차단 해제") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("취소") } },
        )
    }
}
