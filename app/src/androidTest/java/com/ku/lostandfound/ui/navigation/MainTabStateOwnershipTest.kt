package com.ku.lostandfound.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTabStateOwnershipTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // Plain fields allow changing the owner strictly between save and restore, without
    // recomposing (and resetting) the live state before it is saved.
    private var generation = 7L
    private var processId = "test-process-a"
    private var userId by mutableStateOf<Long?>(null)
    private var tick by mutableIntStateOf(0)
    private lateinit var lists: List<LazyListState>
    private lateinit var filter: MutableState<String>
    // Reuse the exact content call site, just as Activity.onCreate does in production.
    private val content: @Composable () -> Unit = { Tabs() }

    @Composable
    private fun Tabs() {
        val owner = mainTabStateOwner(generation, processId)
        lists = listOf(rememberMainTabScrollState(owner), rememberMainTabScrollState(owner),
            rememberMainTabScrollState(owner), rememberMainTabScrollState(owner))
        filter = rememberMainTabCategoryFilter(owner)
        Column {
            Text("user=$userId tick=$tick filter=${filter.value}")
            lists.forEachIndexed { tab, state ->
                LazyColumn(Modifier.height(100.dp), state = state) {
                    items((0..199).toList()) { Text("tab=$tab post=$it") }
                }
            }
        }
    }

    private fun install() { compose.setContent(content); compose.waitForIdle() }
    private fun installRestorable() = StateRestorationTester(compose).also {
        it.setContent(content); compose.waitForIdle()
    }
    private fun populate() {
        compose.runOnIdle {
            lists.forEachIndexed { i, state -> state.requestScrollToItem(10 + i, i + 1) }
            filter.value = "UMBRELLA"
        }
        compose.waitForIdle()
        assertState(saved = true)
    }
    private fun assertState(saved: Boolean) {
        compose.runOnIdle {
            lists.forEachIndexed { i, state ->
                assertEquals("tab $i index", if (saved) 10 + i else 0, state.firstVisibleItemIndex)
                assertEquals("tab $i offset", if (saved) i + 1 else 0, state.firstVisibleItemScrollOffset)
            }
            assertEquals(if (saved) "UMBRELLA" else "ALL", filter.value)
        }
    }
    private fun recreateActivity() {
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { it.setContent(content = content) }
        compose.waitForIdle()
    }

    @Test fun initialUserLookupKeepsAllFourScrollPositionsAndFilter() {
        install(); populate()
        val before = lists.toList()
        compose.runOnIdle { userId = 42L }
        assertState(saved = true)
        compose.runOnIdle { lists.forEachIndexed { i, state -> assertSame(before[i], state) } }
    }
    @Test fun sameOwnerSavedStateRestoresAllFourScrollPositionsAndFilter() {
        val restoration = installRestorable(); populate()
        restoration.emulateSavedInstanceStateRestore()
        assertState(saved = true)
    }
    @Test fun savedStateFromAnotherSessionIsRejectedEvenOnFirstComposition() {
        val restoration = installRestorable(); populate()
        compose.runOnIdle { generation++ }
        restoration.emulateSavedInstanceStateRestore()
        assertState(saved = false)
    }
    @Test fun savedStateFromAnotherProcessIsRejectedWhenGenerationCounterRepeats() {
        val restoration = installRestorable(); populate()
        compose.runOnIdle { processId = "test-process-b" }
        restoration.emulateSavedInstanceStateRestore()
        assertState(saved = false)
    }
    @Test fun accountChangeAndLogoutResetLiveTabState() {
        install(); populate()
        compose.runOnIdle { generation++; tick++ }
        assertState(saved = false)
        populate()
        compose.runOnIdle { generation++; tick++ }
        assertState(saved = false)
    }
    @Test fun activityRecreationRestoresStateForSameProcessAndSession() {
        install(); populate(); recreateActivity(); assertState(saved = true)
        compose.runOnIdle { userId = 42L }
        assertState(saved = true)
    }
    @Test fun activityRecreationRejectsStateAfterAccountChanges() {
        install(); populate()
        compose.runOnIdle { generation++ }
        recreateActivity(); assertState(saved = false)
    }
}
