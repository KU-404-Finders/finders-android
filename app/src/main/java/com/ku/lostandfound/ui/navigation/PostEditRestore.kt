package com.ku.lostandfound.ui.navigation

import android.os.Bundle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.network.RefreshSessionResult
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager

internal data class PostEditRestore(val owner: String, val type: PostType, val id: Long) {
    val route: String get() = Route.PostEdit.create(id, type)

    fun toBundle() = Bundle().apply {
        putString("owner", owner)
        putString(Route.PostEdit.ARG_POST_TYPE, type.name)
        putLong(Route.PostEdit.ARG_POST_ID, id)
    }

    suspend fun authenticate(
        refresh: suspend () -> RefreshSessionResult = { RetrofitClient.refreshSession() },
    ): RefreshSessionResult? {
        if (!belongsToCurrentSession()) return null
        val result = if (TokenManager.isAccessTokenExpired()) refresh() else RefreshSessionResult.Success
        return if (result == RefreshSessionResult.Success && !belongsToCurrentSession()) null else result
    }

    fun belongsToCurrentSession(): Boolean = owner == TokenManager.reminderSession()

    fun navigate(controller: NavHostController) {
        TokenManager.withReminderSession(owner) {
            controller.navigate(route) { launchSingleTop = true }
            controller.currentBackStackEntry?.savedStateHandle?.set(OWNER_KEY, owner)
        }
    }

    companion object {
        private const val OWNER_KEY = "editSessionOwner"
        fun fromBundle(bundle: Bundle?): PostEditRestore? {
            val owner = bundle?.getString("owner")?.takeIf(String::isNotBlank) ?: return null
            val (type, id) = Route.PostEdit.parse(
                bundle.getString(Route.PostEdit.ARG_POST_TYPE),
                bundle.getLong(Route.PostEdit.ARG_POST_ID),
            ) ?: return null
            return PostEditRestore(owner, type, id)
        }

        fun fromEntry(entry: NavBackStackEntry?): PostEditRestore? {
            if (entry?.destination?.route != Route.PostEdit.route) return null
            val owner = entry.savedStateHandle.get<String>(OWNER_KEY) ?: return null
            val (type, id) = entry.editTarget() ?: return null
            return PostEditRestore(owner, type, id)
        }
    }
}
