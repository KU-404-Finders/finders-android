package com.ku.lostandfound.ui.navigation

import androidx.navigation.NavHostController

internal fun NavHostController.navigateToMainTab(route: String) {
    if (currentBackStackEntry?.destination?.route == route) return
    navigate(route) {
        launchSingleTop = true
        // This app uses one shared board stack. Saving the popped stack under
        // Found and restoring it here can reopen Lost when Found is selected.
        popUpTo(Route.Found.route)
    }
}
