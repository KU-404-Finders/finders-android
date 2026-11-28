package com.ku.lostandfound.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ku.lostandfound.network.ApiResponse
import com.ku.lostandfound.network.BlockResponse
import com.ku.lostandfound.network.ChatMessageResponse
import com.ku.lostandfound.network.ChatRoomResponse
import com.ku.lostandfound.network.CreateReportRequest
import com.ku.lostandfound.network.ModerationApiService
import com.ku.lostandfound.network.RequestSession
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.ui.chat.screen.ChatListScreen
import com.ku.lostandfound.ui.chat.screen.ChatRoomScreen
import com.ku.lostandfound.ui.profile.screen.BlockedUsersScreen
import com.ku.lostandfound.ui.profile.screen.ProfileScreen
import com.ku.lostandfound.ui.profile.viewmodel.BlockedUsersViewModel
import com.ku.lostandfound.ui.theme.KUfindersTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.io.File

/**
 * Renders the screens changed for iOS parity with fixture data. Assertions always run;
 * screenshots are written to the app cache only with `-e captureParityUi true`.
 */
@RunWith(AndroidJUnit4::class)
class UiParityCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val capture = InstrumentationRegistry.getArguments().getString("captureParityUi") == "true"

    private fun show(name: String, content: @Composable () -> Unit) {
        compose.setContent { KUfindersTheme { content() } }
        shot(name)
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        if (capture) {
            // A dialog has its own window; capture it on its own when one is open.
            val dialog = compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()
            val node = if (dialog) compose.onNode(isDialog()) else compose.onAllNodes(isRoot())[0]
            val shot = node.captureToImage().asAndroidBitmap()
            File(compose.activity.cacheDir, "parity-$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun blockedUsersShowsCardsDateAndConfirmDialog() {
        TokenManager.init(compose.activity)
        assumeFalse(TokenManager.isLoggedIn())
        check(TokenManager.saveNewSessionTokens(TokenManager.sessionGeneration(), "fixture-a", "fixture-r"))
        val unblocked = mutableListOf<Long>()
        val api = object : ModerationApiService {
            override suspend fun reportReasons() = error("unused")
            override suspend fun report(request: CreateReportRequest) = error("unused")
            override suspend fun myReports() = error("unused")
            override suspend fun block(userId: Long, session: RequestSession?) = error("unused")
            override suspend fun unblock(userId: Long, session: RequestSession?): Response<ApiResponse<Unit>> {
                unblocked += userId
                return Response.success(ApiResponse(true, "ok"))
            }
            override suspend fun blockedUsers(page: Int, session: RequestSession?) = Response.success(ApiResponse(true, "ok", listOf(
                BlockResponse(11, "2026-11-20T09:30:00", "김건국"),
                BlockResponse(12, null, "이름이아주긴사용자이름이아주긴사용자이름이아주긴사용자"),
            )))
        }
        try {
            val vm = BlockedUsersViewModel(compose.activity.application, api)
            show("blocked-users") { BlockedUsersScreen(vm, accountId = 1L, onBack = {}, onUnblocked = {}) }
            compose.onNodeWithText("차단을 해제하면 그 사용자의 게시글이 다시 보이고 서로 채팅할 수 있습니다.").assertIsDisplayed()
            compose.onNodeWithText("차단일 2026.11.20 09:30").assertIsDisplayed()
            // No date from the server: nothing is invented.
            assertEquals(1, compose.onAllNodesWithText("차단일", substring = true).fetchSemanticsNodes().size)
            compose.onAllNodesWithText("차단 해제")[0].assertIsEnabled().performClick()
            compose.onNodeWithText("차단을 해제하시겠습니까?").assertIsDisplayed()
            compose.onNodeWithText("해당 사용자의 게시글이 다시 보이고 서로 채팅할 수 있게 됩니다.").assertIsDisplayed()
            compose.onAllNodesWithText("차단 해제").fetchSemanticsNodes().size.let { check(it >= 2) }
            compose.onAllNodesWithText("차단 해제").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
            compose.waitUntil(5_000) { unblocked == listOf(11L) }
        } finally {
            TokenManager.clear()
        }
    }

    @Test fun profileUsesGroupedSettingsSections() {
        show("profile") {
            ProfileScreen(
                userName = "김건국", userEmail = "student@konkuk.ac.kr", myPosts = emptyList(),
                myLostPostCount = 3, myFoundPostCount = 12,
                onPostClick = {}, onShowAllClick = {}, onLogoutClick = {}, onWithdrawClick = {},
                onWithdrawCompleteConfirm = {}, onFoundTabClick = {}, onLostTabClick = {}, onAddClick = {},
                showAdminMenu = true,
            )
        }
        listOf("알림 설정", "분실물 예방 알림", "유사도 표시 모드", "차단한 사용자", "관리자 페이지", "개인정보 처리방침", "로그아웃", "탈퇴하기").forEach {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(it))
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        shot("profile-bottom")
    }

    @Test fun chatListUsesIosRows() {
        val rooms = listOf(
            ChatRoomResponse(1, 1, 2, "2026-11-27T10:00:00", postType = "LOST", postTitle = "검은 지갑을 찾습니다",
                lastMessage = "혹시 공학관에서 보셨나요?", lastMessageAt = "2026-11-27T10:00:00", unreadMessageCount = 3),
            ChatRoomResponse(2, 1, 3, "2026-11-20T10:00:00", postType = "FOUND", postTitle = "에어팟 습득",
                lastMessage = "감사합니다!", lastMessageAt = "2026-11-20T10:00:00"),
        )
        show("chat-list") {
            ChatListScreen(rooms = rooms, isLoading = false, errorMessage = null, onProfileClick = {}, onSearchClick = {},
                onNotificationClick = {}, notificationCount = 0, onRoomClick = {})
        }
        compose.onNodeWithText("분실물").assertIsDisplayed()
    }

    @Test fun chatRoomShowsReadStateBesideBubbles() {
        val messages = listOf(
            ChatMessageResponse(1, 1, 2, "안녕하세요, 지갑 주우셨나요?", "2026-11-26T10:00:00"),
            ChatMessageResponse(2, 1, 1, "네 공학관 1층에서 주웠어요.", "2026-11-27T10:01:00", unreadCount = 0),
            ChatMessageResponse(3, 1, 1, "어디서 드릴까요?", "2026-11-27T10:02:00", unreadCount = 1),
        )
        show("chat-room") {
            ChatRoomScreen(roomId = 1, roomTitle = "검은 지갑을 찾습니다", messages = messages, currentUserId = 1,
                isLoading = false, errorMessage = null, onBackClick = {}, isConnected = true, connectionMessage = null,
                onSendMessage = { true })
        }
        compose.onNodeWithText("읽음").assertIsDisplayed()
        compose.onNodeWithText("안읽음").assertIsDisplayed()
    }

    @Test fun adminReportListHasStatusChips() {
        var status: String? = "RECEIVED"
        val report = com.ku.lostandfound.ui.admin.screen.AdminReportUiModel(
            id = "7", reporterName = "김건국", reportedUserName = "탈퇴한 사용자", postTitle = "에어팟 습득했습니다",
            reason = "허위 정보", createdAt = "2026.11.27 10:00", isProcessed = false, postId = 31, postKind = "습득물",
            detail = "사진과 내용이 다릅니다.",
        )
        show("admin-reports") {
            com.ku.lostandfound.ui.admin.screen.AdminReportListScreen(
                onRetry = {}, reports = listOf(report, report.copy(id = "8", isProcessed = true)), hasNext = false, onMore = {},
                onBackClick = {}, onReportClick = {}, status = status, onStatusChange = { status = it })
        }
        listOf("접수된 신고", "전체", "반려", "조치 완료").forEach { compose.onAllNodesWithText(it)[0].assertIsDisplayed() }
        compose.onNodeWithText("조치 완료").performClick()
        compose.runOnIdle { assertEquals("ACTION_TAKEN", status) }
    }

    @Test fun adminDashboardShowsAllCounts() {
        show("admin-dashboard") {
            com.ku.lostandfound.ui.admin.screen.AdminDashboardScreen(
                dashboard = com.ku.lostandfound.network.AdminDashboard(3, 120, 45, 61), onBackClick = {})
        }
        listOf("미처리 3건", "회원 120명", "분실 45 · 습득 61").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun adminReportDetailSendsTheMemo() {
        val sent = mutableListOf<Pair<String, String>>()
        val report = com.ku.lostandfound.ui.admin.screen.AdminReportUiModel(
            id = "7", reporterName = "김건국", reportedUserName = "이건국", postTitle = "에어팟 습득했습니다",
            reason = "허위 정보", createdAt = "2026.11.27 10:00", isProcessed = false, postId = 31, postKind = "습득물",
            detail = "사진과 내용이 다릅니다.",
        )
        show("admin-report-detail") {
            com.ku.lostandfound.ui.admin.screen.AdminReportDetailScreen(report = report, onRetry = {}, onBackClick = {},
                onActionConfirmed = { action, note -> sent += action to note })
        }
        compose.onNodeWithText("게시글 ID").assertIsDisplayed()
        compose.onNodeWithText("상세 내용").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("신고 반려"))
        compose.onNodeWithText("신고 반려").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("중복 신고")
        shot("admin-report-dialog")
        compose.onNodeWithText("처리").performClick()
        compose.runOnIdle { assertEquals(listOf("신고 반려" to "중복 신고"), sent) }
    }

    @Test fun adminUserDetailUsesIosCopyAndSendsReason() {
        val user = com.ku.lostandfound.network.AdminUser(5, "student@konkuk.ac.kr", "김건국", "USER", true,
            suspensionReason = null, suspendedAt = "2026-11-20T09:30:00", createdAt = "2026-03-02T10:00:00")
        var change: Pair<Boolean, String>? = null
        show("admin-user-detail") {
            com.ku.lostandfound.ui.admin.screen.AdminUserDetailScreen(user = user, onRetry = {}, isLoading = false,
                errorMessage = null, onBackClick = {}, onSuspensionChange = { s, r -> change = s to r })
        }
        listOf("회원 상세", "일반 회원", "이용 제한 정보", "기록 없음", "회원 ID").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("이용 제한 해제").performClick()
        compose.onNodeWithText("이 회원의 이용 제한을 해제하시겠습니까?").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("오탐")
        compose.onNodeWithText("확인").performClick()
        compose.runOnIdle { assertEquals(false to "오탐", change) }
    }

    @Test fun adminPostListShowsBadgesAndDeleteCapsule() {
        val posts = listOf(
            com.ku.lostandfound.network.AdminPost("FOUND", 1, "에어팟 습득", "", 2, "김건국", itemStatus = "SEARCHING", createdAt = "2026-11-27T10:00:00"),
            com.ku.lostandfound.network.AdminPost("FOUND", 2, "학생증", "", 3, "", itemStatus = "RETURNED", createdAt = "2026-11-26T10:00:00"),
        )
        show("admin-posts") {
            com.ku.lostandfound.ui.admin.screen.AdminPostListScreen(posts = posts, hasNext = false, onMore = {}, postType = "FOUND",
                isLoading = false, errorMessage = null, onBackClick = {}, onTypeChange = {}, onDelete = {})
        }
        compose.onNodeWithText("작성자 탈퇴한 사용자", substring = true).assertIsDisplayed()
        compose.onNodeWithText("해결됨").assertIsDisplayed()
        compose.onAllNodesWithText("삭제")[0].performClick()
        compose.onNodeWithText("댓글·매칭도 함께 정리되며 되돌릴 수 없습니다.", substring = true).assertIsDisplayed()
        shot("admin-post-delete-dialog")
    }

    @Test fun adminAuditLogUsesReadableLabels() {
        val audits = listOf(
            com.ku.lostandfound.network.AdminAudit(1, 9, "DELETE_POST", "POST", 31, postType = "LOST", note = "허위 게시글", createdAt = "2026-11-27T10:00:00"),
            com.ku.lostandfound.network.AdminAudit(2, 9, "RESTORE_USER", "USER", 5, createdAt = "2026-11-26T10:00:00"),
        )
        show("admin-audits") {
            com.ku.lostandfound.ui.admin.screen.AdminAuditLogScreen(hasNext = false, onMore = {}, onRetry = {}, audits = audits,
                isLoading = false, errorMessage = null, onBackClick = {})
        }
        listOf("게시글 삭제", "분실물 게시글 #31 · 관리자 #9", "이용 제한 해제", "회원 #5 · 관리자 #9").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test fun blockedUsersDialogCapture() {
        TokenManager.init(compose.activity)
        assumeFalse(TokenManager.isLoggedIn())
        check(TokenManager.saveNewSessionTokens(TokenManager.sessionGeneration(), "fixture-a", "fixture-r"))
        val api = object : ModerationApiService {
            override suspend fun reportReasons() = error("unused")
            override suspend fun report(request: CreateReportRequest) = error("unused")
            override suspend fun myReports() = error("unused")
            override suspend fun block(userId: Long, session: RequestSession?) = error("unused")
            override suspend fun unblock(userId: Long, session: RequestSession?): Response<ApiResponse<Unit>> = error("unused")
            override suspend fun blockedUsers(page: Int, session: RequestSession?) =
                Response.success(ApiResponse(true, "ok", listOf(BlockResponse(11, "2026-11-20T09:30:00", "김건국"))))
        }
        try {
            val vm = BlockedUsersViewModel(compose.activity.application, api)
            show("blocked-users-base") { BlockedUsersScreen(vm, accountId = 1L, onBack = {}, onUnblocked = {}) }
            compose.onNodeWithText("차단 해제").performClick()
            shot("blocked-users-dialog")
            compose.onNodeWithText("취소").performClick()
            compose.onNodeWithText("차단을 해제하시겠습니까?").assertDoesNotExist()
        } finally {
            TokenManager.clear()
        }
    }
}
