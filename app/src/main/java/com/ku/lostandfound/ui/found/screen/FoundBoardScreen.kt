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
    onBuildingSelected: (CampusBuilding?) -> Unit = {},
) {
    var selectedBuilding by remember { mutableStateOf<CampusBuilding?>(null) }
    val foundPosts = posts.filter { it.type == PostType.FOUND }
    val filteredPosts = selectedBuilding?.let { building ->
        foundPosts.filter { post ->
            val associatedMatch = building.name in post.associatedBuildingNames
            val indoorMatch = post.foundLocation?.indoorPlace?.buildingName == building.name
            val outdoorMatch = post.foundLocation?.outdoorPin?.let { pin ->
                GeoUtils.pointInPolygonOrNearBoundary(pin.point, building.outerRing, toleranceMeters = 4.0)
            } ?: false
            associatedMatch || indoorMatch || outdoorMatch
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
        onBuildingClick = {
            selectedBuilding = it
            onBuildingSelected(it)
        },
        onClearBuildingFilter = {
            selectedBuilding = null
            onBuildingSelected(null)
        },
        onPostClick = onPostClick,
        onAddClick = onAddClick,
        onFoundTabClick = onFoundTabClick,
        onLostTabClick = onLostTabClick,
        onProfileClick = onProfileClick,
        onSearchClick = onSearchClick,
    )
}
