package app.pramaan.core

import app.pramaan.core.integrity.BroadJumpChecks
import app.pramaan.core.integrity.IntegrityEngine
import app.pramaan.core.manifest.AnalysisFrameDimensions
import app.pramaan.core.manifest.AthleteManifestInfo
import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.FileHashes
import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.manifest.SessionResult
import app.pramaan.core.model.Badge
import app.pramaan.core.model.CheckResult
import app.pramaan.core.model.GyroSample
import app.pramaan.core.model.TestType
import app.pramaan.core.scoring.broadjump.AthleteInfo
import app.pramaan.core.scoring.broadjump.BjNote
import app.pramaan.core.scoring.broadjump.BroadJumpAnalyzer
import app.pramaan.core.scoring.broadjump.BroadJumpLiveTracker
import app.pramaan.core.scoring.broadjump.CalibMode
import app.pramaan.core.scoring.broadjump.Foul
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

class BroadJumpAnalyzerTest {

    private val athlete160 = AthleteInfo(heightCm = 160.0, ageYears = 16, sex = "F")

    @Test
    fun `T-BJ-01 Genuine jump at 30 fps with noise`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0, flightTimeS = 0.58)),
            noiseFraction = 0.005
        )

        val result = BroadJumpAnalyzer.analyze(track, calib, athlete160)
        result.best.shouldNotBeNull()
        abs(result.best!! - 200.0).shouldBeBetween(0.0, 2.0, 0.001)

        result.attempts.size shouldBe 1
        val att = result.attempts.first()
        att.valid shouldBe true
        att.gEst.shouldNotBeNull()
        att.gEst!!.shouldBeBetween(8.8, 10.8, 0.001)

        val c2 = BroadJumpChecks.checkGravity(result)
        val c3 = BroadJumpChecks.checkHorizontalConsistency(result, athlete160.heightCm)
        val c5 = BroadJumpChecks.checkCalibrationAgreement(result)

        c2.pass shouldBe true
        c3.pass shouldBe true
        c5.pass shouldBe true

        val (checks, badge) = IntegrityEngine.evaluateAtSeal(
            testType = TestType.BROAD_JUMP,
            source = "CAMERA",
            jumpResult = null,
            gyroSamples = emptyList(),
            broadJumpResult = result,
            athleteHeightCm = athlete160.heightCm
        )
        badge shouldBe Badge.VERIFIED
    }

    @Test
    fun `T-BJ-02 Genuine jump at 60 fps`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 60,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0, flightTimeS = 0.58)),
            noiseFraction = 0.003
        )

        val result = BroadJumpAnalyzer.analyze(track, calib, athlete160)
        result.best.shouldNotBeNull()
        abs(result.best!! - 200.0).shouldBeBetween(0.0, 1.5, 0.001)

        val c2 = BroadJumpChecks.checkGravity(result)
        val c3 = BroadJumpChecks.checkHorizontalConsistency(result, athlete160.heightCm)
        val c5 = BroadJumpChecks.checkCalibrationAgreement(result)
        c2.pass shouldBe true
        c3.pass shouldBe true
        c5.pass shouldBe true
    }

    @Test
    fun `T-BJ-03 Slow motion 2x time stretch triggers C2 gravity failure`() {
        val (baseTrack, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0, flightTimeS = 0.58))
        )
        val slowTrack = SyntheticBroadJump.timeStretch(baseTrack, 2.0)

        val result = BroadJumpAnalyzer.analyze(slowTrack, calib, athlete160)
        val c2 = BroadJumpChecks.checkGravity(result)
        c2.pass shouldBe false
        val g = result.attempts.first().gEst
        g.shouldNotBeNull()
        g.shouldBeBetween(1.8, 3.2, 0.001)

        val (_, badge) = IntegrityEngine.evaluateAtSeal(
            testType = TestType.BROAD_JUMP,
            source = "CAMERA",
            jumpResult = null,
            gyroSamples = emptyList(),
            broadJumpResult = result,
            athleteHeightCm = athlete160.heightCm
        )
        badge shouldBe Badge.FLAGGED
    }

    @Test
    fun `T-BJ-04 Sped up 2x video triggers C2 gravity failure`() {
        val (baseTrack, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0, flightTimeS = 0.58))
        )
        val fastTrack = SyntheticBroadJump.timeStretch(baseTrack, 0.5)

        val result = BroadJumpAnalyzer.analyze(fastTrack, calib, athlete160)
        val c2 = BroadJumpChecks.checkGravity(result)
        c2.pass shouldBe false
    }

    @Test
    fun `T-BJ-05 Dropped frames from flight triggers C2 or C3 check failure`() {
        val (baseTrack, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0, flightTimeS = 0.58))
        )
        // Stance is ~30 frames, dip is ~9 frames. Splice 8 frames out of the middle of flight (index 42 to 49)
        val corrupted = SyntheticBroadJump.spliceFrames(baseTrack, 42..49)

        val result = BroadJumpAnalyzer.analyze(corrupted, calib, athlete160)
        val c2 = BroadJumpChecks.checkGravity(result)
        val c3 = BroadJumpChecks.checkHorizontalConsistency(result, athlete160.heightCm)
        (c2.pass && c3.pass) shouldBe false
    }

    @Test
    fun `T-BJ-06 Toes over line by 6 cm marks attempt invalid`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0, toesOverLineCm = 6.0))
        )

        val result = BroadJumpAnalyzer.analyze(track, calib, athlete160)
        result.attempts.size shouldBe 1
        val att = result.attempts.first()
        att.valid shouldBe false
        att.fouls.contains(Foul.TOES_OVER_LINE) shouldBe true
        result.best.shouldBeNull()
    }

    @Test
    fun `T-BJ-07 Toe moves 10 cm before takeoff marks STEP_OR_HOP foul`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0, hopBeforeCm = 10.0))
        )

        val result = BroadJumpAnalyzer.analyze(track, calib, athlete160)
        result.attempts.first().fouls.contains(Foul.STEP_OR_HOP) shouldBe true
        result.best.shouldBeNull()
    }

    @Test
    fun `T-BJ-08 Small hop before jump marks STEP_OR_HOP foul`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0, hopBeforeCm = 6.0))
        )

        val result = BroadJumpAnalyzer.analyze(track, calib, athlete160)
        result.attempts.first().fouls.contains(Foul.STEP_OR_HOP) shouldBe true
    }

    @Test
    fun `T-BJ-09 One foot takeoff lag 100 ms marks ONE_FOOT_TAKEOFF foul`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0, oneFootLagMs = 100.0))
        )

        val result = BroadJumpAnalyzer.analyze(track, calib, athlete160)
        result.attempts.first().fouls.contains(Foul.ONE_FOOT_TAKEOFF) shouldBe true
    }

    @Test
    fun `T-BJ-10 Fall back with hand placed 25 cm behind heels adds note and measures to hand`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0, fallBackHandCmBehindHeel = 25.0))
        )

        val result = BroadJumpAnalyzer.analyze(track, calib, athlete160)
        val att = result.attempts.first()
        att.notes.contains(BjNote.FALL_BACK) shouldBe true
        att.valid shouldBe true
        // Distance measured to hand = 200 - 25 = 175 cm
        abs(att.distanceCm - 175.0).shouldBeBetween(0.0, 3.0, 0.001)
    }

    @Test
    fun `T-BJ-11 Marker tapped at 180 cm but declared 200 cm fails C5`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0)),
            markerCm = 200.0
        )
        // Declare marker at 180 cm
        val wrongCalib = calib.copy(markerCm = 180.0)

        val result = BroadJumpAnalyzer.analyze(track, wrongCalib, athlete160)
        val c5 = BroadJumpChecks.checkCalibrationAgreement(result)
        c5.pass shouldBe false
        result.calibration.scaleDiffPct.shouldBeBetween(9.5, 13.0, 0.001)
    }

    @Test
    fun `T-BJ-12 Mirrored jump toward image left yields identical distance`() {
        val (baseTrack, baseCalib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0))
        )
        val (mirroredTrack, mirroredCalib) = SyntheticBroadJump.mirror(baseTrack, baseCalib)

        val baseRes = BroadJumpAnalyzer.analyze(baseTrack, baseCalib, athlete160)
        val mirRes = BroadJumpAnalyzer.analyze(mirroredTrack, mirroredCalib, athlete160)

        baseRes.best.shouldNotBeNull()
        mirRes.best.shouldNotBeNull()
        abs(baseRes.best!! - mirRes.best!!).shouldBeBetween(0.0, 0.5, 0.001)
    }

    @Test
    fun `T-BJ-13 Three attempts with second attempt fouled picks best valid`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(
                SyntheticAttemptSpec(distanceCm = 181.0),
                SyntheticAttemptSpec(distanceCm = 195.0, toesOverLineCm = 5.0),
                SyntheticAttemptSpec(distanceCm = 187.0)
            )
        )

        val result = BroadJumpAnalyzer.analyze(track, calib, athlete160)
        result.attempts.size shouldBe 3
        result.attempts[0].valid shouldBe true
        result.attempts[1].valid shouldBe false
        result.attempts[2].valid shouldBe true

        result.best.shouldNotBeNull()
        abs(result.best!! - 187.0).shouldBeBetween(0.0, 2.0, 0.001)
        result.bestIndex shouldBe 3
    }

    @Test
    fun `T-BJ-14 Four attempts in one video counts only first 3`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(
                SyntheticAttemptSpec(distanceCm = 180.0),
                SyntheticAttemptSpec(distanceCm = 185.0),
                SyntheticAttemptSpec(distanceCm = 190.0),
                SyntheticAttemptSpec(distanceCm = 195.0)
            )
        )

        val result = BroadJumpAnalyzer.analyze(track, calib, athlete160)
        result.notes.contains(BjNote.EXTRA_ATTEMPTS_IGNORED) shouldBe true
        result.attempts[0].counted shouldBe true
        result.attempts[1].counted shouldBe true
        result.attempts[2].counted shouldBe true
        result.attempts[3].counted shouldBe false
        // Best should be 190 cm from attempt 3, not 195 cm from attempt 4
        result.bestIndex shouldBe 3
        abs(result.best!! - 190.0).shouldBeBetween(0.0, 2.0, 0.001)
    }

    @Test
    fun `T-BJ-15 Athlete only stands produces no attempts`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = emptyList()
        )

        val result = BroadJumpAnalyzer.analyze(track, calib, athlete160)
        result.best.shouldBeNull()
        result.notes.contains(BjNote.NO_VALID_ATTEMPT) shouldBe true
    }

    @Test
    fun `T-BJ-16 Height only mode produces approx distance and CALIB_APPROX flag`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0))
        )
        val heightCalib = calib.copy(mode = CalibMode.HEIGHT_ONLY)

        val result = BroadJumpAnalyzer.analyze(track, heightCalib, athlete160)
        result.best.shouldNotBeNull()
        // Within 4% of 200 = 8 cm
        abs(result.best!! - 200.0).shouldBeBetween(0.0, 8.0, 0.001)

        val c5 = BroadJumpChecks.checkCalibrationAgreement(result)
        c5.pass shouldBe false
        c5.reason shouldBe "CALIB_APPROX"

        val (_, badge) = IntegrityEngine.evaluateAtSeal(
            testType = TestType.BROAD_JUMP,
            source = "CAMERA",
            jumpResult = null,
            gyroSamples = emptyList(),
            broadJumpResult = result,
            athleteHeightCm = athlete160.heightCm
        )
        badge shouldBe Badge.FLAGGED
    }

    @Test
    fun `T-BJ-17 Stance ahead of line by 30 cm marks foul`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0, toesOverLineCm = 30.0))
        )

        val result = BroadJumpAnalyzer.analyze(track, calib, athlete160)
        result.attempts.first().valid shouldBe false
        result.attempts.first().fouls.contains(Foul.TOES_OVER_LINE) shouldBe true
    }

    @Test
    fun `T-BJ-18 Axis tilt 25 deg blocks calibration`() {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = 200.0)),
            tiltDeg = 25.0
        )

        val result = BroadJumpAnalyzer.analyze(track, calib, athlete160)
        result.calibration.axisTiltDeg.shouldBeBetween(24.0, 26.0, 0.001)
        val c5 = BroadJumpChecks.checkCalibrationAgreement(result)
        c5.pass shouldBe false
    }

    @Test
    fun `T-BJ-19 Live tracker on stream detects attempts and approximate distance`() {
        val (track, _) = SyntheticBroadJump.generate(
            heightCm = 160.0,
            fps = 30,
            attempts = listOf(
                SyntheticAttemptSpec(distanceCm = 181.0),
                SyntheticAttemptSpec(distanceCm = 187.0)
            )
        )

        val tracker = BroadJumpLiveTracker(athleteHeightCm = 160.0)
        var maxAttempts = 0
        var lastApprox: Double? = null

        for (frame in track) {
            val state = tracker.onFrame(frame, frameW = 1280, frameH = 720)
            maxAttempts = maxOf(maxAttempts, state.attemptsDetected)
            if (state.lastApproxCm != null) {
                lastApprox = state.lastApproxCm
            }
        }

        maxAttempts shouldBe 2
        lastApprox.shouldNotBeNull()
        abs(lastApprox!! - 187.0).shouldBeBetween(0.0, 15.0, 0.001)
    }

    @Test
    fun `T-BJ-20 Canonical JSON roundtrip of SBJ manifest`() {
        val (_, calib) = SyntheticBroadJump.generate(heightCm = 160.0, fps = 30)

        val manifest = SessionManifest(
            v = 1,
            sessionId = "01JA7Q8R4C2D3E4F5G6H7J8K9M",
            test = TestType.BROAD_JUMP,
            source = "CAMERA",
            athlete = AthleteManifestInfo("ATH-01", "P.K.", 16, "F", 160.0),
            devicePublicKey = "mockKey",
            capturedAt = "2026-10-11T12:00:00Z",
            fps = 30,
            hashes = FileHashes(
                video = "vidHash",
                frames = "framesHash",
                gyro = "gyroHash",
                calibFrame = "calibHash",
                stills = mapOf("att1" to "hash1")
            ),
            result = SessionResult(unit = "cm", best = 200.0),
            checks = emptyList(),
            badge = Badge.VERIFIED,
            analyzerVersion = "bj-1.0.0",
            analysisFrame = AnalysisFrameDimensions(640, 480),
            calibration = calib
        )

        val encoded1 = CanonicalJson.encodeToString(manifest)
        val decoded = CanonicalJson.decodeFromString<SessionManifest>(encoded1)
        val encoded2 = CanonicalJson.encodeToString(decoded)

        encoded1 shouldBe encoded2
        decoded.test shouldBe TestType.BROAD_JUMP
        decoded.calibration?.mode shouldBe CalibMode.MARKER
        decoded.hashes.calibFrame shouldBe "calibHash"
    }
}
