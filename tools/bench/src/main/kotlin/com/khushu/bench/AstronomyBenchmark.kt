package com.khushu.bench

import com.khushu.engine.KhushuEngine
import com.khushu.engine.core.geo.Location
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** JMH kernel measurements for the engine hot paths. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
class AstronomyBenchmark {
    private val engine = KhushuEngine()
    private val location = Location.of(51.5072, -0.1276, 11.0)
    private val instant: Instant = Instant.parse("2025-06-15T12:00:00Z")
    private val month = YearMonth.of(2025, 6)
    private val zone = ZoneId.of("Europe/London")

    @Benchmark
    fun sunPosition() = engine.astronomy.sun.position(location, instant)

    @Benchmark
    fun moonTrackNoPath() = engine.astronomy.moon.track(location, month, zone, includePath = false)
}
