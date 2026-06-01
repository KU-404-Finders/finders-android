package com.ku.lostandfound.ui.profile.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MyWrittenPostCard(
    title: String,
    count: Int?,
    isLoading: Boolean = false,
    type: WrittenPostType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = when (type) {
        WrittenPostType.LOST -> Color(0xFFB3261E)
        WrittenPostType.FOUND -> Color(0xFF355F2E)
    }
    val countText = if (isLoading && count == null) "..." else (count ?: 0).toString()
    val countFontSize = when {
        countText.length <= 2 -> 30.sp
        countText.length <= 4 -> 27.sp
        else -> 24.sp
    }

    Card(
        modifier = modifier
            .heightIn(min = 136.dp)
            .wrapContentHeight()
            .clickable { onClick() }
        ,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(
            width = 1.5.dp,
            color = Color(0xFFE0E0E0)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 136.dp)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                PostTypeIcon(
                    type = type,
                    color = iconColor
                )

                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8C8C8C),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = countText,
                            fontSize = countFontSize,
                            lineHeight = (countFontSize.value + 4).sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 1,
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "건",
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                }
            }

    }
}

@Preview(showBackground = true)
@Composable
private fun MyWrittenPostCardPreview() {
    MyWrittenPostCard(
        title = "물건 찾기",
        count = 2,
        type= WrittenPostType.FOUND,
        onClick = {}
    )
}
