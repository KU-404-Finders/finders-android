
package com.ku.lostandfound.ui.chat

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ChatTimeFormatter {

    private val koreaZone = ZoneId.of("Asia/Seoul")

    private val timeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)

    private val dateFormatter =
        DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

    private fun parseOffsetDateTime(raw: String): LocalDateTime? {
        return runCatching {
            OffsetDateTime.parse(raw)
                .atZoneSameInstant(koreaZone)
                .toLocalDateTime()
        }.getOrNull()
    }

    private fun parseToKst(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null

        val value = raw.trim().replace(' ', 'T')

        parseOffsetDateTime(value)?.let { return it }

        // Chat API timestamps are Instant values and therefore carry UTC/offset data.
        // Keep this fallback only for old persisted UI data; treating it as UTC would
        // shift an already-local legacy value a second time.
        return runCatching { LocalDateTime.parse(value) }.getOrNull()
    }

    fun formatTime(raw: String?): String {
        return parseToKst(raw)?.format(timeFormatter) ?: ""
    }

    fun formatListTime(raw: String?): String {
        val dateTime = parseToKst(raw) ?: return ""
        val today = LocalDate.now(koreaZone)
        val date = dateTime.toLocalDate()

        return when {
            date == today -> dateTime.format(timeFormatter)
            date == today.minusDays(1) -> "어제"
            date.year == today.year ->
                dateTime.format(dateFormatter)
            else -> dateTime.format(
                DateTimeFormatter.ofPattern(
                    "yyyy.M.d",
                    Locale.KOREAN
                )
            )
        }
    }

    /** Formats administrative timestamps with a year so audit records are never ambiguous. */
    fun formatFullDateTime(raw: String?): String {
        return parseToKst(raw)?.format(
            DateTimeFormatter.ofPattern("yyyy.M.d HH:mm", Locale.KOREAN)
        ) ?: ""
    }

    private fun instant(raw: String?): java.time.Instant? {
        val value = raw?.trim()?.replace(' ', 'T') ?: return null
        return runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).atZone(koreaZone).toInstant() }.getOrNull()
    }

    /** iOS chatDayLabel: "오늘" or "2026년 11월 28일 금요일" (KST). */
    fun formatDayLabel(raw: String?): String {
        val dateTime = parseToKst(raw) ?: return raw?.take(10).orEmpty()
        val date = dateTime.toLocalDate()
        return if (date == LocalDate.now(koreaZone)) "오늘"
        else date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN))
    }

    fun dayKey(raw: String?): String? = parseToKst(raw)?.toLocalDate()?.toString() ?: raw?.take(10)

    /** "3분 전" style label like iOS RelativeDateTimeFormatter; empty when the time does not parse. */
    fun formatRelative(raw: String?, now: Long = System.currentTimeMillis()): String {
        val millis = instant(raw)?.toEpochMilli() ?: return ""
        return android.text.format.DateUtils.getRelativeTimeSpanString(
            millis, now, android.text.format.DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }

    fun compareNewest(first: String?, second: String?): Int = compareValues(instant(first), instant(second))
}
