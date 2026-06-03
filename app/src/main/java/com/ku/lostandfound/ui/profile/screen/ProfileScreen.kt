package com.ku.lostandfound.ui.profile.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.ui.component.noRippleClickable
import com.ku.lostandfound.ui.profile.component.LogoutConfirmDialog
import com.ku.lostandfound.ui.profile.component.MyWrittenPostSection
import com.ku.lostandfound.ui.profile.component.WithdrawCompleteDialog
import com.ku.lostandfound.ui.profile.component.WithdrawConfirmDialog
import com.ku.lostandfound.ui.profile.viewmodel.MyPostsUiState
import com.ku.lostandfound.ui.theme.KUfindersTheme

private val DeepGreen = Color(0xFF1B6425)
private val ScreenGray = Color(0xFFF4F4F4)
private val LightGreen = Color(0xFFCFF6D6)

@Composable
fun ProfileScreen(
    userName: String,
    userEmail: String,
    myPosts: List<BoardPost>,
    myPostCount: Int = myPosts.size,
    myLostPostCount: Int? = null,
    myFoundPostCount: Int? = null,
    myPostCountState: MyPostsUiState = MyPostsUiState.Idle,
    onPostClick: (BoardPost) -> Unit,
    onShowAllClick: () -> Unit,
    onMyLostPostsClick: () -> Unit = {},
    onMyFoundPostsClick: () -> Unit = {},
    onLogoutClick: () -> Unit,
    onWithdrawClick: (onSuccess: () -> Unit) -> Unit,
    onWithdrawCompleteConfirm: () -> Unit,
    onHomeClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    selectedType: PostType = PostType.FOUND,
    onFoundTabClick: () -> Unit,
    onLostTabClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    var showWithdrawConfirmDialog by remember { mutableStateOf(false) }
    var showWithdrawCompleteDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenGray),
    ) {
        ProfileTopBar(
            onHomeClick = onHomeClick,
            onSearchClick = onSearchClick,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ProfileCard(
                    userName = userName,
                    userEmail = userEmail,
                    onLogoutClick = {
                        showLogoutConfirmDialog = true
                    },
                )
            }

            item {
                MyWrittenPostSection(
                    lostCount = myLostPostCount,
                    foundCount = myFoundPostCount,
                    isLoading = myPostCountState == MyPostsUiState.Loading,
                    onLostClick = onMyLostPostsClick,
                    onFoundClick = onMyFoundPostsClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (myPostCountState is MyPostsUiState.Error) {
                item {
                    Text(
                        text = myPostCountState.message,
                        color = Color(0xFFB3261E),
                        fontSize = 13.sp,
                    )
                }
            }

            item {
                Spacer(Modifier.height(58.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "탈퇴하기",
                        color = Color(0xFF888888),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.noRippleClickable {
                            showWithdrawConfirmDialog = true
                        },
                    )
                }
            }
        }
    }

    if (showLogoutConfirmDialog) {
        LogoutConfirmDialog(
            onCancelClick = {
                showLogoutConfirmDialog = false
            },
            onLogoutClick = {
                showLogoutConfirmDialog = false
                onLogoutClick()
            }
        )
    }

    if (showWithdrawConfirmDialog) {
        WithdrawConfirmDialog(
            onCancelClick = {
                showWithdrawConfirmDialog = false
            },
            onWithdrawClick = {
                showWithdrawConfirmDialog = false
                onWithdrawClick {
                    showWithdrawCompleteDialog = true
                }
            }
        )
    }

    if (showWithdrawCompleteDialog) {
        WithdrawCompleteDialog(
            onConfirmClick = {
                showWithdrawCompleteDialog = false
                onWithdrawCompleteConfirm()
            }
        )
    }
}

@Composable
private fun ProfileTopBar(
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
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
                .noRippleClickable(onHomeClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = "홈으로 이동",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(27.dp),
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = "마이페이지",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
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

@Composable
private fun ProfileCard(
    userName: String,
    userEmail: String,
    onLogoutClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(LightGreen),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = userName.take(1),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(userName, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(Modifier.height(7.dp))
            Text(userEmail, fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(26.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DeepGreen)
                    .noRippleClickable(onLogoutClick),
                contentAlignment = Alignment.Center,
            ) {
                Text("로그아웃", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    KUfindersTheme {
        ProfileScreen(
            userName = "건국이",
            userEmail = "konkuk@konkuk.ac.kr",
            myPosts = listOf(
                BoardPost(
                    id = "1",
                    type = PostType.FOUND,
                    title = "에어팟 프로 분실하신 분",
                    category = "전자기기",
                    content = "공학관 201호에서 습득했습니다.",
                    authorName = "건국이",
                    authorEmail = "konkuk@konkuk.ac.kr",
                    createdAtText = "2024-03-22"
                ),
                BoardPost(
                    id = "2",
                    type = PostType.LOST,
                    title = "검은색 장우산 찾습니다",
                    category = "생활잡화",
                    content = "도서관 1층 열람실에서 잃어버렸습니다.",
                    authorName = "건국이",
                    authorEmail = "konkuk@konkuk.ac.kr",
                    createdAtText = "2024-03-21"
                )
            ),
            onPostClick = {},
            onShowAllClick = {},
            onLogoutClick = {},
            onWithdrawClick = {},
            onWithdrawCompleteConfirm = {},
            onFoundTabClick = {},
            onLostTabClick = {},
            onAddClick = {}
        )
    }
}
