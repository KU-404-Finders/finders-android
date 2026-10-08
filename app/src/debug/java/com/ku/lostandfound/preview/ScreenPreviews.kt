package com.ku.lostandfound.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.ku.lostandfound.ui.post.screen.PostWriteScreen
import com.ku.lostandfound.ui.profile.screen.ProfileScreen
import com.ku.lostandfound.ui.theme.KUfindersTheme
import com.ku.lostandfound.viewmodel.PostWriteViewModel

@Preview(name = "글 작성", widthDp = 412, heightDp = 892, locale = "ko", showBackground = true)
@Composable
private fun PostWritePreview() {
    KUfindersTheme {
        PostWriteScreen(
            viewModel = remember { PostWriteViewModel() },
            onBackClick = {},
            onAddLocationClick = {},
            onSubmitClick = {},
        )
    }
}

@Preview(name = "설정 · 유사도 표시", widthDp = 412, heightDp = 892, locale = "ko", showBackground = true)
@Composable
private fun ProfilePreview() {
    var showScores by remember { mutableStateOf(true) }
    KUfindersTheme {
        ProfileScreen(
            userName = "사용자",
            userEmail = "",
            myPosts = emptyList(),
            myLostPostCount = 0,
            myFoundPostCount = 0,
            onPostClick = {},
            onShowAllClick = {},
            onLogoutClick = {},
            onWithdrawClick = {},
            onWithdrawCompleteConfirm = {},
            onFoundTabClick = {},
            onLostTabClick = {},
            onAddClick = {},
            showSimilarityScores = showScores,
            onShowSimilarityScoresChange = { showScores = it },
        )
    }
}
