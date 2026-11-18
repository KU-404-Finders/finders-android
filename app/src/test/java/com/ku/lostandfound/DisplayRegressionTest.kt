package com.ku.lostandfound

import com.ku.lostandfound.ui.chat.ChatTimeFormatter
import com.ku.lostandfound.ui.post.screen.imagePanBounds
import kotlin.math.sign
import org.junit.Assert.*
import org.junit.Test

class DisplayRegressionTest {
    @Test fun mixedTimestampComparatorIsTransitiveAndSymmetric() {
        val values = listOf(null, "zzz", "2026-01-01T00:00:00Z", "2026-01-01T09:01", "2025-12-31T21:02:00-03:00")
        val compare = ChatTimeFormatter::compareNewest
        for (a in values) for (b in values) {
            assertEquals(-compare(b, a).sign, compare(a, b).sign)
            for (c in values) if (compare(a, b) <= 0 && compare(b, c) <= 0) assertTrue(compare(a, c) <= 0)
        }
        assertEquals(values.drop(2), values.reversed().sortedWith(Comparator(compare)).drop(2))
        assertEquals(0, compare(null, "zzz"))
        assertTrue(compare("zzz", values[2]) < 0)
    }

    @Test fun imageBoundsFollowFitScaleAndViewport() {
        assertEquals(0f to 0f, imagePanBounds(200, 100, 400, 400, 1f))
        assertEquals(200f to 0f, imagePanBounds(200, 100, 400, 400, 2f))
        assertEquals(1200f to 600f, imagePanBounds(200, 100, 800, 400, 4f))
        assertEquals(0f to 100f, imagePanBounds(100, 200, 800, 200, 2f))
    }
}
