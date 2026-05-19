package com.ku.lostandfound.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.CampusBoundary
import com.ku.lostandfound.data.CampusBuilding
import com.ku.lostandfound.data.CampusJsonRepository
import com.ku.lostandfound.data.CampusPath
import com.ku.lostandfound.data.FoundLocationSelection
import com.ku.lostandfound.data.GeoPoint
import com.ku.lostandfound.data.LostLocationSelection
import com.ku.lostandfound.data.OutdoorPin
import com.ku.lostandfound.data.PostStatus
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.ui.found.screen.FoundBoardScreen
import com.ku.lostandfound.ui.location.screen.LocationPickerScreen
import com.ku.lostandfound.ui.login.screen.LoginScreen
import com.ku.lostandfound.ui.lost.screen.LostBoardScreen
import com.ku.lostandfound.ui.post.screen.PostDetailScreen
import com.ku.lostandfound.ui.post.screen.PostWriteScreen
import com.ku.lostandfound.ui.profile.screen.MyPostsScreen
import com.ku.lostandfound.ui.profile.screen.ProfileScreen
import com.ku.lostandfound.ui.signup.screen.SignupCodeScreen
import com.ku.lostandfound.ui.signup.screen.SignupEmailScreen
import com.ku.lostandfound.ui.signup.screen.SignupFinishScreen
import com.ku.lostandfound.ui.signup.screen.SignupNameScreen
import com.ku.lostandfound.ui.signup.screen.SignupPwScreen
import com.ku.lostandfound.ui.signup.viewmodel.SignupViewmodel
import com.ku.lostandfound.viewmodel.PostWriteViewModel

@Composable
fun MainNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    val writeViewModel = remember { PostWriteViewModel() }
    val signupViewModel = remember { SignupViewmodel() }

    val currentUserName = signupViewModel.name.ifBlank { "김건겸" }
    val currentUserEmail = signupViewModel.email.ifBlank { "konkuk26@konkuk.ac.kr" }

    var boundary by remember { mutableStateOf<CampusBoundary?>(null) }
    var buildings by remember { mutableStateOf<List<CampusBuilding>>(emptyList()) }
    var referencePaths by remember { mutableStateOf<List<CampusPath>>(emptyList()) }
    var posts by remember { mutableStateOf(sampleBoardPosts(currentUserName, currentUserEmail)) }

    LaunchedEffect(Unit) {
        val repo = CampusJsonRepository(context)
        boundary = repo.loadBoundary()
        buildings = repo.loadBuildings()
        referencePaths = repo.loadPaths()
    }

    fun toggleResolved(post: BoardPost) {
        posts = posts.map {
            if (it.id == post.id) {
                it.copy(status = if (it.status == PostStatus.OPEN) PostStatus.RESOLVED else PostStatus.OPEN)
            } else {
                it
            }
        }
    }

    fun submitPost() {
        if (!writeViewModel.canSubmit()) return
        val newPost = BoardPost(
            id = System.currentTimeMillis().toString(),
            type = writeViewModel.postType,
            status = PostStatus.OPEN,
            title = writeViewModel.title,
            category = writeViewModel.category,
            content = writeViewModel.content,
            imageUri = writeViewModel.imageUri,
            authorName = currentUserName,
            authorEmail = currentUserEmail,
            lostLocation = if (writeViewModel.postType == PostType.LOST) writeViewModel.lostLocation else null,
            foundLocation = if (writeViewModel.postType == PostType.FOUND) writeViewModel.foundLocation else null,
        )
        posts = listOf(newPost) + posts
        writeViewModel.reset()
        navController.navigate(if (newPost.type == PostType.FOUND) Route.Found.route else Route.Lost.route) {
            popUpTo(Route.PostWrite.route) { inclusive = true }
        }
    }

    fun goFound() {
        navController.navigate(Route.Found.route) { launchSingleTop = true }
    }

    fun goLost() {
        navController.navigate(Route.Lost.route) { launchSingleTop = true }
    }

    NavHost(
        navController = navController,
        startDestination = Route.Login.route,
        modifier = modifier.padding(padding),
    ) {
        composable(route = Route.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Route.Found.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                },
                onNavigateToSignUp = { navController.navigate(Route.SignupName.route) }
            )
        }
        composable(route = Route.SignupName.route) {
            SignupNameScreen(
                viewModel = signupViewModel,
                onNavigateToPw = { navController.navigate(Route.SignupPw.route) },
                onNavigateToBack = { navController.popBackStack() },
            )
        }
        composable(route = Route.SignupPw.route) {
            SignupPwScreen(
                viewModel = signupViewModel,
                onNavigateToEmail = { navController.navigate(Route.SignupEmail.route) },
                onNavigateToBack = { navController.popBackStack() },
            )
        }
        composable(route = Route.SignupEmail.route) {
            SignupEmailScreen(
                viewModel = signupViewModel,
                onNavigateToCode = { navController.navigate(Route.SignupCode.route) },
                onNavigateToBack = { navController.popBackStack() },
            )
        }
        composable(route = Route.SignupCode.route) {
            SignupCodeScreen(
                viewModel = signupViewModel,
                onNavigateToFinish = { navController.navigate(Route.SignupFinish.route) },
                onNavigateToBack = { navController.popBackStack() },
            )
        }
        composable(route = Route.SignupFinish.route) {
            SignupFinishScreen(
                onNavigateToLogin = {
                    navController.navigate(Route.Login.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Route.Found.route) {
            val b = boundary
            if (b == null) {
                Text("지도 데이터를 불러오는 중입니다.")
            } else {
                FoundBoardScreen(
                    posts = posts,
                    boundary = b,
                    buildings = buildings,
                    referencePaths = referencePaths,
                    onPostClick = { navController.navigate(Route.PostDetail.create(it.id)) },
                    onAddClick = { navController.navigate(Route.PostWrite.route) },
                    onFoundTabClick = { goFound() },
                    onLostTabClick = { goLost() },
                    onProfileClick = { navController.navigate(Route.Profile.route) },
                )
            }
        }

        composable(route = Route.Lost.route) {
            LostBoardScreen(
                posts = posts,
                onPostClick = { navController.navigate(Route.PostDetail.create(it.id)) },
                onAddClick = { navController.navigate(Route.PostWrite.route) },
                onFoundTabClick = { goFound() },
                onLostTabClick = { goLost() },
                onProfileClick = { navController.navigate(Route.Profile.route) },
            )
        }

        composable(route = Route.Profile.route) {
            val myPosts = posts.filter { it.authorEmail == currentUserEmail }
            ProfileScreen(
                userName = currentUserName,
                userEmail = currentUserEmail,
                myPosts = myPosts,
                onPostClick = { navController.navigate(Route.PostDetail.create(it.id)) },
                onShowAllClick = { navController.navigate(Route.MyPosts.route) },
                onLogoutClick = {
                    navController.navigate(Route.Login.route) { popUpTo(0) }
                },
                onWithdrawClick = { },
            )
        }

        composable(route = Route.MyPosts.route) {
            val myPosts = posts.filter { it.authorEmail == currentUserEmail }
            MyPostsScreen(
                posts = myPosts,
                onBackClick = { navController.popBackStack() },
                onPostClick = { navController.navigate(Route.PostDetail.create(it.id)) },
                onToggleResolvedClick = { toggleResolved(it) },
            )
        }

        composable(route = Route.PostWrite.route) {
            PostWriteScreen(
                viewModel = writeViewModel,
                onBackClick = { navController.popBackStack() },
                onAddLocationClick = { postType -> navController.navigate(Route.LocationPicker.create(postType.name)) },
                onSubmitClick = { submitPost() },
            )
        }

        composable(
            route = Route.LocationPicker.route,
            arguments = listOf(navArgument(Route.LocationPicker.ARG_POST_TYPE) { type = NavType.StringType })
        ) { backStackEntry ->
            val b = boundary
            val postType = backStackEntry.arguments
                ?.getString(Route.LocationPicker.ARG_POST_TYPE)
                ?.let { runCatching { PostType.valueOf(it) }.getOrNull() }
                ?: writeViewModel.postType

            if (b == null) {
                Text("지도 데이터를 불러오는 중입니다.")
            } else {
                LocationPickerScreen(
                    postType = postType,
                    boundary = b,
                    buildings = buildings,
                    referencePaths = referencePaths,
                    initialOutdoorPins = if (postType == PostType.LOST) {
                        writeViewModel.lostLocation.outdoorPins
                    } else {
                        listOfNotNull(writeViewModel.foundLocation.outdoorPin)
                    },
                    initialIndoorPlaces = if (postType == PostType.LOST) {
                        writeViewModel.lostLocation.indoorPlaces
                    } else {
                        listOfNotNull(writeViewModel.foundLocation.indoorPlace)
                    },
                    onBackClick = { navController.popBackStack() },
                    onSubmitLost = { outdoorPins, indoorPlaces ->
                        writeViewModel.setLostOutdoorPins(outdoorPins.map { it.point })
                        writeViewModel.setLostIndoorPlaces(indoorPlaces)
                        navController.popBackStack()
                    },
                    onSubmitFound = { outdoorPin, indoorPlace ->
                        when {
                            outdoorPin != null -> writeViewModel.setFoundOutdoorPin(outdoorPin.point)
                            indoorPlace != null -> writeViewModel.setFoundIndoorPlace(indoorPlace)
                        }
                        navController.popBackStack()
                    },
                )
            }
        }

        composable(
            route = Route.PostDetail.route,
            arguments = listOf(navArgument(Route.PostDetail.ARG_POST_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val b = boundary
            val postId = backStackEntry.arguments?.getString(Route.PostDetail.ARG_POST_ID).orEmpty()
            val post = posts.firstOrNull { it.id == postId }

            if (b == null) {
                Text("지도 데이터를 불러오는 중입니다.")
            } else if (post == null) {
                Text("게시글을 찾을 수 없습니다.")
            } else {
                PostDetailScreen(
                    post = post,
                    boundary = b,
                    buildings = buildings,
                    referencePaths = referencePaths,
                    onBackClick = { navController.popBackStack() },
                    showOwnerActions = post.authorEmail == currentUserEmail,
                    onToggleResolvedClick = { toggleResolved(it) },
                )
            }
        }
    }
}

private fun sampleBoardPosts(userName: String, userEmail: String): List<BoardPost> {
    val p1 = OutdoorPin(1, GeoPoint(127.0790, 37.5418))
    val p2 = OutdoorPin(2, GeoPoint(127.0772, 37.5412))
    val p3 = OutdoorPin(3, GeoPoint(127.0755, 37.5428))

    return listOf(
        BoardPost(
            id = "my-1",
            type = PostType.LOST,
            status = PostStatus.OPEN,
            title = "공학관 아이패드 프로",
            category = "전자기기",
            content = "공학관 근처에서 아이패드 프로를 잃어버렸습니다.",
            imageUri = "sample://ipad",
            authorName = userName,
            authorEmail = userEmail,
            lostLocation = LostLocationSelection(outdoorPins = listOf(p1, p2, p3)),
        ),
        BoardPost(
            id = "my-2",
            type = PostType.LOST,
            status = PostStatus.OPEN,
            title = "경영관 에어팟",
            category = "전자기기",
            content = "경영관 강의실에서 에어팟을 잃어버렸습니다.",
            imageUri = "sample://airpods",
            authorName = userName,
            authorEmail = userEmail,
        ),
        BoardPost(
            id = "my-3",
            type = PostType.LOST,
            status = PostStatus.RESOLVED,
            title = "상허연구관 학생증",
            category = "학생증",
            content = "상허연구관에서 학생증을 잃어버렸습니다.",
            imageUri = "sample://card",
            authorName = userName,
            authorEmail = userEmail,
        ),
        BoardPost(
            id = "found-1",
            type = PostType.FOUND,
            status = PostStatus.OPEN,
            title = "학생회관 에어팟 프로",
            category = "전자기기",
            content = "학생회관 내부에서 에어팟 프로를 습득했습니다.",
            authorName = userName,
            authorEmail = userEmail,
            foundLocation = FoundLocationSelection(outdoorPin = OutdoorPin(1, GeoPoint(127.0781, 37.5416))),
        ),
        BoardPost(
            id = "found-2",
            type = PostType.FOUND,
            status = PostStatus.OPEN,
            title = "학생회관 충전기",
            category = "전자기기",
            content = "학생회관에서 충전기를 습득했습니다.",
            authorName = "익명",
            authorEmail = "someone@konkuk.ac.kr",
            foundLocation = FoundLocationSelection(outdoorPin = OutdoorPin(1, GeoPoint(127.0777, 37.5419))),
        ),
    )
}
