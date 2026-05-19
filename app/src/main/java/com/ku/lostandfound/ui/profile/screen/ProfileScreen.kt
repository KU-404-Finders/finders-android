package com.ku.lostandfound.ui.profile.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.ui.component.CompactPostCard

private val DeepGreen = Color(0xFF1B6425)
private val ScreenGray = Color(0xFFF4F4F4)
private val LightGreen = Color(0xFFCFF6D6)

@Composable
fun ProfileScreen(
    userName: String,
    userEmail: String,
    myPosts: List<BoardPost>,
    onPostClick: (BoardPost) -> Unit,
    onShowAllClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onSearchClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenGray),
    ) {
        ProfileTopBar(onSearchClick = onSearchClick)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ProfileCard(
                    userName = userName,
                    userEmail = userEmail,
                    onLogoutClick = onLogoutClick,
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("나의 등록 내역", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(8.dp))
                    Text(myPosts.size.toString(), color = DeepGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "전체보기 〉",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White)
                            .clickable(onClick = onShowAllClick)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            val previewPosts = myPosts.take(3)
            items(previewPosts) { post ->
                CompactPostCard(
                    post = post,
                    showThumbnail = true,
                    showStatus = true,
                    onClick = { onPostClick(post) },
                )
            }

            item {
                Spacer(Modifier.height(58.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "탈퇴하기",
                        color = Color(0xFF888888),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onWithdrawClick),
                    )
                }
            }
        }

    }
}

@Composable
private fun ProfileTopBar(onSearchClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "숲길대",
            color = DeepGreen,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "⌕",
            color = Color(0xFF777777),
            fontSize = 34.sp,
            modifier = Modifier.clickable(onClick = onSearchClick),
        )
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
                    .clickable(onClick = onLogoutClick),
                contentAlignment = Alignment.Center,
            ) {
                Text("로그아웃", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
