package com.ku.lostandfound.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val DeepGreen = Color(0xFF1B6425)
private val MidGreen = Color(0xFF356D3C)
private val PaleGray = Color(0xFFF4F4F4)
private val DisabledText = Color(0xFF8C8C8C)

@Composable
fun GreenSegmentedSwitch(
    leftText: String,
    rightText: String,
    selectedLeft: Boolean,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(PaleGray)
    ) {
        SegmentItem(
            text = leftText,
            selected = selectedLeft,
            modifier = Modifier.weight(1f),
            onClick = onLeftClick,
        )
        SegmentItem(
            text = rightText,
            selected = !selectedLeft,
            modifier = Modifier.weight(1f),
            onClick = onRightClick,
        )
    }
}

@Composable
private fun SegmentItem(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) MidGreen else PaleGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else DisabledText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
