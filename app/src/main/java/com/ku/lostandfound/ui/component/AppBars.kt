package com.ku.lostandfound.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.R

// iOS FinderNavBar: white bar, 0.5pt hairline, 20pt bold title, 44pt back target (48dp on Android).
private val TopBarBackground = Color.White
private val TopBarHairline = Color.Black.copy(alpha = 0.06f)
private val DeepGreen = Color(0xFF1B6425)

@Composable
fun BackTitleBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onChatClick: (() -> Unit)? = null,
    onReportClick: (() -> Unit)? = null,
    onBlockClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    var menuExpanded by remember {
        mutableStateOf(false)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(TopBarBackground)
            .drawHairline()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIconButton(onClick = onBackClick) {
            Image(
                painter = painterResource(id = R.drawable.img_left_arrow),
                contentDescription = stringResource(R.string.cd_back),
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.size(4.dp))

        Text(
            text = title,
            modifier = (if (actions != null) Modifier.weight(1f) else Modifier).semantics { heading() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1E1E)
        )

        if (actions == null) Spacer(Modifier.weight(1f))

        onChatClick?.let { chatClick ->
            BarIconButton(onClick = chatClick) {
                Image(
                    painter = painterResource(id = R.drawable.img_chat),
                    contentDescription = stringResource(R.string.cd_chat_with_author),
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        if (onReportClick != null || onBlockClick != null) {
            Box {
                val moreLabel = stringResource(R.string.cd_more)
                BarIconButton(onClick = { menuExpanded = true }) {
                    Text(
                        text = "⋮",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333),
                        modifier = Modifier.semantics { contentDescription = moreLabel },
                    )
                }

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
        actions?.invoke()
    }
}

/** 48dp circular touch target used by the bars (iOS FinderIconButton is 44pt). */
@Composable
private fun BarIconButton(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private fun Modifier.drawHairline(): Modifier = drawBehind {
    drawRect(TopBarHairline, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 0.5.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx()))
}

@Composable
fun HomeTopBar(
    title: String = "습득물 캠퍼스맵",
    onProfileClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    notificationCount: Int = 0,
    leadingIcon: ImageVector = Icons.Outlined.AccountCircle,
    leadingDescription: String = stringResource(R.string.cd_my_page),
    leadingIconSize: Dp = 34.dp,
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
                .size(48.dp)
                .noRippleClickable(onProfileClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = leadingDescription,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(leadingIconSize),
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier.size(48.dp).noRippleClickable(onNotificationClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = stringResource(R.string.cd_inbox),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            if (notificationCount > 0) Text(if (notificationCount > 99) "99+" else notificationCount.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).background(Color(0xFFE53935), CircleShape).padding(horizontal = 4.dp, vertical = 1.dp))
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .noRippleClickable(onSearchClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.cd_search),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
