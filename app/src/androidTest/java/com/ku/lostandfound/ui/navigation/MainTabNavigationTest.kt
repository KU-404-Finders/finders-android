package com.ku.lostandfound.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the app's navigation helper without login, network requests, or creating posts. */
@RunWith(AndroidJUnit4::class)
class MainTabNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController
    private var account by mutableStateOf("a")

    @Before
    fun setUp() {
        composeRule.setContent {
            navController = rememberNavController()
            val states = mapOf(
                Route.Found.route to rememberMainTabScrollState(account),
                Route.Lost.route to rememberMainTabScrollState(account),
                Route.ChatList.route to rememberMainTabScrollState(account),
                Route.Profile.route to rememberMainTabScrollState(account),
                Route.PostWrite.route to rememberMainTabScrollState(account),
            )
            val scope = rememberCoroutineScope()
            NavHost(navController = navController, startDestination = Route.Found.route) {
                listOf(Route.Found, Route.Lost, Route.ChatList, Route.Profile, Route.PostWrite).forEach { route ->
                    composable(route.route) {
                        val state = states.getValue(route.route)
                        androidx.compose.foundation.layout.Column {
                            Text(route.route)
                            TextButton(onClick = { scope.launch { state.scrollToItem(10) } }) {
                                Text("counter:${state.firstVisibleItemIndex}")
                            }
                            LazyColumn(state = state) { items((0..80).toList()) { Text("item $it") } }
                        }
                    }
                }
            }
        }
        assertDestination(Route.Found.route)
    }

    @Test
    fun switchingFromLostToFoundDoesNotRestoreLostBoard() {
        repeat(3) {
            goToMainTab(Route.Lost.route)
            assertPreviousDestination(Route.Found.route)

            goToMainTab(Route.Found.route)
            assertPreviousDestination(null)
        }
    }

    @Test
    fun completingLostPostFromFoundAllowsReturningToFound() {
        openWriteScreen()
        // The successful LOST submission invokes this same helper in MainNavGraph.
        goToMainTab(Route.Lost.route)
        assertPreviousDestination(Route.Found.route)

        goToMainTab(Route.Found.route)
        assertPreviousDestination(null)
    }

    @Test
    fun completingLostPostFromLostRemovesWriterAndDuplicateLostBoard() {
        goToMainTab(Route.Lost.route)
        openWriteScreen()
        goToMainTab(Route.Lost.route)

        assertPreviousDestination(Route.Found.route)
        composeRule.runOnIdle { assertTrue(navController.popBackStack()) }
        assertDestination(Route.Found.route)
        assertPreviousDestination(null)
    }

    @Test
    fun completingFoundPostFromLostAllowsSwitchingBackWithoutDuplicateFoundBoard() {
        goToMainTab(Route.Lost.route)
        openWriteScreen()
        // The successful FOUND submission invokes this same helper in MainNavGraph.
        goToMainTab(Route.Found.route)
        assertPreviousDestination(null)

        goToMainTab(Route.Lost.route)
        composeRule.runOnIdle { assertTrue(navController.popBackStack()) }
        assertDestination(Route.Found.route)
        assertPreviousDestination(null)
    }

    @Test
    fun profileCanReturnToEitherBoardWithoutRestoringAnotherTab() {
        goToMainTab(Route.Lost.route)
        goToMainTab(Route.Profile.route)
        assertPreviousDestination(Route.Found.route)
        goToMainTab(Route.Found.route)
        assertPreviousDestination(null)

        goToMainTab(Route.Profile.route)
        goToMainTab(Route.Lost.route)
        assertPreviousDestination(Route.Found.route)
        // Re-selecting the current tab must not add a second copy.
        goToMainTab(Route.Lost.route)
        composeRule.runOnIdle { assertTrue(navController.popBackStack()) }
        assertDestination(Route.Found.route)
        assertPreviousDestination(null)
    }

    @Test
    fun chatCanSwitchBetweenBoardsAndProfileWithoutRestoringAnotherTab() {
        repeat(3) {
            goToMainTab(Route.Lost.route)
            goToMainTab(Route.ChatList.route)
            assertPreviousDestination(Route.Found.route)

            goToMainTab(Route.Profile.route)
            goToMainTab(Route.ChatList.route)
            goToMainTab(Route.ChatList.route)
            assertPreviousDestination(Route.Found.route)

            goToMainTab(Route.Found.route)
            assertPreviousDestination(null)
        }
    }

    @Test fun tabUiStateRestoresIndependentlyAndResetsForAnotherAccount() {
        goToMainTab(Route.Lost.route)
        composeRule.onNodeWithText("counter:0").performClick()
        composeRule.onNodeWithText("counter:10").assertIsDisplayed()
        goToMainTab(Route.Profile.route)
        composeRule.onNodeWithText("counter:0").assertIsDisplayed()
        goToMainTab(Route.Lost.route)
        composeRule.onNodeWithText("counter:10").assertIsDisplayed()
        goToMainTab(Route.Profile.route)
        composeRule.runOnIdle { account = "b" }
        goToMainTab(Route.Lost.route)
        composeRule.onNodeWithText("counter:0").assertIsDisplayed()
    }

    private fun openWriteScreen() {
        composeRule.runOnIdle { navController.navigate(Route.PostWrite.route) }
        assertDestination(Route.PostWrite.route)
    }

    private fun goToMainTab(route: String) {
        composeRule.runOnIdle { navController.navigateToMainTab(route) }
        assertDestination(route)
    }

    private fun assertDestination(route: String) {
        composeRule.onNodeWithText(route).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(route, navController.currentDestination?.route)
        }
    }

    private fun assertPreviousDestination(route: String?) {
        composeRule.runOnIdle {
            val previousRoute = navController.previousBackStackEntry?.destination?.route
            if (route == null) {
                assertNull(previousRoute)
            } else {
                assertEquals(route, previousRoute)
            }
        }
    }
}
