package com.ku.lostandfound.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.R

private val TopBarBackground = Color(0xFFF4F4F4)
private val DeepGreen = Color(0xFF1B6425)

@Composable
fun BackTitleBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onChatClick: (() -> Unit)? = null,
    onReportClick: (() -> Unit)? = null,
    onBlockClick: (() -> Unit)? = null,
) {
    var menuExpanded by remember {
        mutableStateOf(false)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(TopBarBackground)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_left_arrow),
            contentDescription = "뒤로가기",
            modifier = Modifier
                .size(24.dp)
                .noRippleClickable(onBackClick)
        )

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1E1E)
        )

        Spacer(
            modifier = Modifier.weight(1f)
        )

        onChatClick?.let { chatClick ->
            Image(
                painter = painterResource(id = R.drawable.img_chat),
                contentDescription = "채팅",
                modifier = Modifier
                    .size(32.dp)
                    .noRippleClickable(chatClick)
            )

            Spacer(
                modifier = Modifier.size(8.dp)
            )
        }

        if (onReportClick != null || onBlockClick != null) {
            Box {
                Text(
                    text = "⋮",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .noRippleClickable {
                            menuExpanded = true
                        }
                )

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = {
                        menuExpanded = false
                    },
                    containerColor = Color.White,
                ) {
                    if (onReportClick != null) {
                        DropdownMenuItem(
                            text = {
                                Text("게시글 신고")
                            },
                            onClick = {
                                menuExpanded = false
                                onReportClick()
                            }
                        )
                    }

                    if (onBlockClick != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "사용자 차단",
                                    color = Color(0xFFD32F2F)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onBlockClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeTopBar(
    title: String = "습득물 캠퍼스맵",
    onProfileClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(DeepGreen)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .noRippleClickable(onProfileClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = "마이페이지",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(34.dp),
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .noRippleClickable(onSearchClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "⌕",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 36.sp,
            )
        }
    }
}
