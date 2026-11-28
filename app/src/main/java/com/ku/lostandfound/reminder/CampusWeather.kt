package com.ku.lostandfound.reminder

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal class CampusWeather(
    private val calls: Call.Factory = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun lookup(enteredAt: Long? = null): EntryWeather {
        val instant = enteredAt?.let(Instant::ofEpochMilli) ?: clock.instant()
        if (instant.isAfter(clock.instant())) return EntryWeather.UNKNOWN
        val entry = LocalDateTime.ofInstant(instant, ZoneId.of("Asia/Seoul"))
        return withTimeoutOrNull(8_000) { requestWeather(entry, enteredAt != null) } ?: EntryWeather.UNKNOWN
    }

    private suspend fun requestWeather(entry: LocalDateTime, allowHistoricalHour: Boolean): EntryWeather =
        suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(
            "https://api.open-meteo.com/v1/forecast?latitude=37.5418&longitude=127.0760" +
                "&timezone=Asia%2FSeoul&current=precipitation&hourly=precipitation,precipitation_probability" +
                "&past_days=1&forecast_days=2&precipitation_unit=mm",
        ).build()
        val call = calls.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(EntryWeather.UNKNOWN)
            }

            override fun onResponse(call: Call, response: Response) {
                val weather = response.use {
                    runCatching {
                        if (!it.isSuccessful) EntryWeather.UNKNOWN else classify(it.body?.string().orEmpty(), entry, allowHistoricalHour)
                    }.getOrDefault(EntryWeather.UNKNOWN)
                }
                continuation.resume(weather)
            }
        })
    }

    companion object {
        const val ATTRIBUTION_URL = "https://open-meteo.com/"

        fun classify(json: String, entry: LocalDateTime, allowHistoricalHour: Boolean = false): EntryWeather = runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            val currentAmount = runCatching {
                val current = root.getAsJsonObject("current")
                val currentTime = LocalDateTime.parse(current.get("time").asString)
                val interval = current.get("interval").asLong
                require(interval in 1..3600)
                val coversEntry = if (allowHistoricalHour) {
                    !entry.isBefore(currentTime.minusSeconds(interval)) && entry.isBefore(currentTime)
                } else {
                    !entry.isBefore(currentTime) && entry.isBefore(currentTime.plusSeconds(interval))
                }
                require(coversEntry)
                current.amount("precipitation")
            }.getOrElse {
                if (!allowHistoricalHour) throw it
                0.0
            }

            // Open-Meteo timestamps accumulations and probabilities at the end of the preceding hour.
            val endOfHour = entry.truncatedTo(ChronoUnit.HOURS).plusHours(1)
            val hourly = root.getAsJsonObject("hourly")
            val times = hourly.getAsJsonArray("time")
            val index = times.indexOfFirst { LocalDateTime.parse(it.asString) == endOfHour }
            require(index >= 0)
            val amount = hourly.getAsJsonArray("precipitation")[index].asDouble
            val probability = hourly.getAsJsonArray("precipitation_probability")[index].asDouble
            require(amount.isFinite() && amount >= 0 && probability in 0.0..100.0)
            if (currentAmount > 0 || (amount > 0 && probability >= 50)) EntryWeather.RAIN else EntryWeather.DRY
        }.getOrDefault(EntryWeather.UNKNOWN)

        private fun JsonObject.amount(key: String): Double =
            get(key).asDouble.also { require(it.isFinite() && it >= 0) }
    }
}
