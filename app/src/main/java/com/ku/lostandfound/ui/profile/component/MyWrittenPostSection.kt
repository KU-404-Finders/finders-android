package com.ku.lostandfound.ui.profile.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MyWrittenPostSection(
    lostCount: Int?,
    foundCount: Int?,
    isLoading: Boolean = false,
    onLostClick: () -> Unit,
    onFoundClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // The "내가 작성한 게시물" heading is drawn by the screen's Settings-style section header.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MyWrittenPostCard(
                title = "물건 찾기",
                count = lostCount,
                isLoading = isLoading,
                type = WrittenPostType.LOST,
                onClick = onLostClick,
                modifier = Modifier.weight(1f)
            )
            MyWrittenPostCard(
                title = "습득 신고",
                count = foundCount,
                isLoading = isLoading,
                type = WrittenPostType.FOUND,
                onClick = onFoundClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
