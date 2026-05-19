package com.ku.lostandfound.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.ku.lostandfound.data.PostStatus
import com.ku.lostandfound.data.PostType

private val DeepGreen = Color(0xFF1B6425)
private val LightGreen = Color(0xFFE4F6E5)
private val Danger = Color(0xFFFF5D7A)
private val LightDanger = Color(0xFFFFECEF)
private val TextGray = Color(0xFF8A8A8A)
private val FieldGray = Color(0xFFF4F4F4)

@Composable
fun PostTypeBadge(type: PostType, modifier: Modifier = Modifier) {
    val isFound = type == PostType.FOUND
    Text(
        text = if (isFound) "습득" else "분실",
        color = if (isFound) DeepGreen else Danger,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = modifier
            .background(if (isFound) LightGreen else LightDanger, RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
fun PostStatusBadge(status: PostStatus, modifier: Modifier = Modifier) {
    val solved = status == PostStatus.RESOLVED
    Text(
        text = if (solved) "해결됨" else "미해결",
        color = if (solved) Color(0xFF777777) else DeepGreen,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = modifier
            .background(if (solved) Color(0xFFEDEDED) else LightGreen, RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
fun CompactPostCard(
    post: BoardPost,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = false,
    showStatus: Boolean = false,
    showResolveButton: Boolean = false,
    onClick: () -> Unit,
    onToggleResolved: (() -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PostTypeBadge(post.type)
                    if (showStatus) {
                        Spacer(Modifier.size(6.dp))
                        PostStatusBadge(post.status)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(post.createdAtText, color = TextGray, fontSize = 12.sp)
                }

                Spacer(Modifier.height(10.dp))
                Text(post.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "◇ ${post.category}",
                    color = TextGray,
                    fontSize = 12.sp,
                )

                if (showResolveButton && onToggleResolved != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (post.status == PostStatus.OPEN) "해결 처리" else "미해결로 변경",
                            color = DeepGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(LightGreen)
                                .clickable { onToggleResolved() }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            if (showThumbnail) {
                Spacer(Modifier.size(12.dp))
                ThumbnailBox(hasImage = post.imageUri != null)
            }
        }
    }
}

@Composable
private fun ThumbnailBox(hasImage: Boolean) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (hasImage) Color(0xFFE7E7E7) else FieldGray),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (hasImage) "IMG" else "",
            color = TextGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun PostListCard(
    post: BoardPost,
    modifier: Modifier = Modifier,
    showStatus: Boolean = true,
    onClick: () -> Unit,
) {
    CompactPostCard(
        post = post,
        modifier = modifier,
        showThumbnail = true,
        showStatus = showStatus,
        onClick = onClick,
    )
}
