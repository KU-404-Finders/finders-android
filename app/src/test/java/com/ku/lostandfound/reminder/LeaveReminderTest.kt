package com.ku.lostandfound.reminder

import com.google.gson.JsonParser
import com.ku.lostandfound.data.CampusBuilding
import com.ku.lostandfound.data.GeoPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class LeaveReminderTest {
    @Test fun regionsMatchIosReferenceForAll33Buildings() {
        val campus = javaClass.getResource("/konkuk_buildings_full_coordinates.json")!!.readText()
        val buildings = JsonParser.parseString(campus).asJsonObject.getAsJsonArray("buildings").map { item ->
            val building = item.asJsonObject
            CampusBuilding(
                id = building.get("id").asString,
                name = building.get("name").asString,
                levels = 1,
                undergroundLevels = 0,
                rings = building.getAsJsonArray("coordinate").map { ring ->
                    ring.asJsonArray.map { point -> GeoPoint(point.asJsonArray[0].asDouble, point.asJsonArray[1].asDouble) }
                },
            )
        }
        val regions = buildings.mapNotNull(ReminderRegion::from).associateBy { it.id }
        val reference = javaClass.getResource("/ios-leave-reminder-regions.csv")!!.readText().lineSequence()
            .drop(1).filter { it.isNotBlank() }.map { it.split(',') }.toList()
        assertEquals(33, regions.size)
        assertEquals(reference.map { it[0].toInt() }.toSet(), regions.keys)
        for ((id, latitude, longitude, radius) in reference) {
            val region = regions.getValue(id.toInt())
            assertEquals(latitude.toDouble(), region.latitude, 1e-10)
            assertEquals(longitude.toDouble(), region.longitude, 1e-10)
            assertEquals(radius.toDouble(), region.radius, 1e-6)
            assertEquals(region.id, ReminderRegion.buildingId(region.requestId))
        }
    }

    @Test fun repeatedEntryKeepsOriginalVisitAndStaleExitIsIgnored() {
        val entered = ReminderPresence(0).enter(1_000) { "entry" }
        val repeated = entered.enter(4_000) { error("duplicate") }
        assertEquals(entered.visit, repeated.visit)
        assertEquals(repeated to null, repeated.exit(3_000, 5))
        val (left, stay) = repeated.exit(301_000, 5)
        assertEquals("entry", stay?.id)
        assertEquals(left to null, left.exit(301_000, 5))
        assertEquals(ReminderPresence(302_000) to null, left.exit(302_000, 5))
        assertEquals(left, left.enter(4_000) { error("stale") })
    }

    @Test fun dwellIncludesMinimumAndExcludes24Hours() {
        for (dwell in reminderDwellChoices) {
            val start = 1_000L
            val inside = ReminderPresence(0).enter(start) { "visit" }
            val boundary = start + dwell * 60_000L
            assertNull(inside.exit(boundary - 1, dwell).second)
            assertNotNull(inside.exit(boundary, dwell).second)
            assertNotNull(inside.exit(start + MAX_STAY_MS - 1, dwell).second)
            assertNull(inside.exit(start + MAX_STAY_MS, dwell).second)
            assertNull(inside.exit(start - 1, dwell).second)
        }
    }

    @Test fun lateWeatherCannotOverwriteNextVisitOrReviveAnExitedVisit() {
        val first = ReminderPresence(0).enter(1000) { "first" }
        val left = first.exit(2000, 5).first
        assertEquals(left, left.withWeather("first", EntryWeather.RAIN))
        val second = left.enter(3000) { "second" }
        assertEquals(second, second.withWeather("first", EntryWeather.RAIN))
        assertEquals(EntryWeather.DRY, second.withWeather("second", EntryWeather.DRY).visit?.weather)
    }

    @Test fun itemValidationAndMessageFollowTheArrivalWeather() {
        val items = LeaveReminderSettings().items + ReminderItem("지갑", ReminderCondition.ALWAYS) +
            ReminderItem("학생증", ReminderCondition.NEVER)
        val preferences = LeaveReminderSettings(items = items)
        assertNull(preferences.acceptedName(" 우산 "))
        assertNull(preferences.acceptedName(" "))
        assertNull(preferences.acceptedName("a".repeat(13)))
        assertEquals("키", preferences.acceptedName(" 키 "))
        assertNull(preferences.copy(items = List(12) { ReminderItem("$it", ReminderCondition.NEVER) }).acceptedName("키"))
        val rain = ReminderMessage.make("공학관", items, EntryWeather.RAIN)
        assertEquals("들어올 때 비가 왔어요", rain.subtitle)
        assertEquals("공학관에서 나오셨어요. 우산, 지갑 챙기셨나요?", rain.body)
        assertEquals("공학관에서 나오셨어요. 지갑 챙기셨나요?", ReminderMessage.make("공학관", items, EntryWeather.UNKNOWN).body)
        assertEquals("공학관에서 나오셨어요. 두고 온 물건이 없는지 확인해 보세요.",
            ReminderMessage.make("공학관", emptyList(), EntryWeather.DRY).body)
    }

    @Test fun weatherUsesEndOfTheCurrentHourIncludingMidnight() {
        val now = LocalDateTime.parse("2026-11-26T23:53")
        assertEquals(EntryWeather.RAIN, CampusWeather.classify(forecast(0.2, 50), now))
        assertEquals(EntryWeather.DRY, CampusWeather.classify(forecast(0.2, 49), now))
        assertEquals(EntryWeather.DRY, CampusWeather.classify(forecast(0.0, 80), now))
        assertEquals(EntryWeather.RAIN, CampusWeather.classify(forecast(0.0, 0, current = 0.1), now))
        assertEquals(EntryWeather.UNKNOWN, CampusWeather.classify(forecast(0.2, 50), now.plusMinutes(7)))
        assertEquals(EntryWeather.UNKNOWN, CampusWeather.classify("{}", now))
        assertEquals(EntryWeather.UNKNOWN, CampusWeather.classify(forecast(0.2, 101), now))
    }

    @Test fun timeoutAndHttpFailureAreUnknownAndRequestsUseFixedCoordinatesWithoutAuth() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val localhost = OkHttpClient.Builder().callTimeout(120, TimeUnit.MILLISECONDS).build()
            val calls = Call.Factory { request: Request ->
                assertEquals("api.open-meteo.com", request.url.host)
                assertEquals("37.5418", request.url.queryParameter("latitude"))
                assertEquals("127.0760", request.url.queryParameter("longitude"))
                assertEquals("Asia/Seoul", request.url.queryParameter("timezone"))
                assertNull(request.header("Authorization"))
                localhost.newCall(request.newBuilder().url(server.url("/")).build())
            }
            val weather = CampusWeather(calls, Clock.fixed(Instant.parse("2026-11-26T14:53:00Z"), ZoneOffset.UTC))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertEquals(EntryWeather.UNKNOWN, weather.lookup())
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(EntryWeather.UNKNOWN, weather.lookup())
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun delayedEnterUsesItsOwnKoreanHourAndRejectsMissingHours() = runBlocking {
        MockWebServer().use { server ->
            val client = OkHttpClient()
            val weather = CampusWeather(Call.Factory { request ->
                assertEquals("1", request.url.queryParameter("past_days"))
                client.newCall(request.newBuilder().url(server.url("/")).build())
            }, Clock.fixed(Instant.parse("2026-11-26T15:53:00Z"), ZoneOffset.UTC))
            server.enqueue(MockResponse().setBody(forecast(0.3, 70)))
            val entered = Instant.parse("2026-11-26T14:50:00Z").toEpochMilli()
            assertEquals(EntryWeather.RAIN, weather.lookup(entered))
            server.enqueue(MockResponse().setBody(forecast(0.3, 70)))
            assertEquals(EntryWeather.UNKNOWN, weather.lookup(entered - 7_200_000))
            server.enqueue(MockResponse().setBody(forecast(0.0, 0, current = 1.0)))
            assertEquals(EntryWeather.DRY, weather.lookup(entered))
            server.enqueue(MockResponse().setBody(forecast(0.0, 0, current = 1.0)))
            assertEquals(EntryWeather.RAIN, weather.lookup(entered - 600_000))
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun registrationWaitDistinguishesDelayedCompletionActualFailureAndTimeout() =
        kotlinx.coroutines.test.runTest {
            val success = RegistrationAttempt { error("Current registration must not be cleaned up") }
            val delayed = async { success.await { /* first deadline */ } }
            testScheduler.advanceTimeBy(5_000)
            success.complete(0)
            assertEquals(0, delayed.await())

            val failed = RegistrationAttempt {}.also { it.complete(1000) }
            assertEquals(1000, failed.await { error("Already failed") })
            var cleanups = 0
            val pending = RegistrationAttempt { cleanups++ }
            var timeouts = 0
            assertNull(pending.await { timeouts++ })
            assertEquals(1, timeouts)
            pending.abandon()
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use { pending.complete(0) }
            assertEquals(2, cleanups)
            assertEquals(listOf(0L, 10_000L, 30_000L), reminderRestoreDelays)
        }

    @Test fun monitoringErrorWinsOverTaskSuccessBeforeConfirmationInBothOrders() =
        kotlinx.coroutines.test.runTest {
            val unavailable = com.google.android.gms.location.GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE
            val errorFirst = RegistrationAttempt {}
            errorFirst.monitoringUnavailable()
            errorFirst.complete(0)
            assertEquals(unavailable, errorFirst.await { fail("Registration already lost") })
            val taskFirst = RegistrationAttempt {}
            taskFirst.complete(0)
            taskFirst.monitoringUnavailable()
            assertEquals(unavailable, taskFirst.await { fail("Registration already lost") })
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun removalWaitDoesNotConfirmLateOrFailedAttempts() = kotlinx.coroutines.test.runTest {
        val requested = mutableListOf<List<String>>()
        val attempts = mutableListOf<kotlinx.coroutines.CompletableDeferred<Boolean>>()
        var elapsed = 0L
        val removals = RemovalRequests({ elapsed }) { ids ->
            requested += ids
            kotlinx.coroutines.CompletableDeferred<Boolean>().also(attempts::add)
        }
        assertNull(removals.await(listOf("old")))
        assertNull(removals.await(listOf("old", "other")))
        assertEquals(listOf(listOf("old"), listOf("other")), requested)
        attempts[0].complete(true)
        attempts[1].complete(false)
        assertFalse(removals.await(listOf("old", "other"))!!)
        assertNull(removals.await(listOf("old", "other")))
        assertEquals(listOf("other"), requested.last())
        val waiting = async { removals.await(listOf("old", "other")) }
        testScheduler.runCurrent()
        waiting.cancel()
        assertTrue(waiting.isCancelled)
        elapsed = 12_000
        assertNull(removals.await(listOf("old", "other")))
        attempts[2].complete(true)
        assertNull(removals.await(listOf("old", "other")))
        attempts[3].complete(true)
        assertTrue(removals.await(listOf("old", "other"))!!)
    }

    @Test fun selectionChangesOnlyRecognizeRetainedAndCurrentRequestIds() {
        val first = ReminderRegistration("session", "callback", setOf(3, 4), mapOf(3 to "3:a", 4 to "4:a"))
        val second = first.copy(buildings = setOf(3, 5), requestIds = mapOf(3 to "3:a", 5 to "5:b"))
        val third = second.copy(buildings = setOf(3, 4, 5), requestIds = second.requestIds + (4 to "4:c"))
        assertEquals(3, second.buildingFor("3:a"))
        assertNull(second.buildingFor("4:a"))
        assertNull(third.buildingFor("4:a"))
        assertEquals(4, third.buildingFor("4:c"))
    }

    private fun forecast(amount: Double, probability: Int, current: Double = 0.0): String = """
        {"current":{"time":"2026-11-26T23:45","interval":900,"precipitation":$current},
         "hourly":{"time":["2026-11-26T23:00","2026-11-27T00:00"],
         "precipitation":[1,$amount],"precipitation_probability":[100,$probability]}}
    """.trimIndent()

    @Test fun recoveryRetriesOnlyTransientStatesForTheSameSignedInEnabledOwner() {
        for (status in ReminderStatus.entries) {
            val expected = status in setOf(ReminderStatus.LOCATION_DISABLED, ReminderStatus.PLAY_SERVICES,
                ReminderStatus.STARTING, ReminderStatus.ERROR)
            assertEquals(status.name, expected, reminderRecoveryNeeded("user:1", "user:1", true, status))
            assertFalse("disabled ${status.name}", reminderRecoveryNeeded("user:1", "user:1", false, status))
            assertFalse("account switch ${status.name}", reminderRecoveryNeeded("user:1", "user:2", true, status))
            assertFalse("signed out ${status.name}", reminderRecoveryNeeded("user:1", null, true, status))
            assertFalse("no owner ${status.name}", reminderRecoveryNeeded(null, null, true, status))
        }
    }

    private class FakeClock(var now: Long)
    private class Stored(var since: Long = 0L)
    private fun window(stored: Stored, clock: FakeClock) =
        RecoveryWindow({ stored.since }, { stored.since = it ?: 0L }, { clock.now })
    private val hour = 60L * 60 * 1000

    @Test fun bootRecoveryWithoutARecordedOutageStillStopsAfterADay() {
        val stored = Stored()
        val clock = FakeClock(1_000)
        // Boot: nothing recorded yet, Play services keeps failing.
        val needed = reminderRecoveryNeeded("user:1", "user:1", true, ReminderStatus.PLAY_SERVICES)
        assertTrue(window(stored, clock).allows(needed))
        assertEquals("first outage time recorded", 1_000, stored.since)
        clock.now += 23 * hour
        assertTrue(window(stored, clock).allows(needed))
        assertEquals("kept across retries and new processes", 1_000, stored.since)
        clock.now += hour
        assertFalse("no background retries after 24h", window(stored, clock).allows(needed))
        assertEquals(24L * hour, RECOVERY_WINDOW_MS)
    }

    @Test fun everyRecoverableStatusSharesTheWindowAndOthersNeverRecordIt() {
        for (status in ReminderStatus.entries) {
            val stored = Stored()
            val recoverable = reminderRecoveryNeeded("user:1", "user:1", true, status)
            assertEquals(status.name, recoverable, window(stored, FakeClock(5)).allows(recoverable))
            assertEquals(status.name, if (recoverable) 5L else 0L, stored.since)
        }
    }

    @Test fun recoveredDisabledOrSwitchedAccountsEndTheOutage() {
        val stored = Stored(since = 1_000)
        val clock = FakeClock(2_000)
        // Disabled, signed out or another account: not recoverable, so the record is cleared.
        for (needed in listOf(
            reminderRecoveryNeeded("user:1", "user:1", false, ReminderStatus.PLAY_SERVICES),
            reminderRecoveryNeeded("user:1", "user:2", true, ReminderStatus.PLAY_SERVICES),
            reminderRecoveryNeeded("user:1", "user:1", true, ReminderStatus.MONITORING),
        )) {
            stored.since = 1_000
            assertFalse(window(stored, clock).allows(needed))
            assertEquals(0L, stored.since)
        }
        // A later outage starts a fresh window.
        clock.now = 30 * hour
        assertTrue(window(stored, clock).allows(true))
        assertEquals(30 * hour, stored.since)
    }
}
