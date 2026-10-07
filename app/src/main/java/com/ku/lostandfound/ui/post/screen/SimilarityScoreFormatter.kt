package com.ku.lostandfound.ui.post.screen

import java.math.BigDecimal
import java.math.RoundingMode

internal fun formatSimilarityScore(score: Double?): String =
    if (score == null || !score.isFinite()) {
        "—"
    } else {
        BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).toPlainString()
    }
