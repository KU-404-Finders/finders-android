package com.ku.lostandfound.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.R

private val TopBarBackground = Color(0xFFF4F4F4)
private val DeepGreen = Color(0xFF1B6425)

@Composable
fun BackTitleBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(TopBarBackground)
            .padding(start = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
       Image(
           painter = painterResource(id = R.drawable.img_left_arrow),
           contentDescription = "뒤로가기",
           modifier = Modifier.size(20.dp).clickable(onClick = onBackClick)
       )
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1E1E))
    }
}

@Composable
fun HomeTopBar(
    title: String = "습득물 캠퍼스맵",
    onProfileClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(DeepGreen)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "♙",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 24.sp,
            modifier = Modifier.clickable(onClick = onProfileClick),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "⌕",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 30.sp,
            modifier = Modifier.clickable(onClick = onSearchClick),
        )
    }
}
