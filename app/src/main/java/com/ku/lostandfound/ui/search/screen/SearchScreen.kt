package com.ku.lostandfound.ui.search.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material3.LinearProgressIndicator
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
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
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

        if (error != null) {
            // iOS ErrorNotice: red text with a 다시 시도 link.
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Text(error, color = Color(0xFFB3261E), fontSize = 13.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                androidx.compose.material3.TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                    Text("다시 시도", color = Color(0xFF1B6425), fontSize = 13.sp)
                }
            }
        }
        when {
            trimmedKeyword.isBlank() -> {
                EmptySearchGuide(
                    icon = Icons.Outlined.Search,
                    message = "찾는 물건이나 장소를 검색해보세요",
                    detail = "제목, 종류, 건물 이름으로 분실물과 습득물을 함께 찾습니다.",
                )
            }

            searchedPosts.isEmpty() -> {
                EmptySearchGuide(
                    icon = if (loading) null else Icons.Outlined.Inbox,
                    message = when {
                        loading -> "게시글을 불러오는 중입니다."
                        error != null -> "전체 게시글을 검색할 수 없습니다."
                        else -> "검색 결과가 없습니다"
                    }
                )
            }

            else -> {
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = searchedPosts,
                        key = { "${it.type}-${it.id}" }
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

// iOS SearchView bar: 48dp back chevron, rounded field with a leading magnifier and a clear button.
@Composable
private fun SearchTopBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onSearch: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.06f), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 0.5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx()))
            }
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBackClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "뒤로가기", tint = Color.Black, modifier = Modifier.size(28.dp))
        }
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF4F4F4))
                .border(1.5.dp, if (focused) Color(0xFF1B6425).copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(14.dp))
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Color(0xFF8A8A8A), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = Color.Black),
                cursorBrush = SolidColor(Color(0xFF1B6425)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (keyword.isEmpty()) Text("물건 이름, 장소, 종류 검색", color = Color(0xFFB0B0B0), fontSize = 15.sp)
                        inner()
                    }
                },
            )
            if (keyword.isNotEmpty()) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { onKeywordChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Cancel, contentDescription = "검색어 지우기", tint = Color(0xFFBDBDBD), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptySearchGuide(
    icon: ImageVector?,
    message: String,
    detail: String? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            icon?.let { Icon(it, contentDescription = null, tint = Color(0xFFC9CFC9), modifier = Modifier.size(44.dp)) }
            Text(text = message, color = Color(0xFF6F6F6F), fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            detail?.let { Text(it, color = Color(0xFF8A8A8A), fontSize = 13.sp, textAlign = TextAlign.Center) }
        }
    }
}

private fun BoardPost.matchesKeyword(keyword: String): Boolean {
    val lowerKeyword = keyword.lowercase()

    val searchableText = buildString {
        append(title)
        append(" ")
        append(category)
        searchableContent()
            .takeIf { it.isNotBlank() }
            ?.let {
                append(" ")
                append(it)
            }
        append(" ")
        append(type.label)
        append(" ")
        append(locationText())
    }.lowercase()

    return searchableText.contains(lowerKeyword)
}

private fun BoardPost.searchableContent(): String {
    return content.takeUnless {
        it.contains("상세 정보를 불러오려면 상세 조회 API가 필요합니다.") ||
            it.contains("상세 조회가 필요합니다.")
    }.orEmpty()
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
