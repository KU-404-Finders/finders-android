package com.ku.lostandfound.ui.board.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.CampusBoundary
import com.ku.lostandfound.data.CampusBuilding
import com.ku.lostandfound.data.CampusPath
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.ui.component.CampusMapCanvas
import com.ku.lostandfound.ui.component.CompactPostCard
import com.ku.lostandfound.ui.component.HomeTopBar
import com.ku.lostandfound.ui.component.noRippleClickable

private val DeepGreen = Color(0xFF1B6425)
private val ScreenGray = Color(0xFFF4F4F4)

@Composable
fun HomeScreen(
    title: String,
    selectedType: PostType,
    posts: List<BoardPost>,
    boundary: CampusBoundary?,
    buildings: List<CampusBuilding> = emptyList(),
    referencePaths: List<CampusPath> = emptyList(),
    showCampusMap: Boolean,
    selectedBuilding: CampusBuilding? = null,
    onBuildingClick: (CampusBuilding) -> Unit = {},
    onClearBuildingFilter: () -> Unit = {},
    onPostClick: (BoardPost) -> Unit,
    onAddClick: () -> Unit,
    onFoundTabClick: () -> Unit,
    onLostTabClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenGray)
    ) {
        HomeTopBar(
            title = title,
            onProfileClick = onProfileClick,
            onSearchClick = onSearchClick,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showCampusMap && boundary != null) {
                item {
                    CampusMapCanvas(
                        boundary = boundary,
                        buildings = buildings,
                        referencePaths = referencePaths,
                        selectedBuildingIds = selectedBuilding?.let { setOf(it.id) }.orEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(330.dp),
                        showLabels = true,
                        showRoute = false,
                        onBuildingClick = onBuildingClick,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (selectedBuilding != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "📍 ${selectedBuilding.name} 게시물만 보는 중",
                                color = DeepGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onClearBuildingFilter) {
                                Text("전체보기", color = DeepGreen, fontSize = 13.sp)
                            }
                        }
                    } else {
                        Text(
                            text = "📍 전체 구역 게시물",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = "전체 분실물 게시물",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }

            items(posts) { post ->
                CompactPostCard(
                    post = post,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    showStatus = true,
                    showThumbnail = true,
                    onClick = { onPostClick(post) },
                )
            }
        }

        MainBottomBar(
            selectedType = selectedType,
            onFoundTabClick = onFoundTabClick,
            onLostTabClick = onLostTabClick,
            onAddClick = onAddClick,
        )
    }
}

@Composable
fun MainBottomBar(
    selectedType: PostType,
    onFoundTabClick: () -> Unit,
    onLostTabClick: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTab(
                text = "✓\n습득물",
                selected = selectedType == PostType.FOUND,
                onClick = onFoundTabClick,
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DeepGreen)
                    .noRippleClickable(onAddClick)
                    .padding(horizontal = 19.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            BottomTab(
                text = "⚑\n분실물",
                selected = selectedType == PostType.LOST,
                onClick = onLostTabClick,
            )
        }
    }
}

@Composable
private fun BottomTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (selected) DeepGreen else Color.Gray,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
        modifier = Modifier.noRippleClickable(onClick),
    )
}
