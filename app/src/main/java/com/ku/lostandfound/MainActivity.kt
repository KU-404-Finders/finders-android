package com.ku.lostandfound

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.ui.navigation.AppBottomBar
import com.ku.lostandfound.ui.navigation.MainNavGraph
import com.ku.lostandfound.ui.navigation.Route
import com.ku.lostandfound.ui.navigation.navigateToMainTab
import com.ku.lostandfound.ui.theme.KUfindersTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TokenManager.init(applicationContext)
        enableEdgeToEdge()

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )

        setContent {
            KUfindersTheme {
                val navController = rememberNavController()

                val backStackEntry by
                navController.currentBackStackEntryAsState()

                val currentRoute =
                    backStackEntry?.destination?.route

                val mainRoutes = setOf(
                    Route.Lost.route,
                    Route.Found.route,
                    Route.ChatList.route,
                    Route.Profile.route,
                )

                BackHandler(
                    enabled = currentRoute in mainRoutes
                ) {
                    finish()
                }

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        if (currentRoute in mainRoutes) {
                            AppBottomBar(
                                currentRoute = currentRoute.orEmpty(),
                                onTabClick = { targetRoute ->
                                    navController.navigateToMainTab(targetRoute)
                                },
                            )
                        }
                    },
                ) { innerPadding ->
                    MainNavGraph(
                        navController = navController,
                        padding = innerPadding,
                    )
                }
            }
        }
    }
}
