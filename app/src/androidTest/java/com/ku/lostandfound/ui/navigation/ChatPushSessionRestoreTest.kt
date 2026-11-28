package com.ku.lostandfound.ui.navigation

import android.content.Context
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.notification.publishRemotePush
import java.util.Base64
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatPushSessionRestoreTest {
    @Test fun restoresRecipientAcrossInstrumentationProcesses() {
        val phase = InstrumentationRegistry.getArguments().getString("pushRestorePhase")
        assumeTrue(phase == "seed" || phase == "restore")
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val fixture = context.getSharedPreferences("chat_push_restore_fixture", Context.MODE_PRIVATE)
        TokenManager.init(context)

        if (phase == "seed") {
            TokenManager.clear()
            val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"user_id":42}""".toByteArray())
            assertTrue(TokenManager.saveNewSessionTokens(
                TokenManager.sessionGeneration(), "header.$payload.signature", "fixture-refresh",
            ))
            val owner = checkNotNull(TokenManager.reminderSession())
            // Wait for the pending auth preference writes before the runner exits.
            assertTrue(context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).edit().commit())
            assertTrue(fixture.edit().putString("owner", owner).putInt("pid", Process.myPid()).commit())
            return
        }

        try {
            val seedPid = fixture.getInt("pid", 0)
            assertTrue(seedPid > 0)
            assertNotEquals(seedPid, Process.myPid())
            val owner = checkNotNull(fixture.getString("owner", null))
            assertEquals(owner, TokenManager.reminderSession())
            val payload = mapOf("type" to "CHAT", "roomId" to "7", "recipientUserId" to "42")
            var published = 0
            publishRemotePush(owner, payload + ("recipientUserId" to "43")) { _, _, _ ->
                fail("Wrong recipient in restored process")
            }
            publishRemotePush(owner, payload) { _, _, destination ->
                assertEquals(owner, destination?.owner)
                assertEquals(42L, destination?.recipientUserId)
                published++
            }
            assertEquals(1, published)
        } finally {
            TokenManager.clear()
            fixture.edit().clear().commit()
        }
    }
}
