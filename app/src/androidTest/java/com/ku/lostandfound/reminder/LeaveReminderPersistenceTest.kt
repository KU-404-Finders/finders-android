package com.ku.lostandfound.reminder

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ku.lostandfound.data.CampusJsonRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LeaveReminderPersistenceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "reminder-test-" + UUID.randomUUID()
    private val context = object : ContextWrapper(app) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            app.getSharedPreferences(prefix, mode)
    }

    @Test fun persistedVisitCanResumeWithSameSessionAndIsInvisibleToAnotherSession() {
        try {
            val first = LeaveReminderStore(context)
            val settings = LeaveReminderSettings(enabled = true, buildingIds = setOf(13), dwellMinutes = 5)
            val visit = ReminderPresence(1_000, ReminderVisit("visit-id", 1_000, EntryWeather.RAIN))
            first.save(settings)
            first.savePresence("old-session", mapOf(13 to visit))
            first.setRegistration(ReminderRegistration("old-session", "registration-id", setOf(13)))

            val reloaded = LeaveReminderStore(context)
            assertEquals(settings, reloaded.settings())
            assertEquals("registration-id", reloaded.registration()?.id)
            assertEquals(EntryWeather.RAIN, reloaded.presence("old-session")[13]?.exit(301_000, 5)?.second?.weather)
            assertTrue(reloaded.presence("new-session").isEmpty())
            reloaded.savePresence(null, emptyMap())
            assertTrue(reloaded.presence("old-session").isEmpty())
        } finally {
            app.deleteSharedPreferences(prefix)
        }
    }

    @Test fun pendingRemovalKeepsPlatformIdsAcrossReopenUntilDurablyConfirmed() {
        try {
            val first = LeaveReminderStore(context)
            val original = ReminderRegistration("owner", "callback", setOf(13, 14), mapOf(13 to "keep", 14 to "erase"))
            val visit = ReminderPresence(1_000, ReminderVisit("visit", 1_000, EntryWeather.RAIN))
            first.setRegistration(original)
            first.savePresence("owner", mapOf(13 to visit))
            assertTrue(first.beginRemoval(setOf(14)))
            val reopened = LeaveReminderStore(context)
            assertEquals(setOf(14), reopened.pendingRemoval())
            assertEquals("erase", reopened.registration()?.requestIds?.get(14))
            assertTrue(reopened.beginRemoval(setOf(14)))
            assertEquals(original, reopened.registration())
            val kept = original.copy(buildings = setOf(13), requestIds = mapOf(13 to "keep"))
            assertTrue(reopened.finishRemoval(kept))
            val committed = LeaveReminderStore(context)
            assertTrue(committed.pendingRemoval().isEmpty())
            assertEquals(kept, committed.registration())
            assertEquals(visit, committed.presence("owner")[13])
        } finally {
            app.deleteSharedPreferences(prefix)
        }
    }

    @Test fun actualAssetParserProvides33SelectableBuildings() {
        val ids = CampusJsonRepository(app).loadBuildings().mapNotNull(ReminderRegion::from).map { it.id }
        assertEquals(33, ids.size)
        assertEquals(33, ids.toSet().size)
        assertTrue(ids.none { it in setOf(33, 34, 36) })
    }
}
