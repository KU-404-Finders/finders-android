package com.ku.lostandfound

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.navigation.compose.rememberNavController
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.ui.navigation.MainNavGraph
import com.ku.lostandfound.ui.theme.KUfindersTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TokenManager.init(applicationContext)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        setContent {
            KUfindersTheme {
                val navController = rememberNavController()

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    MainNavGraph(
                        navController = navController,
                        padding = innerPadding
                    )
                }
            }
        }
    }
}
