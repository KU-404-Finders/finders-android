package com.ku.lostandfound.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTimeFormatterTest {

    @Test
    fun utcTimestamp_isConvertedToKoreaTime() {
        assertEquals("12:00", ChatTimeFormatter.formatTime("2026-11-09T03:00:00Z"))
    }

    @Test
    fun utcTimestamp_crossingDateBoundary_isConvertedToNextKoreaDay() {
        assertEquals("08:30", ChatTimeFormatter.formatTime("2026-11-09T23:30:00+00:00"))
    }

    @Test
    fun koreaOffset_isNotConvertedTwice() {
        assertEquals("09:27", ChatTimeFormatter.formatTime("2026-11-09T09:27:12.123+09:00"))
    }

    @Test
    fun legacyOffsetlessTimestamp_isDisplayedWithoutDoubleConversion() {
        assertEquals("09:27", ChatTimeFormatter.formatTime("2026-11-09T09:27:00"))
    }

    @Test
    fun malformedTimestamp_isSafe() {
        assertEquals("", ChatTimeFormatter.formatTime("not-a-timestamp"))
    }
}
