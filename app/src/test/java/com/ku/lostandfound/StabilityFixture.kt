package com.ku.lostandfound

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import com.ku.lostandfound.network.ApiResponse
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.notification.NotificationInboxRefreshBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.mockito.Mockito.*
import org.mockito.MockedStatic
import retrofit2.Response

internal fun <T> ok(value: T) = Response.success(ApiResponse(true, "", value))

@OptIn(ExperimentalCoroutinesApi::class)
open class StabilityFixture {
    protected val main = StandardTestDispatcher()
    protected val application = mock(Application::class.java)
    private lateinit var log: MockedStatic<Log>

    @Before fun setupSession() {
        Dispatchers.setMain(main)
        log = mockStatic(Log::class.java)
        val values = mutableMapOf<String, String?>()
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(application.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.clear()).thenAnswer { values.clear(); editor }
        `when`(editor.putString(anyString(), anyString())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument(1)
            editor
        }
        `when`(prefs.getString(anyString(), isNull())).thenAnswer { values[it.getArgument<String>(0)] }
        TokenManager.init(application)
        TokenManager.clear()
        login("a")
    }

    protected fun login(name: String) {
        check(TokenManager.saveNewSessionTokens(TokenManager.sessionGeneration(), name, "refresh-$name"))
    }

    protected fun dispatch() = main.scheduler.runCurrent()

    @After fun teardownSession() {
        try {
            // Subclass @After methods cancel their ViewModels. Run cancellation finally
            // blocks on this test's scheduler before replacing Dispatchers.Main.
            main.scheduler.runCurrent()
            assertEquals("Event subscribers leaked past test teardown", 0,
                NotificationInboxRefreshBus.events.subscriptionCount.value)
        }
        finally {
            try { TokenManager.clear() }
            finally {
                try { if (::log.isInitialized) log.close() }
                finally { Dispatchers.resetMain() }
            }
        }
    }
}
