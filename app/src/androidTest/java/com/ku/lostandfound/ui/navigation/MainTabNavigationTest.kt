package com.ku.lostandfound.ui.navigation

import androidx.compose.material3.Text
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

    @Before
    fun setUp() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Route.Found.route) {
                listOf(Route.Found, Route.Lost, Route.ChatList, Route.Profile, Route.PostWrite).forEach { route ->
                    composable(route.route) { Text(route.route) }
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
