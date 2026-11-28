package com.ku.lostandfound.reminder

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

internal data class ReminderRegistration(
    val owner: String,
    val id: String,
    val buildings: Set<Int>,
    val requestIds: Map<Int, String> = emptyMap(),
) {
    fun buildingFor(requestId: String): Int? = requestIds.entries.firstOrNull { it.value == requestId }?.key
}

internal class LeaveReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("leave_reminder", Context.MODE_PRIVATE)

    fun settings(): LeaveReminderSettings = runCatching {
        val data = JSONObject(prefs.getString("settings", null) ?: return LeaveReminderSettings())
        val defaults = LeaveReminderSettings()
        LeaveReminderSettings(
            enabled = data.optBoolean("enabled"),
            buildingIds = data.optJSONArray("buildings").ids().take(MAX_REMINDER_BUILDINGS).toSet(),
            dwellMinutes = data.optInt("dwell").takeIf { it in reminderDwellChoices } ?: defaults.dwellMinutes,
            items = data.optJSONArray("items")?.let { items ->
                (0 until items.length()).mapNotNull { index ->
                    runCatching {
                        val item = items.getJSONObject(index)
                        val name = defaults.copy(items = emptyList()).acceptedName(item.getString("name"))
                        name?.let { ReminderItem(it, ReminderCondition.valueOf(item.getString("condition"))) }
                    }.getOrNull()
                }.distinctBy { it.name }.take(MAX_REMINDER_ITEMS)
            } ?: defaults.items,
        )
    }.getOrDefault(LeaveReminderSettings())

    fun save(settings: LeaveReminderSettings) {
        val items = settings.items.map { JSONObject().put("name", it.name).put("condition", it.condition.name) }
        val value = JSONObject()
            .put("enabled", settings.enabled)
            .put("buildings", JSONArray(settings.buildingIds.toList()))
            .put("dwell", settings.dwellMinutes)
            .put("items", JSONArray(items))
        prefs.edit { putString("settings", value.toString()) }
    }

    fun registration(): ReminderRegistration? = runCatching {
        val value = JSONObject(prefs.getString("registration", null) ?: return null)
        val ids = value.getJSONArray("buildings").ids()
        val requests = value.optJSONObject("requests")
        ReminderRegistration(value.getString("owner"), value.getString("id"), ids,
            ids.mapNotNull { id -> requests?.optString(id.toString())?.takeIf(String::isNotBlank)?.let { id to it } }.toMap())
    }.getOrNull()

    private fun android.content.SharedPreferences.Editor.writeRegistration(registration: ReminderRegistration?) {
        if (registration == null) remove("registration")
        else putString("registration", JSONObject()
                .put("owner", registration.owner)
                .put("id", registration.id)
                .put("buildings", JSONArray(registration.buildings.toList()))
                .put("requests", JSONObject(registration.requestIds.mapKeys { it.key.toString() })).toString())
    }

    fun setRegistration(registration: ReminderRegistration?) {
        prefs.edit {
            writeRegistration(registration)
            if (registration == null) remove("pending_removal")
        }
    }

    fun pendingRemoval(): Set<Int> = runCatching {
        JSONArray(prefs.getString("pending_removal", "[]")).ids()
    }.getOrDefault(emptySet())

    fun beginRemoval(ids: Set<Int>): Boolean = prefs.edit()
        .putString("pending_removal", JSONArray(ids.toList()).toString()).commit()

    fun finishRemoval(registration: ReminderRegistration): Boolean = prefs.edit().apply {
        writeRegistration(registration)
        remove("pending_removal")
    }.commit()

    fun orphanFences(): Set<String> = prefs.getStringSet("orphan_fences", null).orEmpty().toSet()

    fun addOrphanFences(ids: Collection<String>): Boolean =
        prefs.edit().putStringSet("orphan_fences", orphanFences() + ids).commit()

    fun removeOrphanFences(ids: Collection<String>): Boolean =
        prefs.edit().putStringSet("orphan_fences", orphanFences() - ids.toSet()).commit()

    fun recoverySince(): Long = prefs.getLong("recovery_since", 0L)

    fun setRecoverySince(at: Long?) = prefs.edit { if (at == null) remove("recovery_since") else putLong("recovery_since", at) }

    fun pendingCallback(): String? = prefs.getString("pending_callback", null)

    fun setPendingCallback(id: String?): Boolean = prefs.edit().putString("pending_callback", id).commit()

    fun presence(owner: String): MutableMap<Int, ReminderPresence> {
        val values = mutableMapOf<Int, ReminderPresence>()
        runCatching {
            val data = JSONObject(prefs.getString("presence", null) ?: return values)
            if (data.getString("owner") != owner) return values
            val buildings = data.getJSONObject("buildings")
            for (key in buildings.keys()) {
                val value = buildings.getJSONObject(key)
                val visit = value.optJSONObject("visit")?.let {
                    ReminderVisit(it.getString("id"), it.getLong("entered"), EntryWeather.valueOf(it.getString("weather")))
                }
                values[key.toInt()] = ReminderPresence(value.getLong("last"), visit)
            }
        }.onFailure { values.clear() }
        return values
    }

    fun savePresence(owner: String?, presence: Map<Int, ReminderPresence>) {
        prefs.edit {
            if (owner == null || presence.isEmpty()) remove("presence")
            else {
                val buildings = JSONObject()
                for ((id, value) in presence) {
                    val entry = JSONObject().put("last", value.lastEventAt)
                    value.visit?.let {
                        entry.put("visit", JSONObject().put("id", it.id)
                            .put("entered", it.enteredAt).put("weather", it.weather.name))
                    }
                    buildings.put(id.toString(), entry)
                }
                putString("presence", JSONObject().put("owner", owner).put("buildings", buildings).toString())
            }
        }
    }

    private fun JSONArray?.ids(): Set<Int> =
        this?.let { array -> (0 until array.length()).map { array.optInt(it, -1) }.filter { it > 0 }.toSet() }
            ?: emptySet()
}
