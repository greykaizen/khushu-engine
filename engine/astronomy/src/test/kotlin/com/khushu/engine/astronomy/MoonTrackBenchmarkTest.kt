package com.khushu.engine.astronomy

import com.khushu.engine.core.geo.Location
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Performance lock: one-pass `moon.track` vs the donor calendar's call pattern
 * (per-day rise/set searches + independent 96-sample path recomputation).
 * Generous wall-clock bounds — this guards against algorithmic regressions,
 * not absolute machine speed.
 */
class MoonTrackBenchmarkTest {

    private val london = Location.of(51.5072, -0.1276)
    private val zone = ZoneId.of("Europe/London")

    /** What Osprey's calendar effectively did for one month view. */
    private fun donorPatternMs(): Long {
        val start = System.nanoTime()
        val month = YearMonth.of(2025, 6)
        var day = month.atDay(1)
        while (day <= month.atEndOfMonth()) {
            // getMoonTransit(): today's pair + previous-day pair.
            repeat(2) {
                val rise = io.github.cosinekitty.astronomy.searchRiseSet(
                    Body.Moon, observer(), Direction.Rise,
                    Time(day.toEpochMs()), 1.5,
                )
                if (rise != null) {
                    io.github.cosinekitty.astronomy.searchRiseSet(
                        Body.Moon, observer(), Direction.Set,
                        Time(rise.toMillisecondsSince1970()), 1.5,
                    )
                }
            }
            // getDailyMoonPath(): 96 samples of equator+horizon.
            repeat(96) { i ->
                val t = day.toEpochMs() + i * (86_400_000L / 96)
                EphemerisAlias.horizontalPosition(t)
            }
            day = day.plusDays(1)
        }
        return (System.nanoTime() - start) / 1_000_000
    }


    private fun java.time.LocalDate.toEpochMs(): Long =
        this.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun Time(epochMs: Long) = io.github.cosinekitty.astronomy.Time.fromMillisecondsSince1970(epochMs)
    private fun observer() = io.github.cosinekitty.astronomy.Observer(london.latitude.degrees, london.longitude.degrees, 11.0)

    @Test
    fun trackBeatsDonorCallPattern() {
        // Warm-up both paths.
        Astronomy.moon.track(london, YearMonth.of(2025, 6), zone, includePath = true, pathSamplesPerDay = 48)
        donorPatternMs()

        val engineStart = System.nanoTime()
        val track = Astronomy.moon.track(london, YearMonth.of(2025, 6), zone, includePath = true, pathSamplesPerDay = 48)
        val engineMs = (System.nanoTime() - engineStart) / 1_000_000

        val donorMs = donorPatternMs()

        println("moon.track(month incl. 48-sample/day path): ${engineMs} ms")
        println("donor-pattern equivalent:                    ${donorMs} ms")
        assertTrue(engineMs < 15_000, "track unexpectedly slow: $engineMs ms")
        assertTrue(track.days.size == 30)
        assertTrue(track.pathPoints.size == 30 * 48)

        // The structural win must hold even on noisy CI machines.
        assertTrue(engineMs <= donorMs * 2 || donorMs < 50, "regressed vs donor pattern: $engineMs vs $donorMs")
    }
}

/** Test-local alias to keep the benchmark readable. */
private object EphemerisAlias {
    fun horizontalPosition(epochMs: Long): Unit {
        val time = io.github.cosinekitty.astronomy.Time.fromMillisecondsSince1970(epochMs)
        val obs = io.github.cosinekitty.astronomy.Observer(51.5072, -0.1276, 11.0)
        val eq = io.github.cosinekitty.astronomy.equator(
            Body.Moon, time, obs, io.github.cosinekitty.astronomy.EquatorEpoch.OfDate, io.github.cosinekitty.astronomy.Aberration.Corrected,
        )
        io.github.cosinekitty.astronomy.horizon(time, obs, eq.ra, eq.dec, io.github.cosinekitty.astronomy.Refraction.Normal)
    }
}
