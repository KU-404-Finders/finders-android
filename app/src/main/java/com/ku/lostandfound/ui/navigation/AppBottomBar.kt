package com.ku.lostandfound.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

private val DeepGreen = Color(0xFF1B6425)

private data class MainTab(
    val route: String,
    val title: String,
    val icon: ImageVector,
)

@Composable
fun AppBottomBar(
    currentRoute: String,
    onTabClick: (String) -> Unit,
) {
    val tabs = listOf(
        MainTab(Route.Lost.route, "분실물", Icons.Outlined.Search),
        MainTab(Route.Found.route, "습득물", Icons.Outlined.CheckCircle),
        MainTab(Route.ChatList.route, "채팅", Icons.Outlined.ChatBubbleOutline),
        MainTab(Route.Profile.route, "마이페이지", Icons.Outlined.Person),
    )

    NavigationBar(
        containerColor = Color.White,
        tonalElevation = androidx.compose.ui.unit.Dp.Hairline,
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    if (currentRoute != tab.route) {
                        onTabClick(tab.route)
                    }
                },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                    )
                },
                label = {
                    Text(text = tab.title)
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DeepGreen,
                    selectedTextColor = DeepGreen,
                    indicatorColor = Color(0xFFE7F3E9),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                ),
            )
        }
    }
}
