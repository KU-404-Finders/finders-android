package com.ku.lostandfound.reminder

import com.ku.lostandfound.data.CampusBuilding
import kotlin.math.cos
import kotlin.math.hypot

internal const val MAX_REMINDER_BUILDINGS = 20
internal const val MAX_REMINDER_ITEMS = 12
internal const val MAX_REMINDER_NAME = 12
internal const val MAX_STAY_MS = 24 * 60 * 60 * 1000L
internal val reminderDwellChoices = listOf(5, 10, 15, 30, 60)

internal enum class ReminderCondition(val label: String) {
    ALWAYS("항상 받기"), RAIN("비 올 때 받기"), NEVER("받지 않기"),
}

internal enum class EntryWeather { UNKNOWN, RAIN, DRY }

internal data class ReminderItem(val name: String, val condition: ReminderCondition)

internal data class LeaveReminderSettings(
    val enabled: Boolean = false,
    val buildingIds: Set<Int> = emptySet(),
    val dwellMinutes: Int = 15,
    val items: List<ReminderItem> = listOf(ReminderItem("우산", ReminderCondition.RAIN)),
) {
    val needsWeather: Boolean get() = items.any { it.condition == ReminderCondition.RAIN }

    fun acceptedName(raw: String): String? {
        val trimmed = raw.trim()
        val count = trimmed.codePointCount(0, trimmed.length)
        return trimmed.takeIf { count in 1..MAX_REMINDER_NAME && items.size < MAX_REMINDER_ITEMS &&
            items.none { item -> item.name == trimmed } }
    }
}

internal data class ReminderRegion(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Double,
) {
    val requestId: String get() = "$PREFIX$id"

    companion object {
        private const val PREFIX = "findersLeaveReminderBuilding"
        private val outdoorIds = setOf(33, 34, 36)

        fun buildingId(requestId: String): Int? =
            requestId.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toIntOrNull()

        fun from(building: CampusBuilding): ReminderRegion? {
            val id = building.id.toIntOrNull()?.takeUnless { it in outdoorIds } ?: return null
            val points = building.rings.flatten()
            if (points.isEmpty()) return null
            val lat = (points.minOf { it.latitude } + points.maxOf { it.latitude }) / 2
            val lon = (points.minOf { it.longitude } + points.maxOf { it.longitude }) / 2
            val metersPerLat = 111_320.0
            val metersPerLon = metersPerLat * cos(Math.toRadians(lat))
            val reach = points.maxOf {
                hypot((it.latitude - lat) * metersPerLat, (it.longitude - lon) * metersPerLon)
            }
            return ReminderRegion(id, building.name, lat, lon, (reach + 40).coerceIn(50.0, 250.0))
        }
    }
}

internal data class ReminderMessage(
    val body: String,
    val subtitle: String?,
) {
    val title: String get() = "소지품 챙기셨나요?"

    companion object {
        fun make(building: String?, items: List<ReminderItem>, weather: EntryWeather): ReminderMessage {
            val names = items.filter {
                it.condition == ReminderCondition.ALWAYS ||
                    (it.condition == ReminderCondition.RAIN && weather == EntryWeather.RAIN)
            }.joinToString(", ") { it.name }
            val place = building?.let { "${it}에서 나오셨어요. " }.orEmpty()
            val question = if (names.isEmpty()) "두고 온 물건이 없는지 확인해 보세요." else "$names 챙기셨나요?"
            return ReminderMessage(place + question, "들어올 때 비가 왔어요".takeIf { weather == EntryWeather.RAIN })
        }
    }
}

internal data class ReminderVisit(
    val id: String,
    val enteredAt: Long,
    val weather: EntryWeather = EntryWeather.UNKNOWN,
)

internal data class ReminderPresence(val lastEventAt: Long, val visit: ReminderVisit? = null) {
    fun enter(at: Long, visitId: () -> String): ReminderPresence =
        if (at <= lastEventAt) this else ReminderPresence(at, visit ?: ReminderVisit(visitId(), at))

    fun exit(at: Long, dwellMinutes: Int): Pair<ReminderPresence, ReminderVisit?> {
        if (at <= lastEventAt) return this to null
        val eligible = visit?.takeIf { at - it.enteredAt in dwellMinutes * 60_000L until MAX_STAY_MS }
        return ReminderPresence(at) to eligible
    }

    fun withWeather(visitId: String, weather: EntryWeather): ReminderPresence =
        if (visit?.id != visitId) this else copy(visit = visit.copy(weather = weather))
}
