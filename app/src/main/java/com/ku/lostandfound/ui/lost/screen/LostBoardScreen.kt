package com.ku.lostandfound.ui.lost.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.setValue
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.ui.board.screen.HomeScreen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LostBoardScreen(
    posts: List<BoardPost>,
    onPostClick: (BoardPost) -> Unit,
    onAddClick: () -> Unit,
    onFoundTabClick: () -> Unit,
    onLostTabClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    notificationCount: Int = 0,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetryClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    categoryFilter: String? = null,
    onCategoryFilterChange: (String) -> Unit = {},
) {
    var localCategory by rememberSaveable { mutableStateOf(LostCategory.ALL) }
    val selectedCategory = LostCategory.entries.firstOrNull { it.name == categoryFilter } ?: localCategory
    var filterExpanded by remember { mutableStateOf(false) }
    val filteredPosts = remember(posts, selectedCategory) {
        posts.filter { it.type == PostType.LOST && selectedCategory.matches(it.category) }
    }
    HomeScreen(
        title = "분실물 게시판",
        selectedType = PostType.LOST,
        posts = filteredPosts,
        boundary = null,
        showCampusMap = false,
        onPostClick = onPostClick,
        onAddClick = onAddClick,
        onFoundTabClick = onFoundTabClick,
        onLostTabClick = onLostTabClick,
        onProfileClick = onProfileClick,
        onSearchClick = onSearchClick,
        onNotificationClick = onNotificationClick,
        notificationCount = notificationCount,
        isLoading = isLoading,
        errorMessage = errorMessage,
        emptyMessage = if (selectedCategory == LostCategory.ALL) {
            "분실물 게시글이 없습니다."
        } else {
            "${selectedCategory.label} 분류의 분실물이 없습니다."
        },
        onRetryClick = onRetryClick,
        onRefresh = onRefresh,
        listState = listState,
        listHeaderAction = {
            Box {
                // iOS categoryFilter: mint capsule for 전체, solid green once a category is chosen.
                val filtered = selectedCategory != LostCategory.ALL
                OutlinedButton(
                    onClick = { filterExpanded = true },
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (filtered) Color(0xFF1B6425) else Color(0xFFE4F6E5),
                        contentColor = if (filtered) Color.White else Color(0xFF1B6425),
                    ),
                    border = null,
                    modifier = Modifier.heightIn(min = 30.dp),
                    contentPadding = PaddingValues(
                        horizontal = 12.dp,
                        vertical = 4.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "카테고리 필터",
                        modifier = Modifier.size(17.dp)
                    )

                    Spacer(Modifier.width(6.dp))

                    Text(
                        text = if (filtered) selectedCategory.label else "필터",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.width(4.dp))

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false },
                ) {
                    LostCategory.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.label) },
                            onClick = {
                                localCategory = category
                                onCategoryFilterChange(category.name)
                                filterExpanded = false
                            },
                        )
                    }
                }
            }
        },
    )
}

private enum class LostCategory(val label: String, private val keywords: List<String>) {
    ALL("전체", emptyList()),
    ELECTRONICS("전자기기", listOf("전자", "폰", "phone", "휴대", "tablet", "태블릿", "노트북", "laptop", "이어폰", "airpod", "충전")),
    WALLET_CARD("지갑/카드", listOf("지갑", "카드", "wallet", "card", "신분", "학생증", "교통")),
    FASHION("의류/패션잡화", listOf("의류", "옷", "패션", "모자", "신발", "jacket", "shirt", "coat")),
    BAG("가방/파우치", listOf("가방", "백팩", "파우치", "bag", "pouch")),
    BOOK_DOCUMENT("책/문구/서류", listOf("책", "문구", "서류", "노트", "book", "document", "필기")),
    TUMBLER("텀블러/컵", listOf("텀블러", "컵", "물병", "tumbler", "cup", "bottle")),
    UMBRELLA("우산", listOf("우산", "umbrella")),
    KEY("열쇠/키링", listOf("열쇠", "키링", "key", "keyring")),
    OTHER("기타", emptyList());

    fun matches(kind: String?): Boolean {
        if (this == ALL) return true
        val normalized = kind.orEmpty().lowercase().replace(" ", "")
        return if (this == OTHER) entries.none { category -> category != ALL && category != OTHER && category.keywords.any(normalized::contains) }
        else keywords.any(normalized::contains)
    }
}
