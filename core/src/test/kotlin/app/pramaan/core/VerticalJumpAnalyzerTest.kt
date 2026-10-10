package app.pramaan.core

import app.pramaan.core.scoring.VerticalJumpAnalyzer
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class VerticalJumpAnalyzerTest {

    private val heightCm = 175.0
    private val fps = 30

    @Test
    fun `T1 synthetic jump with 0_500s flight time passes height, gravity and agreement checks`() {
        // T1: Flight 0.500 s, 30 fps, small noise
        val track = SyntheticTracks.countermovementJump(
            flightTimeSeconds = 0.500,
            fps = fps,
            heightCm = heightCm,
            noiseStdDev = 0.0005,
            seed = 12345L
        )

        val result = VerticalJumpAnalyzer.analyze(track, heightCm)

        result.hasValidJump shouldBe true
        result.attempts.size shouldBe 1

        val attempt = result.attempts.first()
        attempt.valid shouldBe true

        // 1. Flight height within ±2 cm of 30.7 cm
        attempt.flightCm shouldBe (30.7 plusOrMinus 2.0)

        // 2. Gravity within 7.31 - 12.31 m/s^2 (C2 pass rule)
        (attempt.gravity >= 7.31) shouldBe true
        (attempt.gravity <= 12.31) shouldBe true

        // 3. Two-method agreement within max(4 cm, 20% of flight) (C3 pass rule)
        val agreementDiff = Math.abs(attempt.flightCm - attempt.hipRiseCm)
        val maxAllowedDiff = Math.max(4.0, 0.20 * attempt.flightCm)
        (agreementDiff <= maxAllowedDiff) shouldBe true
    }

    @Test
    fun `T2 slowed video with timestamps x2 yields g_est about 2_45 and fails C2`() {
        val genuineTrack = SyntheticTracks.countermovementJump(
            flightTimeSeconds = 0.500,
            fps = fps,
            heightCm = heightCm,
            noiseStdDev = 0.0
        )
        // Simulate slow motion (stretching timestamps by factor 2.0)
        val slowedTrack = SyntheticTracks.scaleTimestamps(genuineTrack, factor = 2.0)

        val result = VerticalJumpAnalyzer.analyze(slowedTrack, heightCm)

        result.attempts.size shouldBe 1
        val attempt = result.attempts.first()

        // g_est should be ~ 2.45 m/s^2 (within 2.0 - 3.0) and strictly < 7.31 (C2 fails)
        attempt.gravity shouldBe (2.45 plusOrMinus 0.5)
        (attempt.gravity < 7.31) shouldBe true
    }
    
    @Test
    fun `T2b long jump slowed down yielding flight over 1_0s still reports estimated gravity and fails C2`() {
        val genuineTrack = SyntheticTracks.countermovementJump(
            flightTimeSeconds = 0.550,
            fps = fps,
            heightCm = heightCm,
            noiseStdDev = 0.0
        )
        // 0.55s * 2.0 = 1.10s flight time
        val slowedTrack = SyntheticTracks.scaleTimestamps(genuineTrack, factor = 2.0)

        val result = VerticalJumpAnalyzer.analyze(slowedTrack, heightCm)

        result.attempts.size shouldBe 1
        result.bestGravity shouldBe (2.45 plusOrMinus 0.5)
        (result.bestGravity < 7.31) shouldBe true
    }

    @Test
    fun `T3 sped-up video with timestamps x0_5 yields g_est about 39 and fails C2`() {
        val genuineTrack = SyntheticTracks.countermovementJump(
            flightTimeSeconds = 0.500,
            fps = fps,
            heightCm = heightCm,
            noiseStdDev = 0.0
        )
        // Simulate sped-up video (compressing timestamps by factor 0.5)
        val spedUpTrack = SyntheticTracks.scaleTimestamps(genuineTrack, factor = 0.5)

        val result = VerticalJumpAnalyzer.analyze(spedUpTrack, heightCm)

        result.attempts.size shouldBe 1
        val attempt = result.attempts.first()

        // g_est should be ~ 39 m/s^2 and strictly > 12.31 (C2 fails)
        attempt.gravity shouldBe (39.0 plusOrMinus 6.0)
        (attempt.gravity > 12.31) shouldBe true
    }

    @Test
    fun `three jumps in one take picks the best jump`() {
        // Three jumps: 0.35s (h ~ 15 cm), 0.50s (h ~ 30.7 cm), 0.42s (h ~ 21.6 cm)
        val track = SyntheticTracks.multipleJumps(
            flightTimes = listOf(0.35, 0.50, 0.42),
            fps = fps,
            heightCm = heightCm,
            noiseStdDev = 0.0
        )

        val result = VerticalJumpAnalyzer.analyze(track, heightCm)

        result.attempts.size shouldBe 3
        result.attempts.all { it.valid } shouldBe true

        // Best jump should be attempt #2 (flight 0.50s)
        result.bestFlightCm shouldBe (30.7 plusOrMinus 2.0)
        result.bestAttempt?.attemptNumber shouldBe 2
    }

    @Test
    fun `a take with no jump yields no valid attempts`() {
        val standingTrack = SyntheticTracks.standing(
            durationSeconds = 2.5,
            fps = fps,
            heightCm = heightCm
        )

        val result = VerticalJumpAnalyzer.analyze(standingTrack, heightCm)

        result.hasValidJump shouldBe false
        result.attempts.isEmpty() shouldBe true
        result.bestFlightCm shouldBe 0.0
    }

    @Test
    fun `a flight under 0_12 s is rejected as invalid`() {
        // Flight time 0.08 s (below minimum 0.12 s)
        val tinyJumpTrack = SyntheticTracks.countermovementJump(
            flightTimeSeconds = 0.08,
            fps = fps,
            heightCm = heightCm,
            noiseStdDev = 0.0
        )

        val result = VerticalJumpAnalyzer.analyze(tinyJumpTrack, heightCm)

        result.attempts.size shouldBe 1
        val attempt = result.attempts.first()
        attempt.valid shouldBe false
        attempt.invalidReason?.contains("0.12") shouldBe true
        result.hasValidJump shouldBe false
    }
}
