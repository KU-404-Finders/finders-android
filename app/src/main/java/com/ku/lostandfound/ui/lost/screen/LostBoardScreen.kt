package com.ku.lostandfound.ui.lost.screen

import androidx.compose.runtime.Composable
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.ui.board.screen.HomeScreen

@Composable
fun LostBoardScreen(
    posts: List<BoardPost>,
    onPostClick: (BoardPost) -> Unit,
    onAddClick: () -> Unit,
    onFoundTabClick: () -> Unit,
    onLostTabClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit = {},
) {
    HomeScreen(
        title = "분실물 게시판",
        selectedType = PostType.LOST,
        posts = posts.filter { it.type == PostType.LOST },
        boundary = null,
        showCampusMap = false,
        onPostClick = onPostClick,
        onAddClick = onAddClick,
        onFoundTabClick = onFoundTabClick,
        onLostTabClick = onLostTabClick,
        onProfileClick = onProfileClick,
        onSearchClick = onSearchClick,
    )
}
