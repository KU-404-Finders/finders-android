package com.ku.lostandfound.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ku.lostandfound.network.TokenManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fails when state granted or saved by another test (LeaveReminderPermissionTest and
 * ChatPushAccountSwitchTest grant POST_NOTIFICATIONS) leaks into later tests. Passing after those
 * tests in the same run shows the orchestrator's clearPackageData isolates them.
 */
@RunWith(AndroidJUnit4::class)
class TestIsolationProbeTest {
    @Test fun startsWithoutPermissionsOrSessionsFromEarlierTests() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (Build.VERSION.SDK_INT >= 33) {
            assertEquals(
                "POST_NOTIFICATIONS leaked from an earlier test",
                PackageManager.PERMISSION_DENIED,
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS),
            )
        }
        TokenManager.init(context)
        assertFalse("A fixture session leaked from an earlier test", TokenManager.isLoggedIn())
    }
}
