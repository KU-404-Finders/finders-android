package com.ku.lostandfound.ui.navigation

import androidx.navigation.NavHostController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.UUID

// A generation is unique only within this process. Never trust a saved generation alone.
private val tabStateProcessId = UUID.randomUUID().toString()

internal fun mainTabStateOwner(sessionGeneration: Long, processId: String = tabStateProcessId): String =
    "$processId:$sessionGeneration"

@Composable
internal fun rememberMainTabScrollState(accountKey: String): LazyListState {
    val saver = remember(accountKey) {
        Saver<LazyListState, Any>(
            save = { arrayListOf(accountKey, it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
            restore = { saved ->
                val fields = saved as? List<*>
                val index = fields?.getOrNull(1) as? Int
                val offset = fields?.getOrNull(2) as? Int
                if (fields?.getOrNull(0) == accountKey && index != null && index >= 0 && offset != null && offset >= 0) {
                    LazyListState(index, offset)
                } else null
            },
        )
    }
    return rememberSaveable(accountKey, saver = saver) { LazyListState() }
}

@Composable
internal fun rememberMainTabCategoryFilter(accountKey: String): MutableState<String> {
    val saver = remember(accountKey) {
        Saver<MutableState<String>, Any>(
            save = { arrayListOf(accountKey, it.value) },
            restore = { saved ->
                val fields = saved as? List<*>
                val category = fields?.getOrNull(1) as? String
                if (fields?.getOrNull(0) == accountKey && category != null) mutableStateOf(category) else null
            },
        )
    }
    return rememberSaveable(accountKey, saver = saver) { mutableStateOf("ALL") }
}

internal fun NavHostController.navigateToMainTab(route: String) {
    if (currentBackStackEntry?.destination?.route == route) return
    navigate(route) {
        launchSingleTop = true
        // This app uses one shared board stack. Saving the popped stack under
        // Found and restoring it here can reopen Lost when Found is selected.
        popUpTo(Route.Found.route)
    }
}
