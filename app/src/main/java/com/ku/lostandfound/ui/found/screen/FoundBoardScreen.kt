package com.ku.lostandfound.ui.found.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
) {
    var selectedBuilding by remember { mutableStateOf<CampusBuilding?>(null) }
    val foundPosts = posts.filter { it.type == PostType.FOUND }
    val filteredPosts = selectedBuilding?.let { building ->
        foundPosts.filter { post ->
            val indoorMatch = post.foundLocation?.indoorPlace?.buildingId == building.id
            val outdoorMatch = post.foundLocation?.outdoorPin?.let { pin ->
                GeoUtils.pointInPolygon(pin.point, building.outerRing)
            } ?: false
            indoorMatch || outdoorMatch
        }
    } ?: foundPosts

    HomeScreen(
        title = "습득물 캠퍼스맵",
        selectedType = PostType.FOUND,
        posts = filteredPosts,
        boundary = boundary,
        buildings = buildings,
        referencePaths = referencePaths,
        showCampusMap = true,
        selectedBuilding = selectedBuilding,
        onBuildingClick = { selectedBuilding = it },
        onClearBuildingFilter = { selectedBuilding = null },
        onPostClick = onPostClick,
        onAddClick = onAddClick,
        onFoundTabClick = onFoundTabClick,
        onLostTabClick = onLostTabClick,
        onProfileClick = onProfileClick,
        onSearchClick = onSearchClick,
    )
}
