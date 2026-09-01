package com.khushu.engine.core.observance

/**
 * Shared angle normalization — extracted from astronomy.AstroMath when
 * qibla became the second consumer (AGENTS §2 extraction trigger).
 * Previously: astronomy had an internal copy, qibla re-implemented it
 * inline 4 times (flagged in the v1.13 audit).
 */
object AngleMath {
    /** Normalize an angle to [0, 360). Accepts any finite input. */
    fun normalizeDeg(deg: Double): Double {
        val r = deg % 360.0
        return if (r < 0) r + 360.0 else r
    }
}
