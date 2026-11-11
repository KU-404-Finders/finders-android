
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

    /**
     * Chat timestamps are UTC Instants and are ordered by their instant. Offsetless
     * legacy values retain lexicographic local ordering as a safe fallback.
     */
    fun compareNewest(first: String?, second: String?): Int {
        val firstValue = first?.trim().orEmpty()
        val secondValue = second?.trim().orEmpty()
        val firstInstant = runCatching {
            OffsetDateTime.parse(firstValue.replace(' ', 'T')).toInstant()
        }.getOrNull()
        val secondInstant = runCatching {
            OffsetDateTime.parse(secondValue.replace(' ', 'T')).toInstant()
        }.getOrNull()

        return if (firstInstant != null && secondInstant != null) {
            firstInstant.compareTo(secondInstant)
        } else {
            val firstLocal = parseToKst(firstValue)
            val secondLocal = parseToKst(secondValue)
            if (firstLocal != null && secondLocal != null) {
                firstLocal.compareTo(secondLocal)
            } else {
                firstValue.compareTo(secondValue)
            }
        }
    }
}
