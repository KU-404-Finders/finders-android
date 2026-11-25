package com.ku.lostandfound.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController

internal fun NavHostController.navigateToLoginClearingBackStack() {
    navigate(Route.Login.route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
internal fun AuthStepGuard(
    navController: NavHostController,
    route: Route,
    hasPrerequisites: Boolean,
    onInvalid: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (hasPrerequisites) {
        content()
    } else {
        LaunchedEffect(navController, route) {
            if (navController.currentDestination?.route == route.route) onInvalid()
        }
    }
}
