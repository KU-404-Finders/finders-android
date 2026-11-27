package com.ku.lostandfound.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ku.lostandfound.data.CampusJsonRepository
import com.ku.lostandfound.ui.component.CampusMapCanvas
import com.ku.lostandfound.ui.profile.screen.ProfileScreen
import com.ku.lostandfound.ui.signup.screen.LegalLinks
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParityAttributionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun sharedMapShowsAttributionAndOpensOsmCopyright() {
        val repository = CampusJsonRepository(compose.activity)
        val boundary = repository.loadBoundary()
        val buildings = repository.loadBuildings()
        val url = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides record(url)) {
                CampusMapCanvas(
                    boundary = boundary,
                    buildings = buildings,
                    modifier = Modifier.size(330.dp, 250.dp),
                )
            }
        }
        compose.onNodeWithText("© OpenStreetMap").assertIsDisplayed()
        compose.onNodeWithContentDescription("OpenStreetMap 저작권 및 지도 출처").performClick()
        compose.runOnIdle { assertEquals(listOf("https://www.openstreetmap.org/copyright"), url) }
    }

    @Test fun profileUsesTheSignupLinksAndHandlesAnUnavailableBrowser() {
        val urls = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides record(urls, fail = true)) {
                ProfileScreen(
                    userName = "테스트", userEmail = "a@example.invalid", myPosts = emptyList(),
                    onPostClick = {}, onShowAllClick = {}, onLogoutClick = {}, onWithdrawClick = {},
                    onWithdrawCompleteConfirm = {}, onFoundTabClick = {}, onLostTabClick = {}, onAddClick = {},
                )
            }
        }
        for (label in listOf("이용약관", "개인정보 처리방침", "문의하기")) {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsDisplayed()
            if (label != "문의하기") compose.onNodeWithText(label).performClick()
        }
        compose.runOnIdle { assertEquals(listOf(LegalLinks.TERMS, LegalLinks.PRIVACY), urls) }
    }

    private fun record(urls: MutableList<String>, fail: Boolean = false): UriHandler = object : UriHandler {
        override fun openUri(uri: String) {
            urls += uri
            if (fail) throw IllegalArgumentException("No activity")
        }
    }
}
