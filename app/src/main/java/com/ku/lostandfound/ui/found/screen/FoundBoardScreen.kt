package com.ku.lostandfound.ui.found.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.CampusBoundary
import com.ku.lostandfound.data.CampusBuilding
import com.ku.lostandfound.data.CampusPath
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.ui.board.screen.HomeScreen
import com.ku.lostandfound.util.GeoUtils

@Composable
fun FoundBoardScreen(
    posts: List<BoardPost>,
    boundary: CampusBoundary,
    buildings: List<CampusBuilding>,
    referencePaths: List<CampusPath> = emptyList(),
    onPostClick: (BoardPost) -> Unit,
    onAddClick: () -> Unit,
    onFoundTabClick: () -> Unit,
    onLostTabClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    notificationCount: Int = 0,
    selectedBuilding: CampusBuilding?,
    onBuildingSelected: (CampusBuilding?) -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetryClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    val filteredPosts = remember(posts, selectedBuilding) {
        val foundPosts = posts.filter { it.type == PostType.FOUND }
        selectedBuilding?.let { building ->
            foundPosts.filter { post ->
                val associatedMatch = building.name in post.associatedBuildingNames
                val indoorMatch = post.foundLocation?.indoorPlace?.buildingName == building.name
                val outdoorMatch = post.foundLocation?.outdoorPin?.let { pin ->
                    GeoUtils.pointInPolygonOrNearBoundary(pin.point, building.outerRing, toleranceMeters = 4.0)
                } ?: false
                associatedMatch || indoorMatch || outdoorMatch
            }
        } ?: foundPosts
    }

    HomeScreen(
        title = "습득물 게시판",
        selectedType = PostType.FOUND,
        posts = filteredPosts,
        boundary = boundary,
        buildings = buildings,
        referencePaths = referencePaths,
        showCampusMap = true,
        selectedBuilding = selectedBuilding,
        onBuildingClick = onBuildingSelected,
        onClearBuildingFilter = {
            onBuildingSelected(null)
        },
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
        emptyMessage = selectedBuilding?.let { "${it.name}에 등록된 습득물 게시글이 없습니다." } ?: "습득물 게시글이 없습니다.",
        onRetryClick = onRetryClick,
        onRefresh = onRefresh,
        listState = listState,
    )
}
