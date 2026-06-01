package com.ku.lostandfound.ui.profile.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PostTypeIcon(
    type: WrittenPostType,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .border(
                width = 2.dp,
                color = color,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        when (type) {
            WrittenPostType.LOST -> {
                Text(
                    text = "i",
                    color = color,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            WrittenPostType.FOUND -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

enum class WrittenPostType {
    LOST,
    FOUND
}