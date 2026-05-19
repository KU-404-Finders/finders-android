package com.ku.lostandfound.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.R

@Composable
fun GetBackTopAppBar(isWhite: Boolean = true, title: String = "", onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isWhite) Color.White else Color(0xFFF4F4F4))
            .padding(start = 20.dp)
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_left_arrow),
            contentDescription = "back",
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onClick)
        )
        if (title.isNotEmpty()) {
            Text(
                text = title,
                modifier = Modifier.padding(start = 14.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

}

@Preview
@Composable
private fun GetBackTopAppBarPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GetBackTopAppBar(false, "새 글 쓰기")
        GetBackTopAppBar()
    }
}