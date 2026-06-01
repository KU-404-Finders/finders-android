package com.ku.lostandfound.ui.profile.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        Text(
            text = "내가 작성한 게시물",
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 136.dp),
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

@Preview(showBackground = true)
@Composable
private fun MyWrittenPostSectionPreview() {
    MyWrittenPostSection(
        lostCount = 2,
        foundCount = 2,
        onLostClick = {
            // 내가 작성한 분실물 목록 화면으로 이동
        },
        onFoundClick = {
            // 내가 작성한 습득물 목록 화면으로 이동
        }
    )
}
