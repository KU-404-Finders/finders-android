package com.ku.lostandfound.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.network.TokenManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoredEditNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restoreSnapshotKeepsTheSessionAndBothPartsOfThePostKey() {
        val found = PostEditRestore("existing-session", PostType.FOUND, 7)
        val lost = found.copy(type = PostType.LOST)
        assertEquals(found, PostEditRestore.fromBundle(found.toBundle()))
        assertEquals(lost, PostEditRestore.fromBundle(lost.toBundle()))
        assertNull(PostEditRestore.fromBundle(found.toBundle().apply { putLong(Route.PostEdit.ARG_POST_ID, 0) }))
    }

    @Test fun restoresLostAndFoundWithTheSameNumericId() {
        TokenManager.init(InstrumentationRegistry.getInstrumentation().targetContext)
        assumeFalse(TokenManager.isLoggedIn())
        check(TokenManager.saveNewSessionTokens(TokenManager.sessionGeneration(), "fixture-a", "fixture-r"))
        try {
            assertRestores()
        } finally {
            TokenManager.clear()
        }
    }

    private fun assertRestores() {
        val owner = checkNotNull(TokenManager.reminderSession())
        val restoration = StateRestorationTester(compose)
        lateinit var controller: NavHostController
        restoration.setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = Route.Found.route) {
                composable(Route.Found.route) { Text("board") }
                composable(
                    Route.PostEdit.route,
                    arguments = listOf(
                        navArgument(Route.PostEdit.ARG_POST_TYPE) { type = NavType.StringType },
                        navArgument(Route.PostEdit.ARG_POST_ID) { type = NavType.LongType },
                    ),
                ) { entry ->
                    val edit = Route.PostEdit.parse(
                        entry.arguments?.getString(Route.PostEdit.ARG_POST_TYPE),
                        entry.arguments?.getLong(Route.PostEdit.ARG_POST_ID),
                    )
                    Text(edit?.let { "${it.first.name} ${it.second}" } ?: "invalid")
                }
            }
        }
        for (type in listOf(PostType.LOST, PostType.FOUND)) {
            val expected = PostEditRestore(owner, type, 7)
            // The real path MainActivity uses, so a lost owner write would fail here.
            compose.runOnIdle { expected.navigate(controller) }
            compose.onNodeWithText("${type.name} 7").assertIsDisplayed()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("${type.name} 7").assertIsDisplayed()
            compose.runOnIdle { assertEquals(expected, PostEditRestore.fromEntry(controller.currentBackStackEntry)) }
            compose.runOnIdle { controller.popBackStack() }
        }
    }
}
