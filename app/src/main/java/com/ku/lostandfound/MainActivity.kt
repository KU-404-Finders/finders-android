package com.ku.lostandfound

import androidx.core.view.WindowCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.reminder.refreshLeaveReminders
import com.ku.lostandfound.notification.FindersNotificationHelper
import com.ku.lostandfound.notification.PushDeviceManager
import com.ku.lostandfound.ui.navigation.AppBottomBar
import com.ku.lostandfound.ui.navigation.MainNavGraph
import com.ku.lostandfound.ui.navigation.PostEditRestore
import com.ku.lostandfound.ui.navigation.isPublicRoute
import com.ku.lostandfound.ui.navigation.Route
import com.ku.lostandfound.ui.navigation.navigateToMainTab
import com.ku.lostandfound.ui.theme.KUfindersTheme
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ku.lostandfound.notification.NotificationTarget
import com.ku.lostandfound.notification.PendingNotification
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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

    private var pendingNotification by mutableStateOf<PendingNotification?>(null)
    private val consumedNotifications = linkedSetOf<String>()
    private var navigationKey = 0
    private var restoredEdit by mutableStateOf<PostEditRestore?>(null)
    private var activeController: androidx.navigation.NavHostController? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("navigationKey", navigationKey)
        outState.putBoolean("publicDestination", isPublicRoute(activeController?.currentDestination?.route))
        outState.putString("navigationOwner", TokenManager.reminderSession())
        pendingNotification?.let { pending ->
            outState.putBundle("pendingNotification", Intent().also { pending.target.putInto(it) }.extras)
            outState.putString("pendingNotificationOwner", pending.owner)
            outState.putString("pendingNotificationDelivery", pending.delivery)
            pending.recipientUserId?.let { outState.putLong("pendingNotificationRecipient", it) }
        }
        outState.putStringArrayList("consumedNotifications", ArrayList(consumedNotifications))
        val nav = activeController
        val entry = nav?.currentBackStackEntry
        val editEntry = if (entry?.destination?.route == Route.LocationPicker.route) nav.previousBackStackEntry else entry
        val edit = restoredEdit ?: PostEditRestore.fromEntry(editEntry)
        edit?.takeIf(PostEditRestore::belongsToCurrentSession)?.let { outState.putBundle("postEditRestore", it.toBundle()) }
    }

    override fun onResume() {
        super.onResume()
        refreshLeaveReminders(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        TokenManager.init(applicationContext)
        if (savedInstanceState == null) {
            queueNotification(intent)
        } else {
            consumedNotifications.addAll(savedInstanceState.getStringArrayList("consumedNotifications").orEmpty())
            pendingNotification = savedInstanceState.getBundle("pendingNotification")?.let {
                NotificationTarget.fromIntent(Intent().putExtras(it))
            }?.let { target -> PendingNotification(target, savedInstanceState.getString("pendingNotificationOwner"),
                savedInstanceState.getString("pendingNotificationDelivery"),
                savedInstanceState.getLong("pendingNotificationRecipient").takeIf { it > 0 }) }
        }

        if (savedInstanceState != null) {
            navigationKey = savedInstanceState.getInt("navigationKey")
            val savedEdit = PostEditRestore.fromBundle(savedInstanceState.getBundle("postEditRestore"))
            val ownsEdit = savedEdit?.belongsToCurrentSession() == true
            val needsFreshNavigation = savedEdit != null && !ownsEdit ||
                !savedInstanceState.getBoolean("publicDestination") &&
                (!TokenManager.hasTokens() || TokenManager.isAccessTokenExpired() ||
                    savedInstanceState.getString("navigationOwner") != TokenManager.reminderSession())
            if (needsFreshNavigation) {
                navigationKey++
                restoredEdit = savedEdit?.takeIf { ownsEdit }
            }
        }
        PushDeviceManager.init(applicationContext)
        FindersNotificationHelper.createChannel(applicationContext)
        synchronized(TokenManager) {
            if (!TokenManager.hasTokens()) FindersNotificationHelper.cancelRemoteNotifications(applicationContext)
        }

        // FID 기반 FCM 등록을 시작한다. 성공 FID는 onRegistered에서만 서버에 전달한다.
        FirebaseMessaging.getInstance().register()
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(
                        "FindersFCM",
                        "FCM FID 등록 시작 실패"
                    )
                }
            }

        setContent {
            KUfindersTheme {
                // Keep scroll ranges intact while suppressing visual bounce/glow on
                // screens whose content does not exceed the viewport.
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                val navController = androidx.compose.runtime.key(navigationKey) { rememberNavController() }
                val chatViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.ku.lostandfound.ui.chat.viewmodel.ChatViewModel>()
                androidx.compose.runtime.SideEffect { activeController = navController }

                val backStackEntry by
                navController.currentBackStackEntryAsState()

                val currentRoute =
                    backStackEntry?.destination?.route
                val isImeVisible = WindowInsets.isImeVisible

                val mainRoutes = setOf(
                    Route.Lost.route,
                    Route.Found.route,
                    Route.ChatList.route,
                    Route.Profile.route,
                )

                val sessionGeneration by TokenManager.sessionChanges.collectAsState()
                LaunchedEffect(pendingNotification, currentRoute, sessionGeneration, restoredEdit) {
                    if (restoredEdit != null) return@LaunchedEffect
                    val queued = pendingNotification ?: return@LaunchedEffect
                    synchronized(TokenManager) {
                        val session = TokenManager.reminderSession()
                        val current = queued.forSession(session, TokenManager.tokenUserId(TokenManager.accessToken))
                        pendingNotification = current
                        if (current == null || session == null || currentRoute == null || isPublicRoute(currentRoute)) {
                            return@synchronized
                        }
                        pendingNotification = null
                        current.delivery?.let { id ->
                            if (!consumedNotifications.add(id)) return@synchronized
                            if (consumedNotifications.size > 32) consumedNotifications.remove(consumedNotifications.first())
                        }
                        val target = current.target

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
                    containerColor = Color.White,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        // NavigationBar handles its own system bars and cutout insets.
                        if (currentRoute in mainRoutes && !isImeVisible) {
                            AppBottomBar(
                                unreadChatCount = chatViewModel.unreadMessageTotal,
                                currentRoute = currentRoute.orEmpty(),
                                onTabClick = { targetRoute ->
                                    navController.navigateToMainTab(targetRoute)
                                },
                            )
                        }
                    },
                ) { innerPadding ->
                    MainNavGraph(
                        chatViewModel = chatViewModel,
                        navController = navController,
                        padding = innerPadding,
                        isFreshLaunch = savedInstanceState == null,
                        restoredEdit = restoredEdit,
                        onRestoredEditHandled = { restoredEdit = null },
                    )
                }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        queueNotification(intent)
    }

    private fun queueNotification(intent: Intent?) {
        synchronized(TokenManager) {
            pendingNotification = PendingNotification.updatedFrom(
                pendingNotification, intent, TokenManager.reminderSession(), consumedNotifications,
                TokenManager.tokenUserId(TokenManager.accessToken),
            )
        }
    }
}
