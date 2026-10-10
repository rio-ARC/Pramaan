package app.pramaan.core

import app.pramaan.core.scoring.SitUpAnalyzer
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SitUpAnalyzerTest {

    @Test
    fun `T4 22 synthetic sit-ups plus 3 that stop at 50 deg yields count 22 and 3 rejected NOT_UP`() {
        // Interleave 22 full reps (75°) with 3 partial reps (50°)
        val peakAngles = mutableListOf<Double>()
        repeat(7) { peakAngles.add(75.0) }
        peakAngles.add(50.0) // partial 1
        repeat(8) { peakAngles.add(75.0) }
        peakAngles.add(50.0) // partial 2
        repeat(7) { peakAngles.add(75.0) }
        peakAngles.add(50.0) // partial 3

        peakAngles.size shouldBe 25

        // Cycle 1.0s, pause 0.2s -> Each rep is 1.2s
        val track = SyntheticTracks.sitUps(
            peakAngles = peakAngles,
            fps = 30,
            cycleDuration = 1.0,
            pauseDuration = 0.2,
            noiseStdDev = 0.0
        )

        // Give sufficient maxDuration to allow all 25 attempts
        val result = SitUpAnalyzer.analyze(track, maxDurationSeconds = 60.0)

        result.totalReps shouldBe 22
        result.rejectedReps.size shouldBe 3
        result.rejectedReps.all { it.reason == "NOT_UP" } shouldBe true
    }

    @Test
    fun `reps completing after 30s limit are rejected as TIME_EXPIRED`() {
        val peakAngles = List(35) { 75.0 }
        // Each rep is 1.0s + 0.2s = 1.2s -> 25 reps take 30.0s, remaining take up to 42s
        val track = SyntheticTracks.sitUps(
            peakAngles = peakAngles,
            fps = 30,
            cycleDuration = 1.0,
            pauseDuration = 0.2
        )

        val result = SitUpAnalyzer.analyze(track, maxDurationSeconds = 30.0)

        // Reps after 30s should be marked TIME_EXPIRED
        (result.totalReps in 24..26) shouldBe true
        result.rejectedReps.any { it.reason == "TIME_EXPIRED" } shouldBe true
    }

    @Test
    fun `stationary lying down track produces 0 reps`() {
        val track = SyntheticTracks.sitUps(
            peakAngles = emptyList(),
            fps = 30
        )

        val result = SitUpAnalyzer.analyze(track)
        result.totalReps shouldBe 0
        result.rejectedReps.isEmpty() shouldBe true
    }
}
