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

    val currentUserName = userViewModel.me?.name ?: signupViewModel.name.ifBlank { "김건국" }
    val currentUserEmail = userViewModel.me?.email ?: signupViewModel.email.ifBlank { "konkuk26@konkuk.ac.kr" }

    var boundary by remember { mutableStateOf<CampusBoundary?>(null) }
    var buildings by remember { mutableStateOf<List<CampusBuilding>>(emptyList()) }
    var referencePaths by remember { mutableStateOf<List<CampusPath>>(emptyList()) }
    var posts by remember { mutableStateOf<List<BoardPost>>(emptyList()) }
    var commentsByPostId by remember {
        mutableStateOf<Map<String, List<BoardComment>>>(emptyMap())
    }

    LaunchedEffect(Unit) {
        val repo = CampusJsonRepository(context)
        boundary = repo.loadBoundary()
        buildings = repo.loadBuildings()
        referencePaths = repo.loadPaths()
        lostItemViewModel.loadLostItems()
        foundItemViewModel.loadFoundItems()
    }

    fun toggleResolved(post: BoardPost) {
        if (post.status != PostStatus.OPEN) return
        posts = posts.map { if (it.id == post.id) it.copy(status = PostStatus.RESOLVED) else it }
    }

    fun addComment(postId: String, content: String) {
        if (content.isBlank()) return

        val newComment = BoardComment(
            id = System.currentTimeMillis().toString(),
            postId = postId,
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

    fun submitPost() {
        if (!writeViewModel.canSubmit()) return

        if (writeViewModel.postType == PostType.LOST) {
            coroutineScope.launch {
                writeViewModel.createLostItem(context)
                    .onSuccess { createdPost ->
                        posts = listOf(
                            createdPost.copy(
                                authorName = currentUserName,
                                authorEmail = currentUserEmail,
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
            writeViewModel.createFoundItem(context)
                .onSuccess { createdPost ->
                    posts = listOf(
                        createdPost.copy(
                            authorName = currentUserName,
                            authorEmail = currentUserEmail,
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

    fun navigateMainTab(route: String) {
        if (navController.currentBackStackEntry?.destination?.route == route) return
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(Route.Found.route) {
                saveState = true
            }
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
        startDestination = Route.Login.route,
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
                val foundPosts = if (foundItemViewModel.selectedBuildingName != null) {
                    (serverFoundPosts + posts.filter { it.type == PostType.FOUND }).distinctBy { it.id }
                } else {
                    (serverFoundPosts + posts.filter { it.type == PostType.FOUND }).distinctBy { it.id }
                }
                FoundBoardScreen(
                    posts = foundPosts,
                    boundary = b,
                    buildings = buildings,
                    referencePaths = referencePaths,
                    onPostClick = { post ->
                        if (posts.none { it.id == post.id }) {
                            posts = listOf(post) + posts
                        }
                        navController.navigate(Route.PostDetail.create(post.id))
                    },
                    onAddClick = { navigateSingleTop(Route.PostWrite.route) },
                    onFoundTabClick = { navigateMainTab(Route.Found.route) },
                    onLostTabClick = { navigateMainTab(Route.Lost.route) },
                    onProfileClick = { navigateMainTab(Route.Profile.route) },
                    onSearchClick = { navigateSingleTop(Route.Search.route) },
                    onBuildingSelected = { building ->
                        if (building == null) {
                            foundItemViewModel.clearBuildingFilter()
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
            val lostPosts = (serverLostPosts + posts.filter { it.type == PostType.LOST }).distinctBy { it.id }
            LostBoardScreen(
                posts = lostPosts,
                onPostClick = { post ->
                    if (posts.none { it.id == post.id }) {
                        posts = listOf(post) + posts
                    }
                    navController.navigate(Route.PostDetail.create(post.id))
                },
                onAddClick = { navigateSingleTop(Route.PostWrite.route) },
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
                onPostClick = { navController.navigate(Route.PostDetail.create(it.id)) },
                onShowAllClick = { navController.navigate(Route.MyLostPosts.route) },
                onMyLostPostsClick = { navController.navigate(Route.MyLostPosts.route) },
                onMyFoundPostsClick = { navController.navigate(Route.MyFoundPosts.route) },
                onLogoutClick = {
                    loginViewModel.logout {
                        navController.navigate(Route.Login.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                },
                onWithdrawClick = {
                    onSuccess ->
                    loginViewModel.withdraw {
                        onSuccess()
                    }
                },
                onWithdrawCompleteConfirm = {
                    navController.navigate(Route.Login.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onSearchClick = { navigateSingleTop(Route.Search.route) },
                selectedType = PostType.FOUND,
                onFoundTabClick = { navigateMainTab(Route.Found.route) },
                onLostTabClick = { navigateMainTab(Route.Lost.route) },
                onAddClick = { navigateSingleTop(Route.PostWrite.route) },
            )
        }

        composable(route = Route.Search.route) {
            val searchablePosts = (
                lostItemViewModel.asBoardPosts() +
                    foundItemViewModel.asBoardPosts() +
                    posts
                ).distinctBy { it.id }
            SearchScreen(
                posts = searchablePosts,
                onBackClick = { navController.popBackStack() },
                onPostClick = { post ->
                    if (posts.none { it.id == post.id }) {
                        posts = listOf(post) + posts
                    }
                    navController.navigate(Route.PostDetail.create(post.id))
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
                    posts = listOf(post) + posts.filterNot { it.id == post.id }
                    navController.navigate(Route.PostDetail.create(post.id))
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
                    posts = listOf(post) + posts.filterNot { it.id == post.id }
                    navController.navigate(Route.PostDetail.create(post.id))
                },
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
            val localPost = posts.firstOrNull { it.id == postId }
            val numericItemId = postId.toLongOrNull()
            var isDetailLoading by remember(postId) { mutableStateOf(false) }
            var isMatchLoading by remember(postId) { mutableStateOf(false) }
            var isCommentsLoading by remember(postId) { mutableStateOf(false) }
            LaunchedEffect(numericItemId, localPost?.type) {
                if (numericItemId != null) {
                    when (localPost?.type) {
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
            val post = if (numericItemId != null && localPost?.type == PostType.FOUND) {
                foundItemViewModel.detailPost
                    ?.takeIf { it.id == postId }
                    ?.copy(
                        authorName = localPost.authorName,
                        authorEmail = localPost.authorEmail,
                    )
                    ?: localPost
            } else if (numericItemId != null) {
                lostItemViewModel.detailPost
                    ?.takeIf { it.id == postId }
                    ?.copy(
                        authorName = localPost?.authorName ?: "",
                        authorEmail = localPost?.authorEmail ?: "",
                    )
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
                            id = "found-${match.foundItem.id}",
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
                                    id = "lost-${match.lostItem.id}",
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
                PostDetailScreen(
                    post = post,
                    boundary = b,
                    buildings = buildings,
                    referencePaths = referencePaths,
                    onBackClick = { navController.popBackStack() },
                    showOwnerActions = post.authorEmail == currentUserEmail,
                    onToggleResolvedClick = {
                        val itemId = it.id.toLongOrNull()
                        if (it.type == PostType.LOST && itemId != null && it.status == PostStatus.OPEN) {
                            lostItemViewModel.markReturned(itemId) { updatedPost ->
                                posts = posts.map { existing ->
                                    if (existing.id == updatedPost.id) {
                                        updatedPost.copy(
                                            authorName = existing.authorName,
                                            authorEmail = existing.authorEmail,
                                        )
                                    } else {
                                        existing
                                    }
                                }
                            }
                        } else if (it.type == PostType.FOUND && itemId != null && it.status == PostStatus.OPEN) {
                            foundItemViewModel.updateFoundItemStatus(itemId) { updatedPost ->
                                posts = posts.map { existing ->
                                    if (existing.id == updatedPost.id) {
                                        updatedPost.copy(
                                            authorName = existing.authorName,
                                            authorEmail = existing.authorEmail,
                                        )
                                    } else {
                                        existing
                                    }
                                }
                            }
                        } else {
                            toggleResolved(it)
                        }
                    },
                    onDeleteClick = {
                        val itemId = it.id.toLongOrNull() ?: return@PostDetailScreen
                        if (it.type == PostType.LOST) {
                            lostItemViewModel.deleteLostItem(itemId) {
                                posts = posts.filterNot { existing -> existing.id == it.id }
                                navController.popBackStack()
                            }
                        } else {
                            foundItemViewModel.deleteFoundItem(itemId) {
                                posts = posts.filterNot { existing -> existing.id == it.id }
                                navController.popBackStack()
                            }
                        }
                    },
                    comments = if (numericItemId != null) {
                        postCommentViewModel.commentsByPostId[post.id].orEmpty()
                    } else {
                        commentsByPostId[post.id].orEmpty()
                    },
                    currentUserName = currentUserName,
                    currentUserEmail = currentUserEmail,
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
                        }
                    },
                )
            }
        }
    }
}
