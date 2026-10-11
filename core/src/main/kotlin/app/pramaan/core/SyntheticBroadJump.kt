package app.pramaan.core

import app.pramaan.core.model.Landmark
import app.pramaan.core.model.PoseFrame
import app.pramaan.core.model.PoseLandmarks
import app.pramaan.core.scoring.broadjump.CalibMode
import app.pramaan.core.scoring.broadjump.GroundCalibration
import app.pramaan.core.scoring.broadjump.NormPoint
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class SyntheticAttemptSpec(
    val distanceCm: Double = 200.0,
    val flightTimeS: Double = 0.58,
    val toesOverLineCm: Double = 0.0,
    val hopBeforeCm: Double = 0.0,
    val oneFootLagMs: Double = 0.0,
    val fallBackHandCmBehindHeel: Double = 0.0,
    val fallBackHipCmBehindHeel: Double = 0.0,
    val kneeDipDeg: Double = 100.0,
    val armSwingUp: Boolean = true
)

object SyntheticBroadJump {

    fun generate(
        heightCm: Double = 160.0,
        fps: Int = 30,
        attempts: List<SyntheticAttemptSpec> = listOf(SyntheticAttemptSpec()),
        frameW: Int = 1280,
        frameH: Int = 720,
        lineNormX: Double = 0.20,
        markerNormX: Double = 0.55,
        markerCm: Double = 200.0,
        groundNormY: Double = 0.80,
        tiltDeg: Double = 0.0,
        noiseFraction: Double = 0.0,
        seed: Long = 42L
    ): Pair<List<PoseFrame>, GroundCalibration> {
        val random = Random(seed)

        val tiltRad = Math.toRadians(tiltDeg)
        val linePxX = lineNormX * frameW
        val linePxY = groundNormY * frameH

        val distNormX = markerNormX - lineNormX
        val markerPxDist = distNormX * frameW
        val markerPxX = linePxX + markerPxDist * cos(tiltRad)
        val markerPxY = linePxY + markerPxDist * sin(tiltRad)

        val calib = GroundCalibration(
            mode = CalibMode.MARKER,
            frameW = frameW,
            frameH = frameH,
            calibFrameTNs = 100_000_000L,
            line = NormPoint(linePxX / frameW, linePxY / frameH),
            marker = NormPoint(markerPxX / frameW, markerPxY / frameH),
            markerCm = markerCm
        )

        val pxPerCm = markerPxDist / markerCm
        val bodyPx = heightCm * pxPerCm
        val gCm = 9.81 * 100.0 // 981 cm/s^2

        val frames = mutableListOf<PoseFrame>()
        val dtNs = (1_000_000_000L / fps)
        var currentTNs = 0L

        for (attempt in attempts) {
            // 1. Initial standing still window: 1.0 s
            val stanceFrames = (1.0 * fps).toInt()
            val toeRestFrontCm = -0.5 + attempt.toesOverLineCm
            val rearHeelRestCm = toeRestFrontCm - 24.0 // 24 cm foot
            val hipRestCm = (toeRestFrontCm + rearHeelRestCm) / 2.0

            val footRestY = linePxY
            val hipRestY = footRestY - 0.53 * bodyPx
            val headRestY = footRestY - bodyPx

            for (i in 0 until stanceFrames) {
                // If hopBefore is specified, inject a hop or toe movement in second half of stance
                var currToeCm = toeRestFrontCm
                var currFootY = footRestY
                if (attempt.hopBeforeCm > 0.0 && i > stanceFrames / 2) {
                    currToeCm += attempt.hopBeforeCm
                    // slight hop lift
                    currFootY -= (attempt.hopBeforeCm * 0.5 * pxPerCm)
                }

                frames.add(
                    buildPoseFrame(
                        tNs = currentTNs,
                        hipCm = hipRestCm,
                        hipY = hipRestY,
                        rearHeelCm = rearHeelRestCm,
                        toeCm = currToeCm,
                        footY = currFootY,
                        headY = headRestY,
                        frameW = frameW,
                        frameH = frameH,
                        pxPerCm = pxPerCm,
                        linePxX = linePxX,
                        noiseFraction = noiseFraction,
                        bodyPx = bodyPx,
                        random = random,
                        kneeAngleDeg = 175.0,
                        wristsAboveShoulders = false
                    )
                )
                currentTNs += dtNs
            }

            // 2. Prep / countermovement dip window: 0.3 s
            val dipFrames = maxOf(4, (0.3 * fps).toInt())
            for (i in 0 until dipFrames) {
                val progress = (i + 1).toDouble() / dipFrames
                val dipDepth = (1.0 - sin(progress * PI / 2.0)) * 0.10 * bodyPx
                val dipKneeAngle = 175.0 - (175.0 - attempt.kneeDipDeg) * progress
                val armUp = attempt.armSwingUp && progress > 0.7

                frames.add(
                    buildPoseFrame(
                        tNs = currentTNs,
                        hipCm = hipRestCm,
                        hipY = hipRestY + dipDepth,
                        rearHeelCm = rearHeelRestCm,
                        toeCm = toeRestFrontCm,
                        footY = footRestY,
                        headY = headRestY + dipDepth,
                        frameW = frameW,
                        frameH = frameH,
                        pxPerCm = pxPerCm,
                        linePxX = linePxX,
                        noiseFraction = noiseFraction,
                        bodyPx = bodyPx,
                        random = random,
                        kneeAngleDeg = dipKneeAngle,
                        wristsAboveShoulders = armUp
                    )
                )
                currentTNs += dtNs
            }

            // 3. Flight window
            val flightFrames = (attempt.flightTimeS * fps).toInt()
            val T = attempt.flightTimeS
            val vx = attempt.distanceCm / T // cm/s
            val vy0 = 0.5 * gCm * T // cm/s

            val hipStartCm = hipRestCm
            val hipLandingCm = attempt.distanceCm - 12.0 // center of feet at landing

            for (i in 0 until flightFrames) {
                val tSec = (i + 1).toDouble() / fps
                val tClamped = minOf(tSec, T)

                // Parabolic trajectory of center of mass / hip:
                val hipForwardCm = hipStartCm + (hipLandingCm - hipStartCm) * (tClamped / T)
                val hipRiseCm = vy0 * tClamped - 0.5 * gCm * tClamped * tClamped
                val hipY = hipRestY - hipRiseCm * pxPerCm

                // Feet are tucked/off ground during flight
                val footRiseCm = maxOf(5.0, hipRiseCm * 0.8)
                val footY = footRestY - footRiseCm * pxPerCm

                val heelCm = hipForwardCm - 10.0 + (10.0 * (tClamped / T))
                val toeCm = heelCm + 24.0

                // If one foot lag is injected
                val lagS = attempt.oneFootLagMs / 1000.0
                val leftFootY = if (tSec <= lagS) footRestY else footY

                frames.add(
                    buildPoseFrame(
                        tNs = currentTNs,
                        hipCm = hipForwardCm,
                        hipY = hipY,
                        rearHeelCm = heelCm,
                        toeCm = toeCm,
                        footY = footY,
                        leftFootY = leftFootY,
                        headY = hipY - 0.47 * bodyPx,
                        frameW = frameW,
                        frameH = frameH,
                        pxPerCm = pxPerCm,
                        linePxX = linePxX,
                        noiseFraction = noiseFraction,
                        bodyPx = bodyPx,
                        random = random,
                        kneeAngleDeg = 140.0,
                        wristsAboveShoulders = attempt.armSwingUp
                    )
                )
                currentTNs += dtNs
            }

            // 4. Landing and hold window: 1.0 s
            val landingFrames = (1.0 * fps).toInt()
            val landedRearHeelCm = attempt.distanceCm
            val landedToeCm = landedRearHeelCm + 24.0
            val landedHipCm = landedRearHeelCm + 12.0

            for (i in 0 until landingFrames) {
                var wristY = footRestY - 0.40 * bodyPx
                var wristCm = landedHipCm
                if (attempt.fallBackHandCmBehindHeel > 0.0 && i > 3) {
                    wristY = footRestY // touches floor
                    wristCm = landedRearHeelCm - attempt.fallBackHandCmBehindHeel
                }

                var hipLandY = hipRestY
                var currHipCm = landedHipCm
                if (attempt.fallBackHipCmBehindHeel > 0.0 && i > 3) {
                    hipLandY = footRestY - 0.05 * bodyPx // hip near floor
                    currHipCm = landedRearHeelCm - attempt.fallBackHipCmBehindHeel
                }

                frames.add(
                    buildPoseFrame(
                        tNs = currentTNs,
                        hipCm = currHipCm,
                        hipY = hipLandY,
                        rearHeelCm = landedRearHeelCm,
                        toeCm = landedToeCm,
                        footY = footRestY,
                        headY = headRestY,
                        frameW = frameW,
                        frameH = frameH,
                        pxPerCm = pxPerCm,
                        linePxX = linePxX,
                        noiseFraction = noiseFraction,
                        bodyPx = bodyPx,
                        random = random,
                        kneeAngleDeg = 160.0,
                        wristsAboveShoulders = false,
                        overrideWristCm = if (attempt.fallBackHandCmBehindHeel > 0.0 && i > 3) wristCm else null,
                        overrideWristY = if (attempt.fallBackHandCmBehindHeel > 0.0 && i > 3) wristY else null
                    )
                )
                currentTNs += dtNs
            }
        }

        return Pair(frames, calib)
    }

    private fun buildPoseFrame(
        tNs: Long,
        hipCm: Double,
        hipY: Double,
        rearHeelCm: Double,
        toeCm: Double,
        footY: Double,
        leftFootY: Double = footY,
        headY: Double,
        frameW: Int,
        frameH: Int,
        pxPerCm: Double,
        linePxX: Double,
        noiseFraction: Double,
        bodyPx: Double,
        random: Random,
        kneeAngleDeg: Double = 180.0,
        wristsAboveShoulders: Boolean = false,
        overrideWristCm: Double? = null,
        overrideWristY: Double? = null
    ): PoseFrame {
        val landmarks = ArrayList<Landmark>(33)
        val noiseAmpPx = noiseFraction * bodyPx

        fun p(xPx: Double, yPx: Double): Landmark {
            val nx = if (noiseFraction > 0) (random.nextDouble() - 0.5) * 2.0 * noiseAmpPx else 0.0
            val ny = if (noiseFraction > 0) (random.nextDouble() - 0.5) * 2.0 * noiseAmpPx else 0.0
            val cx = ((xPx + nx) / frameW).toFloat().coerceIn(0f, 1f)
            val cy = ((yPx + ny) / frameH).toFloat().coerceIn(0f, 1f)
            return Landmark(cx, cy, 0.99f)
        }

        val hipPxX = linePxX + hipCm * pxPerCm
        val rearHeelPxX = linePxX + rearHeelCm * pxPerCm
        val toePxX = linePxX + toeCm * pxPerCm
        val anklePxX = rearHeelPxX + 4.0 * pxPerCm
        val shoulderY = headY + 0.20 * bodyPx
        val shoulderX = hipPxX

        val kneeFlexFrac = (180.0 - kneeAngleDeg) / 90.0
        val kneePxX = hipPxX + (15.0 * kneeFlexFrac) * pxPerCm
        val kneeY = (hipY + footY) / 2.0

        val wristX = if (overrideWristCm != null) linePxX + overrideWristCm * pxPerCm else hipPxX + (if (wristsAboveShoulders) 20.0 else -10.0) * pxPerCm
        val wristY = overrideWristY ?: if (wristsAboveShoulders) shoulderY - 0.15 * bodyPx else shoulderY + 0.30 * bodyPx

        for (idx in 0..32) {
            val lm = when (idx) {
                PoseLandmarks.NOSE -> p(rearHeelPxX + 4.0 * pxPerCm, footY - 0.90 * bodyPx)
                PoseLandmarks.LEFT_EYE, PoseLandmarks.RIGHT_EYE -> p(rearHeelPxX, footY - 0.936 * bodyPx)
                PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER -> p(shoulderX, shoulderY)
                PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP -> p(hipPxX, hipY)
                PoseLandmarks.LEFT_KNEE, PoseLandmarks.RIGHT_KNEE -> p(kneePxX, kneeY)
                PoseLandmarks.LEFT_ANKLE, PoseLandmarks.RIGHT_ANKLE -> p(anklePxX, footY - 8.0 * pxPerCm)
                PoseLandmarks.LEFT_HEEL -> p(rearHeelPxX, leftFootY)
                PoseLandmarks.RIGHT_HEEL -> p(rearHeelPxX, footY)
                PoseLandmarks.LEFT_FOOT_INDEX -> p(toePxX, leftFootY)
                PoseLandmarks.RIGHT_FOOT_INDEX -> p(toePxX, footY)
                PoseLandmarks.LEFT_WRIST, PoseLandmarks.RIGHT_WRIST -> p(wristX, wristY)
                else -> p(shoulderX, (headY + footY) / 2.0)
            }
            landmarks.add(lm)
        }

        return PoseFrame(t = tNs, landmarks = landmarks)
    }

    fun timeStretch(track: List<PoseFrame>, factor: Double): List<PoseFrame> {
        val baseT = track.firstOrNull()?.t ?: 0L
        return track.map { f ->
            val rel = f.t - baseT
            val newRel = (rel * factor).toLong()
            f.copy(t = baseT + newRel)
        }
    }

    fun dropFrames(track: List<PoseFrame>, range: IntRange): List<PoseFrame> {
        return track.filterIndexed { index, _ -> index !in range }
    }

    fun spliceFrames(track: List<PoseFrame>, range: IntRange): List<PoseFrame> {
        val dt = if (track.size > 1) track[1].t - track[0].t else 33_333_333L
        val kept = track.filterIndexed { index, _ -> index !in range }
        val baseT = track.firstOrNull()?.t ?: 0L
        return kept.mapIndexed { idx, f -> f.copy(t = baseT + idx * dt) }
    }

    fun mirror(
        track: List<PoseFrame>,
        calib: GroundCalibration
    ): Pair<List<PoseFrame>, GroundCalibration> {
        val mirroredFrames = track.map { f ->
            val lms = f.landmarks.map { lm ->
                lm.copy(x = 1.0f - lm.x)
            }
            f.copy(landmarks = lms)
        }

        val mirroredCalib = calib.copy(
            line = NormPoint(1.0 - calib.line.x, calib.line.y),
            marker = calib.marker?.let { NormPoint(1.0 - it.x, it.y) }
        )

        return Pair(mirroredFrames, mirroredCalib)
    }
}
