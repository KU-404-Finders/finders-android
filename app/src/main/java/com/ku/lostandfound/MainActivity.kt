package com.ku.lostandfound

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.notification.FindersNotificationHelper
import com.ku.lostandfound.notification.PushDeviceManager
import com.ku.lostandfound.ui.navigation.AppBottomBar
import com.ku.lostandfound.ui.navigation.MainNavGraph
import com.ku.lostandfound.ui.navigation.Route
import com.ku.lostandfound.ui.navigation.navigateToMainTab
import com.ku.lostandfound.ui.theme.KUfindersTheme
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ku.lostandfound.notification.NotificationTarget

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            Log.d(
                "FindersFCM",
                if (granted) {
                    "알림 권한 허용됨"
                } else {
                    "알림 권한 거부됨"
                }
            )
        }

    private var pendingNotificationTarget
            by mutableStateOf<NotificationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            pendingNotificationTarget =
                NotificationTarget.fromIntent(intent)
        }

        TokenManager.init(applicationContext)
        PushDeviceManager.init(applicationContext)
        FindersNotificationHelper.createChannel(applicationContext)

        enableEdgeToEdge()

        // 앱 실행 시 Firebase 기기 등록 상태 동기화
        FirebaseMessaging.getInstance().register()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(
                        "FindersFCM",
                        "Firebase 기기 등록 요청 성공"
                    )
                } else {
                    Log.w(
                        "FindersFCM",
                        "Firebase 기기 등록 요청 실패",
                        task.exception
                    )
                }
            }

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

                LaunchedEffect(
                    pendingNotificationTarget,
                    currentRoute
                ) {
                    val target = pendingNotificationTarget
                        ?: return@LaunchedEffect

                    // 로그아웃 상태라면 로그인 완료 후 이동
                    if (
                        !TokenManager.isLoggedIn() ||
                        currentRoute == null ||
                        currentRoute == Route.Login.route
                    ) {
                        return@LaunchedEffect
                    }

                    // 중복 이동 방지
                    pendingNotificationTarget = null

                    when (target) {

                        is NotificationTarget.Chat -> {

                            val alreadyOpened =
                                currentRoute == Route.ChatRoom.route &&
                                        backStackEntry?.arguments?.getLong(
                                            Route.ChatRoom.ARG_ROOM_ID
                                        ) == target.roomId

                            if (!alreadyOpened) {
                                // 뒤로 가면 채팅 목록으로 돌아가도록 처리
                                navController.navigateToMainTab(
                                    Route.ChatList.route
                                )

                                navController.navigate(
                                    Route.ChatRoom.create(target.roomId)
                                ) {
                                    launchSingleTop = true
                                }
                            }
                        }

                        is NotificationTarget.Post -> {

                            val alreadyOpened =
                                currentRoute == Route.PostDetail.route &&
                                        backStackEntry?.arguments?.getString(
                                            Route.PostDetail.ARG_POST_ID
                                        ) == target.postId.toString() &&
                                        backStackEntry!!.arguments?.getString(
                                            Route.PostDetail.ARG_POST_TYPE
                                        ) == target.postType

                            if (!alreadyOpened) {

                                val boardRoute =
                                    if (target.postType == "LOST") {
                                        Route.Lost.route
                                    } else {
                                        Route.Found.route
                                    }

                                // 뒤로 가면 해당 게시판으로 돌아감
                                navController.navigateToMainTab(boardRoute)

                                navController.navigate(
                                    Route.PostDetail.create(
                                        postId = target.postId.toString(),
                                        postTypeName = target.postType
                                    )
                                ) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                }

                LaunchedEffect(currentRoute) {
                    if (
                        currentRoute in mainRoutes &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        val prefs = getSharedPreferences(
                            "notification_permission",
                            MODE_PRIVATE
                        )

                        if (!prefs.getBoolean("requested_once", false)) {
                            prefs.edit {
                                putBoolean("requested_once", true)
                            }

                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    }
                }

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
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        pendingNotificationTarget =
            NotificationTarget.fromIntent(intent)
    }
}
