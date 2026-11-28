package com.ku.lostandfound.ui.profile.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// iOS ProfileView.writtenPostCard: white group-radius card, outlined type icon, chevron, count + 건.
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = "$title ${countText}건"; role = Role.Button }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PostTypeIcon(type = type, color = iconColor)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                tint = Color(0xFFC7C7CC), modifier = Modifier.size(20.dp))
        }
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF8E8E93),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(countText, fontSize = if (countText.length <= 3) 26.sp else 22.sp, fontWeight = FontWeight.Bold,
                color = Color.Black, maxLines = 1)
            Text("건", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black,
                modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
