package com.ku.lostandfound.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ku.lostandfound.data.PostStatus
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.ui.found.screen.FoundBoardScreen
import com.ku.lostandfound.ui.location.screen.LocationPickerScreen
import com.ku.lostandfound.ui.login.screen.LoginScreen
import com.ku.lostandfound.ui.lost.screen.LostBoardScreen
import com.ku.lostandfound.ui.post.screen.MatchCandidateUiModel
import com.ku.lostandfound.ui.post.screen.PostDetailScreen
import com.ku.lostandfound.ui.post.screen.PostWriteScreen
import com.ku.lostandfound.network.MyFoundItemData
import com.ku.lostandfound.network.MyLostItemData
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.network.UserMeData
import com.ku.lostandfound.ui.post.viewmodel.PostCommentViewModel
import com.ku.lostandfound.ui.profile.screen.MyPostsType
import com.ku.lostandfound.ui.profile.screen.MyPostsScreen
import com.ku.lostandfound.ui.profile.screen.ProfileScreen
import com.ku.lostandfound.ui.signup.screen.SignupCodeScreen
import com.ku.lostandfound.ui.signup.screen.SignupEmailScreen
import com.ku.lostandfound.ui.signup.screen.SignupFinishScreen
import com.ku.lostandfound.ui.signup.screen.SignupNameScreen
import com.ku.lostandfound.ui.signup.screen.SignupPwScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ku.lostandfound.data.BoardComment
import com.ku.lostandfound.ui.found.viewmodel.FoundItemMatchUiState
import com.ku.lostandfound.ui.found.viewmodel.FoundItemViewModel
import com.ku.lostandfound.ui.login.viewmodel.LoginViewModel
import com.ku.lostandfound.ui.lost.viewmodel.LostItemViewModel
import com.ku.lostandfound.ui.profile.viewmodel.UserViewModel
import com.ku.lostandfound.ui.search.screen.SearchScreen
import com.ku.lostandfound.ui.signup.viewmodel.SignupViewmodel
import com.ku.lostandfound.viewmodel.PostWriteViewModel
import kotlinx.coroutines.launch

@Composable
fun MainNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val writeViewModel = remember { PostWriteViewModel() }
    val loginViewModel = viewModel<LoginViewModel>()
    val signupViewModel = viewModel<SignupViewmodel>()
    val userViewModel = viewModel<UserViewModel>()
    val lostItemViewModel = viewModel<LostItemViewModel>()
    val foundItemViewModel = viewModel<FoundItemViewModel>()
    val postCommentViewModel = viewModel<PostCommentViewModel>()

    val currentUserId = userViewModel.me?.id
    val currentUserName = userViewModel.me?.name ?: signupViewModel.name
    val currentUserEmail = userViewModel.me?.email ?: signupViewModel.email

    var boundary by remember { mutableStateOf<CampusBoundary?>(null) }
    var buildings by remember { mutableStateOf<List<CampusBuilding>>(emptyList()) }
    var referencePaths by remember { mutableStateOf<List<CampusPath>>(emptyList()) }
    var posts by remember { mutableStateOf<List<BoardPost>>(emptyList()) }
    var commentsByPostId by remember {
        mutableStateOf<Map<String, List<BoardComment>>>(emptyMap())
    }
    var lastAppliedFoundListVersion by remember { mutableStateOf(0) }
    var lastAppliedLostListVersion by remember { mutableStateOf(0) }

    val startDestination = remember {
        if (TokenManager.isLoggedIn()) Route.Found.route else Route.Login.route
    }

    LaunchedEffect(Unit) {
        val repo = CampusJsonRepository(context)
        boundary = repo.loadBoundary()
        buildings = repo.loadBuildings()
        referencePaths = repo.loadPaths()
        if (TokenManager.isLoggedIn()) {
            userViewModel.loadMe()
        }
        lostItemViewModel.loadLostItems()
        foundItemViewModel.loadFoundItems()
    }

    fun toggleResolved(post: BoardPost) {
        if (post.status != PostStatus.OPEN) return
        posts = posts.map { if (it.id == post.id) it.copy(status = PostStatus.RESOLVED) else it }
    }

    fun syncLocalPostsWithServer(type: PostType, serverPosts: List<BoardPost>) {
        val serverIds = serverPosts.map { it.id }.toSet()
        posts = posts.filter { cachedPost ->
            cachedPost.type != type ||
                (cachedPost.status == PostStatus.OPEN && cachedPost.id in serverIds)
        }
    }

    fun publicLocalPosts(type: PostType): List<BoardPost> {
        return posts.filter { it.type == type && it.status == PostStatus.OPEN }
    }

    fun addComment(postId: String, content: String) {
        if (content.isBlank()) return

        val newComment = BoardComment(
            id = System.currentTimeMillis().toString(),
            postId = postId,
            authorUserId = currentUserId,
            authorName = currentUserName,
            authorEmail = currentUserEmail,
            content = content.trim(),
            createdAtText = "방금 전"
        )

        val oldComments = commentsByPostId[postId].orEmpty()

        commentsByPostId = commentsByPostId + mapOf(
            postId to (oldComments + newComment)
        )
    }

    fun deleteLocalComment(postId: String, commentId: String) {
        commentsByPostId = commentsByPostId + (
            postId to commentsByPostId[postId].orEmpty().filterNot { it.id == commentId }
            )
    }

    fun updateLocalComment(postId: String, commentId: String, content: String) {
        val trimmedContent = content.trim()
        if (trimmedContent.isBlank()) return

        commentsByPostId = commentsByPostId + (
            postId to commentsByPostId[postId].orEmpty().map { comment ->
                if (comment.id == commentId) {
                    comment.copy(content = trimmedContent)
                } else {
                    comment
                }
            }
            )
    }

    suspend fun currentUserOrLoad(): UserMeData? {
        return userViewModel.me ?: userViewModel.loadMeNow()
    }

    fun submitPost() {
        if (!writeViewModel.canSubmit()) return

        if (writeViewModel.postType == PostType.LOST) {
            coroutineScope.launch {
                val author = currentUserOrLoad()
                val authorName = author?.name?.takeIf { it.isNotBlank() }
                    ?: currentUserName.ifBlank { "익명" }
                val authorEmail = author?.email?.takeIf { it.isNotBlank() }
                    ?: currentUserEmail
                writeViewModel.createLostItem(context)
                    .onSuccess { createdPost ->
                        posts = listOf(
                            createdPost.copy(
                                authorName = authorName,
                                authorEmail = authorEmail,
                            )
                        ) + posts
                        writeViewModel.reset()
                        navController.navigate(Route.Lost.route) {
                            popUpTo(Route.PostWrite.route) { inclusive = true }
                        }
                    }
            }
            return
        }

        coroutineScope.launch {
            val author = currentUserOrLoad()
            val authorName = author?.name?.takeIf { it.isNotBlank() }
                ?: currentUserName.ifBlank { "익명" }
            val authorEmail = author?.email?.takeIf { it.isNotBlank() }
                ?: currentUserEmail
            writeViewModel.createFoundItem(context)
                .onSuccess { createdPost ->
                    posts = listOf(
                        createdPost.copy(
                            authorName = authorName,
                            authorEmail = authorEmail,
                        )
                    ) + posts
                    writeViewModel.reset()
                    navController.navigate(Route.Found.route) {
                        popUpTo(Route.PostWrite.route) { inclusive = true }
                    }
                }
        }
    }

    fun navigateSingleTop(route: String) {
        if (navController.currentBackStackEntry?.destination?.route == route) return
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openPostWrite() {
        writeViewModel.reset()
        navigateSingleTop(Route.PostWrite.route)
    }

    fun openPostEdit(post: BoardPost) {
        writeViewModel.loadForEdit(post)
        navController.navigate(Route.PostEdit.create(post.id))
    }

    fun navigateMainTab(route: String) {
        if (navController.currentBackStackEntry?.destination?.route == route) return
        when (route) {
            Route.Found.route -> foundItemViewModel.loadFoundItems()
            Route.Lost.route -> lostItemViewModel.loadLostItems()
        }
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(Route.Found.route) {
                saveState = true
            }
        }
    }

    fun navigateToLoginClearingBackStack() {
        navController.navigate(Route.Login.route) {
            popUpTo(navController.graph.startDestinationId) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    fun MyLostItemData.toBoardPost(): BoardPost {
        return BoardPost(
            id = id.toString(),
            type = PostType.LOST,
            status = if (itemStatus == "RETURNED") PostStatus.RESOLVED else PostStatus.OPEN,
            title = title,
            category = kind,
            content = "분실물 상세 정보를 불러오려면 상세 조회 API가 필요합니다.",
            imageUri = imageUrl,
            createdAtText = createdAt.take(10),
        )
    }

    fun MyFoundItemData.toBoardPost(): BoardPost {
        return BoardPost(
            id = id.toString(),
            type = PostType.FOUND,
            status = if (itemStatus == "RETURNED") PostStatus.RESOLVED else PostStatus.OPEN,
            title = title,
            category = kind,
            content = associatedBuildingNames.joinToString(", "),
            imageUri = imageUrl,
            createdAtText = createdAt.take(10),
            associatedBuildingNames = associatedBuildingNames,
        )
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.padding(padding),
    ) {
        composable(route = Route.Login.route) {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    userViewModel.loadMe()
                    navController.navigate(Route.Found.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    loginViewModel.resetUiState()
                    navController.navigate(Route.SignupName.route)
                }
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
                    loginViewModel.resetUiState()
                    signupViewModel.reset()
                    navController.navigate(Route.Login.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Route.Found.route) {
            LaunchedEffect(Unit) {
                if (foundItemViewModel.selectedBuildingName == null) {
                    foundItemViewModel.loadFoundItems()
                }
            }
            val b = boundary
            if (b == null) {
                Text("지도 데이터를 불러오는 중입니다.")
            } else {
                val serverFoundPosts = foundItemViewModel.asBoardPosts()
                LaunchedEffect(foundItemViewModel.listVersion) {
                    if (foundItemViewModel.listVersion > lastAppliedFoundListVersion) {
                        syncLocalPostsWithServer(PostType.FOUND, serverFoundPosts)
                        lastAppliedFoundListVersion = foundItemViewModel.listVersion
                    }
                }
                val foundPosts = if (foundItemViewModel.selectedBuildingName != null) {
                    (serverFoundPosts + publicLocalPosts(PostType.FOUND)).distinctBy { it.id }
                } else {
                    (serverFoundPosts + publicLocalPosts(PostType.FOUND)).distinctBy { it.id }
                }
                FoundBoardScreen(
                    posts = foundPosts,
                    boundary = b,
                    buildings = buildings,
                    referencePaths = referencePaths,
                    onPostClick = { post ->
                        if (posts.none { it.id == post.id && it.type == post.type }) {
                            posts = listOf(post) + posts
                        }
                        navController.navigate(Route.PostDetail.create(post.id, post.type.name))
                    },
                    onAddClick = { openPostWrite() },
                    onFoundTabClick = { navigateMainTab(Route.Found.route) },
                    onLostTabClick = { navigateMainTab(Route.Lost.route) },
                    onProfileClick = { navigateMainTab(Route.Profile.route) },
                    onSearchClick = { navigateSingleTop(Route.Search.route) },
                    onBuildingSelected = { building ->
                        if (building == null) {
                            foundItemViewModel.loadFoundItems()
                        } else {
                            foundItemViewModel.loadFoundItemsByBuilding(building.name)
                        }
                    },
                )
            }
        }

        composable(route = Route.Lost.route) {
            LaunchedEffect(Unit) {
                lostItemViewModel.loadLostItems()
            }
            val serverLostPosts = lostItemViewModel.asBoardPosts()
            LaunchedEffect(lostItemViewModel.listVersion) {
                if (lostItemViewModel.listVersion > lastAppliedLostListVersion) {
                    syncLocalPostsWithServer(PostType.LOST, serverLostPosts)
                    lastAppliedLostListVersion = lostItemViewModel.listVersion
                }
            }
            val lostPosts = (serverLostPosts + publicLocalPosts(PostType.LOST)).distinctBy { it.id }
            LostBoardScreen(
                posts = lostPosts,
                onPostClick = { post ->
                    if (posts.none { it.id == post.id && it.type == post.type }) {
                        posts = listOf(post) + posts
                    }
                    navController.navigate(Route.PostDetail.create(post.id, post.type.name))
                },
                onAddClick = { openPostWrite() },
                onFoundTabClick = { navigateMainTab(Route.Found.route) },
                onLostTabClick = { navigateMainTab(Route.Lost.route) },
                onProfileClick = { navigateMainTab(Route.Profile.route) },
                onSearchClick = { navigateSingleTop(Route.Search.route) }
            )
        }

        composable(route = Route.Profile.route) {
            LaunchedEffect(Unit) {
                userViewModel.loadMe()
                userViewModel.loadMyPostCounts()
            }
            val userMe = userViewModel.me
            val userName = userMe?.name ?: currentUserName
            val userEmail = userMe?.email ?: currentUserEmail
            val myPosts = posts.filter { it.authorEmail == userEmail || it.authorEmail == currentUserEmail }
            val myPostCount = myPosts.size
            ProfileScreen(
                userName = userName,
                userEmail = userEmail,
                myPosts = myPosts,
                myPostCount = myPostCount,
                myLostPostCount = userViewModel.myLostCount,
                myFoundPostCount = userViewModel.myFoundCount,
                myPostCountState = userViewModel.myPostCountState,
                onPostClick = { navController.navigate(Route.PostDetail.create(it.id, it.type.name)) },
                onShowAllClick = { navController.navigate(Route.MyLostPosts.route) },
                onMyLostPostsClick = { navController.navigate(Route.MyLostPosts.route) },
                onMyFoundPostsClick = { navController.navigate(Route.MyFoundPosts.route) },
                onLogoutClick = {
                    loginViewModel.logout {
                        userViewModel.clearSession()
                        postCommentViewModel.clear()
                        posts = emptyList()
                        navigateToLoginClearingBackStack()
                    }
                },
                onWithdrawClick = {
                    onSuccess ->
                    loginViewModel.withdraw {
                        onSuccess()
                    }
                },
                onWithdrawCompleteConfirm = {
                    userViewModel.clearSession()
                    postCommentViewModel.clear()
                    posts = emptyList()
                    navigateToLoginClearingBackStack()
                },
                onHomeClick = { navigateMainTab(Route.Found.route) },
                onSearchClick = { navigateSingleTop(Route.Search.route) },
                selectedType = PostType.FOUND,
                onFoundTabClick = { navigateMainTab(Route.Found.route) },
                onLostTabClick = { navigateMainTab(Route.Lost.route) },
                onAddClick = { openPostWrite() },
            )
        }

        composable(route = Route.Search.route) {
            val searchablePosts = (
                lostItemViewModel.asBoardPosts() +
                    foundItemViewModel.asBoardPosts() +
                    posts.filter { it.status == PostStatus.OPEN }
                ).filter { it.status == PostStatus.OPEN }
                .distinctBy { it.type to it.id }
            SearchScreen(
                posts = searchablePosts,
                onBackClick = { navController.popBackStack() },
                onPostClick = { post ->
                    if (posts.none { it.id == post.id && it.type == post.type }) {
                        posts = listOf(post) + posts
                    }
                    navController.navigate(Route.PostDetail.create(post.id, post.type.name))
                }
            )
        }

        composable(route = Route.MyLostPosts.route) {
            LaunchedEffect(Unit) {
                userViewModel.loadMyLostItems()
            }
            MyPostsScreen(
                type = MyPostsType.LOST,
                lostItems = userViewModel.myLostItems,
                foundItems = emptyList(),
                uiState = userViewModel.myLostItemsState,
                onBackClick = { navController.popBackStack() },
                onLostItemClick = { item ->
                    val post = item.toBoardPost().copy(authorName = currentUserName, authorEmail = currentUserEmail)
                    posts = listOf(post) + posts.filterNot { it.id == post.id && it.type == post.type }
                    navController.navigate(Route.PostDetail.create(post.id, post.type.name))
                },
                onFoundItemClick = {},
            )
        }

        composable(route = Route.MyFoundPosts.route) {
            LaunchedEffect(Unit) {
                userViewModel.loadMyFoundItems()
            }
            MyPostsScreen(
                type = MyPostsType.FOUND,
                lostItems = emptyList(),
                foundItems = userViewModel.myFoundItems,
                uiState = userViewModel.myFoundItemsState,
                onBackClick = { navController.popBackStack() },
                onLostItemClick = {},
                onFoundItemClick = { item ->
                    val post = item.toBoardPost().copy(authorName = currentUserName, authorEmail = currentUserEmail)
                    posts = listOf(post) + posts.filterNot { it.id == post.id && it.type == post.type }
                    navController.navigate(Route.PostDetail.create(post.id, post.type.name))
                },
            )
        }

        composable(route = Route.PostWrite.route) {
            PostWriteScreen(
                viewModel = writeViewModel,
                onBackClick = {
                    writeViewModel.reset()
                    navController.popBackStack()
                },
                onAddLocationClick = { postType -> navController.navigate(Route.LocationPicker.create(postType.name)) },
                onSubmitClick = { submitPost() },
            )
        }

        composable(
            route = Route.PostEdit.route,
            arguments = listOf(navArgument(Route.PostEdit.ARG_POST_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString(Route.PostEdit.ARG_POST_ID).orEmpty()
            val itemId = postId.toLongOrNull()
            PostWriteScreen(
                viewModel = writeViewModel,
                title = "게시글 수정",
                submitText = "수정하기",
                loadingText = "수정 중...",
                allowTypeChange = false,
                onBackClick = {
                    writeViewModel.reset()
                    navController.popBackStack()
                },
                onAddLocationClick = { postType -> navController.navigate(Route.LocationPicker.create(postType.name)) },
                onSubmitClick = {
                    if (!writeViewModel.canSubmit() || itemId == null) return@PostWriteScreen
                    coroutineScope.launch {
                        writeViewModel.updatePost(context, itemId)
                            .onSuccess { updatedPost ->
                                posts = listOf(updatedPost) + posts.filterNot { it.id == updatedPost.id && it.type == updatedPost.type }
                                if (updatedPost.type == PostType.LOST) {
                                    lostItemViewModel.loadLostItemDetail(itemId)
                                    lostItemViewModel.loadLostItems()
                                } else {
                                    foundItemViewModel.loadFoundItemDetail(itemId)
                                    foundItemViewModel.loadFoundItems()
                                }
                                writeViewModel.reset()
                                navController.navigate(Route.PostDetail.create(updatedPost.id, updatedPost.type.name)) {
                                    popUpTo(Route.PostEdit.route) { inclusive = true }
                                }
                            }
                    }
                },
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
            arguments = listOf(
                navArgument(Route.PostDetail.ARG_POST_TYPE) { type = NavType.StringType },
                navArgument(Route.PostDetail.ARG_POST_ID) { type = NavType.StringType },
            )
        ) { backStackEntry ->
            val b = boundary
            val routePostType = backStackEntry.arguments
                ?.getString(Route.PostDetail.ARG_POST_TYPE)
                ?.let { runCatching { PostType.valueOf(it) }.getOrNull() }
            val postId = backStackEntry.arguments?.getString(Route.PostDetail.ARG_POST_ID).orEmpty()
            val localPost = posts.firstOrNull { it.id == postId && (routePostType == null || it.type == routePostType) }
            val numericItemId = postId.toLongOrNull()
            var isDetailLoading by remember(postId) { mutableStateOf(false) }
            var isMatchLoading by remember(postId) { mutableStateOf(false) }
            var isCommentsLoading by remember(postId) { mutableStateOf(false) }
            val detailType = routePostType ?: localPost?.type
            LaunchedEffect(numericItemId, detailType) {
                if (numericItemId != null) {
                    when (detailType) {
                        PostType.FOUND -> {
                            isDetailLoading = true
                            foundItemViewModel.loadFoundItemDetailNow(numericItemId)
                            isDetailLoading = false
                            isMatchLoading = true
                            foundItemViewModel.loadFoundItemMatchesNow(numericItemId)
                            isMatchLoading = false
                            isCommentsLoading = true
                            postCommentViewModel.loadCommentsNow(numericItemId, PostType.FOUND)
                            isCommentsLoading = false
                        }
                        else -> {
                            isDetailLoading = true
                            lostItemViewModel.loadLostItemDetailNow(numericItemId)
                            isDetailLoading = false
                            isMatchLoading = true
                            lostItemViewModel.loadMatchesNow(numericItemId)
                            isMatchLoading = false
                            isCommentsLoading = true
                            postCommentViewModel.loadCommentsNow(numericItemId, PostType.LOST)
                            isCommentsLoading = false
                        }
                    }
                }
            }
            val post = if (numericItemId != null && detailType == PostType.FOUND) {
                foundItemViewModel.detailPost
                    ?.takeIf { it.id == postId }
                    ?.let { serverPost ->
                        serverPost.copy(
                            authorName = localPost?.authorName?.takeIf { it != "익명" } ?: serverPost.authorName,
                            authorEmail = localPost?.authorEmail ?: serverPost.authorEmail,
                        )
                    }
                    ?: localPost
            } else if (numericItemId != null) {
                lostItemViewModel.detailPost
                    ?.takeIf { it.id == postId }
                    ?.let { serverPost ->
                        serverPost.copy(
                            authorName = localPost?.authorName?.takeIf { it != "익명" } ?: serverPost.authorName,
                            authorEmail = localPost?.authorEmail ?: serverPost.authorEmail,
                        )
                    }
                    ?: localPost
            } else {
                localPost
            }

            if (b == null) {
                Text("지도 데이터를 불러오는 중입니다.")
            } else if (post == null) {
                Text("게시글을 찾을 수 없습니다.")
            } else {
                val matchCandidates = when (post.type) {
                    PostType.LOST -> lostItemViewModel.matches.take(10).map { match ->
                        MatchCandidateUiModel(
                            id = match.foundItem.id.toString(),
                            type = PostType.FOUND,
                            title = match.foundItem.title,
                            category = match.foundItem.kind,
                            status = if (match.foundItem.itemStatus == "RETURNED") {
                                PostStatus.RESOLVED
                            } else {
                                PostStatus.OPEN
                            },
                            createdAtText = match.foundItem.createdAt.take(10),
                            imageUrl = match.foundItem.imageUrl,
                            locationScore = match.locationScore,
                            associatedBuildingNames = match.foundItem.associatedBuildingNames,
                        )
                    }
                    PostType.FOUND -> {
                        val state = foundItemViewModel.matchUiState
                        if (state is FoundItemMatchUiState.Completed) {
                            state.matches.take(10).map { match ->
                                MatchCandidateUiModel(
                                    id = match.lostItem.id.toString(),
                                    type = PostType.LOST,
                                    title = match.lostItem.title,
                                    category = match.lostItem.kind,
                                    status = if (match.lostItem.itemStatus.name == "RETURNED") {
                                        PostStatus.RESOLVED
                                    } else {
                                        PostStatus.OPEN
                                    },
                                    createdAtText = match.lostItem.createdAt.take(10),
                                    imageUrl = match.lostItem.imageUrl,
                                    locationScore = match.locationScore,
                                )
                            }
                        } else {
                            emptyList()
                        }
                    }
                }
                val displayPost = if (currentUserId != null && post.authorUserId == currentUserId) {
                    post.copy(authorName = currentUserName, authorEmail = currentUserEmail)
                } else {
                    post
                }
                PostDetailScreen(
                    post = displayPost,
                    boundary = b,
                    buildings = buildings,
                    referencePaths = referencePaths,
                    onBackClick = { navController.popBackStack() },
                    showOwnerActions = (currentUserId != null && post.authorUserId == currentUserId) ||
                        (post.authorEmail.isNotBlank() && post.authorEmail == currentUserEmail) ||
                        (currentUserName.isNotBlank() && post.authorName == currentUserName),
                    onEditClick = { openPostEdit(it) },
                    onToggleResolvedClick = {
                        val itemId = it.id.toLongOrNull()
                        if (it.type == PostType.LOST && itemId != null && it.status == PostStatus.OPEN) {
                            lostItemViewModel.markReturned(itemId) { updatedPost ->
                                posts = posts.filterNot { existing -> existing.id == updatedPost.id && existing.type == updatedPost.type }
                            }
                        } else if (it.type == PostType.FOUND && itemId != null && it.status == PostStatus.OPEN) {
                            foundItemViewModel.updateFoundItemStatus(itemId) { updatedPost ->
                                posts = posts.filterNot { existing -> existing.id == updatedPost.id && existing.type == updatedPost.type }
                            }
                        } else {
                            toggleResolved(it)
                        }
                    },
                    onDeleteClick = {
                        val itemId = it.id.toLongOrNull() ?: return@PostDetailScreen
                        if (it.type == PostType.LOST) {
                            lostItemViewModel.deleteLostItem(itemId) {
                                posts = posts.filterNot { existing -> existing.id == it.id && existing.type == it.type }
                                navController.popBackStack()
                            }
                        } else {
                            foundItemViewModel.deleteFoundItem(itemId) {
                                posts = posts.filterNot { existing -> existing.id == it.id && existing.type == it.type }
                                navController.popBackStack()
                            }
                        }
                    },
                    comments = if (numericItemId != null) {
                        postCommentViewModel.commentsFor(numericItemId, post.type)
                    } else {
                        commentsByPostId[post.id].orEmpty()
                    },
                    currentUserName = currentUserName,
                    currentUserEmail = currentUserEmail,
                    currentUserId = currentUserId,
                    onAddComment = { content ->
                        if (numericItemId != null) {
                            postCommentViewModel.addComment(numericItemId, post.type, content)
                        } else {
                            addComment(post.id, content)
                        }
                    },
                    matchCandidates = matchCandidates,
                    isDetailLoading = isDetailLoading,
                    isMatchLoading = isMatchLoading,
                    isCommentsLoading = isCommentsLoading,
                    onDeleteComment = { comment ->
                        val itemId = post.id.toLongOrNull()
                        val commentId = comment.id.toLongOrNull()
                        if (itemId != null && commentId != null) {
                            postCommentViewModel.deleteComment(itemId, post.type, commentId)
                        } else {
                            deleteLocalComment(post.id, comment.id)
                        }
                    },
                    onUpdateComment = { comment, content ->
                        val itemId = post.id.toLongOrNull()
                        val commentId = comment.id.toLongOrNull()
                        if (itemId != null && commentId != null) {
                            postCommentViewModel.updateComment(itemId, post.type, commentId, content)
                        } else {
                            updateLocalComment(post.id, comment.id, content)
                        }
                    },
                    onMatchCandidateClick = { candidate ->
                        val candidatePost = BoardPost(
                            id = candidate.id,
                            type = candidate.type,
                            status = candidate.status,
                            title = candidate.title,
                            category = candidate.category,
                            content = "상세 정보를 불러오는 중입니다.",
                            imageUri = candidate.imageUrl,
                            createdAtText = candidate.createdAtText,
                            associatedBuildingNames = candidate.associatedBuildingNames,
                        )
                        posts = listOf(candidatePost) + posts.filterNot { it.id == candidate.id && it.type == candidate.type }
                        navController.navigate(Route.PostDetail.create(candidate.id, candidate.type.name))
                    },
                )
            }
        }
    }
}
