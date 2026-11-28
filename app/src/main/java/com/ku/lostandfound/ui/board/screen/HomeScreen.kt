package com.ku.lostandfound.ui.board.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Place
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

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
    onNotificationClick: () -> Unit = {},
    notificationCount: Int = 0,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    emptyMessage: String? = null,
    onRetryClick: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    listHeaderAction: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
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
            onNotificationClick = onNotificationClick,
            notificationCount = notificationCount,
        )

        Box(
            modifier = Modifier.weight(1f)
        ) {
            BoardList(
                modifier = Modifier.fillMaxSize(),
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                listState = listState,
            ) {
                if (showCampusMap && boundary != null) {
                    item {
                        // iOS BoardView: the map sits in its own rounded card above the list section.
                        val mapShape = RoundedCornerShape(18.dp)
                        CampusMapCanvas(
                            boundary = boundary,
                            buildings = buildings,
                            referencePaths = referencePaths,
                            selectedBuildingIds = selectedBuilding?.let { setOf(it.id) }.orEmpty(),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .height(330.dp)
                                .shadow(6.dp, mapShape, ambientColor = Color.Black.copy(alpha = 0.07f), spotColor = Color.Black.copy(alpha = 0.07f))
                                .clip(mapShape)
                                .border(1.dp, Color.Black.copy(alpha = 0.08f), mapShape),
                            showLabels = true,
                            showRoute = false,
                            onBuildingClick = onBuildingClick,
                        )
                        Spacer(Modifier.height(22.dp))
                        if (selectedBuilding != null) {
                            ListHeading("${selectedBuilding.name} 습득물") {
                                Text(
                                    "전체보기",
                                    color = DeepGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(CircleShape).background(Color(0xFFE4F6E5))
                                        .clickable(role = Role.Button, onClick = onClearBuildingFilter)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        } else {
                            ListHeading("전체 습득물", listHeaderAction)
                        }
                    }
                } else {
                    item {
                        ListHeading("전체 분실물", listHeaderAction)
                    }
                }

                items(posts, key = { "${it.type}-${it.id}" }) { post ->
                    CompactPostCard(
                        post = post,
                        modifier = Modifier.padding(horizontal = 20.dp),
                        // Main boards contain only open posts; keep status badges for detail/history screens.
                        showStatus = false,
                        showThumbnail = true,
                        onClick = { onPostClick(post) },
                    )
                }
                if (isLoading && posts.isEmpty()) {
                    item { CircularProgressIndicator(Modifier.padding(vertical = 28.dp).size(28.dp), color = DeepGreen) }
                } else if (errorMessage != null) {
                    item {
                    BoardStateMessage(
                        message = errorMessage,
                        actionLabel = "다시 시도",
                        onActionClick = onRetryClick,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    )
                    }
                } else if (posts.isEmpty() && emptyMessage != null) {
                    item {
                    BoardStateMessage(
                        message = emptyMessage,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    )
                    }
                }
            }

            FloatingActionButton(
                onClick = onAddClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(58.dp),
                shape = CircleShape,
                containerColor = DeepGreen,
                contentColor = Color.White,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "게시글 작성",
                )
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardList(
    modifier: Modifier,
    isRefreshing: Boolean,
    onRefresh: (() -> Unit)?,
    listState: LazyListState,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val list: @Composable () -> Unit = {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
    if (onRefresh == null) list() else PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) { list() }
}

@Composable
private fun ListHeading(text: String, action: (@Composable () -> Unit)?) {
    // iOS sectionHeader: green pin, 16 bold title, trailing control, 1dp rule.
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Place, contentDescription = null, tint = DeepGreen, modifier = Modifier.size(16.dp))
            Text(text = text, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            action?.invoke()
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            thickness = 1.dp,
            color = Color.Black.copy(alpha = 0.08f),
        )
    }
}

@Composable
private fun BoardStateMessage(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    // iOS FinderEmptyState (grey, centred) and ErrorNotice (red text with a 다시 시도 link).
    val isError = actionLabel != null
    Column(
        modifier = modifier.padding(horizontal = if (isError) 20.dp else 24.dp),
        horizontalAlignment = if (isError) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            color = if (isError) Color(0xFFB3261E) else Color.Gray,
            fontSize = 13.sp,
            textAlign = if (isError) TextAlign.Start else TextAlign.Center,
            modifier = if (isError) Modifier else Modifier.fillMaxWidth(),
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(actionLabel, fontSize = 13.sp, color = DeepGreen)
            }
        }
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
            .height(103.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .align(Alignment.BottomCenter)
                .background(Color.White),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomTab(
                    text = "✓\n습득물",
                    selected = selectedType == PostType.FOUND,
                    onClick = onFoundTabClick,
                )
                Spacer(Modifier.width(86.dp))
                BottomTab(
                    text = "⚑\n분실물",
                    selected = selectedType == PostType.LOST,
                    onClick = onLostTabClick,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 17.dp.roundToPx())
                    }
                }
                .size(58.dp)
                .clip(CircleShape)
                .background(DeepGreen)
                .noRippleClickable(onAddClick),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
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
