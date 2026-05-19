package com.ku.lostandfound.ui.profile.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.ui.component.BackTitleBar
import com.ku.lostandfound.ui.component.CompactPostCard

@Composable
fun MyPostsScreen(
    posts: List<BoardPost>,
    onBackClick: () -> Unit,
    onPostClick: (BoardPost) -> Unit,
    onToggleResolvedClick: (BoardPost) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        BackTitleBar(
            title = "나의 등록 내역",
            onBackClick = onBackClick,
        )

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(posts) { post ->
                CompactPostCard(
                    post = post,
                    showStatus = true,
                    showResolveButton = true,
                    onClick = { onPostClick(post) },
                    onToggleResolved = { onToggleResolvedClick(post) },
                )
            }
        }
    }
}
