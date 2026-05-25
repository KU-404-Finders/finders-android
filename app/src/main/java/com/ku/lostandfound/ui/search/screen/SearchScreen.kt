package com.ku.lostandfound.ui.search.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.ui.component.CompactPostCard

@Composable
fun SearchScreen(
    posts: List<BoardPost>,
    onBackClick: () -> Unit,
    onPostClick: (BoardPost) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val trimmedKeyword = keyword.trim()

    val searchedPosts = remember(posts, trimmedKeyword) {
        if (trimmedKeyword.isBlank()) {
            emptyList()
        } else {
            posts.filter { post ->
                post.matchesKeyword(trimmedKeyword)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F4F4))
    ) {
        SearchTopBar(
            keyword = keyword,
            onKeywordChange = { keyword = it },
            onBackClick = onBackClick,
            onSearch = { focusManager.clearFocus() }
        )

        when {
            trimmedKeyword.isBlank() -> {
                EmptySearchGuide(
                    iconText = "⌕",
                    message = "검색어를 입력해보세요"
                )
            }

            searchedPosts.isEmpty() -> {
                EmptySearchGuide(
                    iconText = "",
                    message = "검색 결과가 없습니다."
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = searchedPosts,
                        key = { it.id }
                    ) { post ->
                        CompactPostCard(
                            post = post,
                            showThumbnail = true,
                            showStatus = false,
                            onClick = { onPostClick(post) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "‹",
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            color = Color.Black,
            modifier = Modifier
                .padding(end = 10.dp)
                .clickable(onClick = onBackClick)
        )

        TextField(
            value = keyword,
            onValueChange = onKeywordChange,
            placeholder = {
                Text(
                    text = "물건 이름, 장소, 태그 검색",
                    color = Color(0xFFB0B0B0),
                    fontSize = 12.sp
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(6.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onSearch() }
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF4F4F4),
                unfocusedContainerColor = Color(0xFFF4F4F4),
                disabledContainerColor = Color(0xFFF4F4F4),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                unfocusedTextColor = Color.Black,
                focusedTextColor = Color.Black,
            ),
            modifier = Modifier
                .weight(1f)
        )

        Text(
            text = "⌕",
            fontSize = 34.sp,
            color = Color(0xFF8F8F8F),
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun EmptySearchGuide(
    iconText: String,
    message: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (iconText.isNotBlank()) {
                Text(
                    text = iconText,
                    fontSize = 70.sp,
                    color = Color(0xFFE0E0E0)
                )
                Spacer(modifier = Modifier.size(14.dp))
            }

            Text(
                text = message,
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun BoardPost.matchesKeyword(keyword: String): Boolean {
    val lowerKeyword = keyword.lowercase()

    val searchableText = buildString {
        append(title)
        append(" ")
        append(category)
        append(" ")
        append(content)
        append(" ")
        append(type.label)
        append(" ")
        append(locationText())
    }.lowercase()

    return searchableText.contains(lowerKeyword)
}

private fun BoardPost.locationText(): String {
    return when (type) {
        PostType.FOUND -> {
            foundLocation?.indoorPlace?.buildingName
                ?: foundLocation?.outdoorPin?.let { "외부 위치" }
                ?: ""
        }

        PostType.LOST -> {
            val indoorText = lostLocation?.indoorPlaces
                ?.joinToString(" ") { it.buildingName }
                .orEmpty()

            val outdoorText = if (lostLocation?.outdoorPins?.isNotEmpty() == true) {
                "외부 경로"
            } else {
                ""
            }

            "$indoorText $outdoorText"
        }
    }
}