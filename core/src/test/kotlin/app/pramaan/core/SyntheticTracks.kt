package app.pramaan.core

import app.pramaan.core.model.Landmark
import app.pramaan.core.model.PoseFrame
import app.pramaan.core.model.PoseLandmarks
import java.util.Random

/**
 * Generator of synthetic 33-landmark PoseFrames for testing (PRD 13.2).
 */
object SyntheticTracks {
    const val GRAVITY = 9.81

    /**
     * Scales all timestamps by a factor (PRD 13.2 / 10.1):
     * factor > 1.0 simulates slow-motion video (e.g. 2.0x stretches duration)
     * factor < 1.0 simulates sped-up video (e.g. 0.5x compresses duration)
     */
    fun scaleTimestamps(frames: List<PoseFrame>, factor: Double): List<PoseFrame> {
        if (frames.isEmpty()) return frames
        val t0 = frames.first().t
        return frames.map { frame ->
            val newT = t0 + ((frame.t - t0) * factor).toLong()
            frame.copy(t = newT)
        }
    }

    /**
     * Generates a quiet standing person track for [durationSeconds].
     */
    fun standing(
        durationSeconds: Double = 2.0,
        fps: Int = 30,
        heightCm: Double = 175.0,
        noiseStdDev: Double = 0.0,
        seed: Long = 42L
    ): List<PoseFrame> {
        val totalFrames = (durationSeconds * fps).toInt()
        val dtNs = (1_000_000_000.0 / fps).toLong()
        val rnd = if (noiseStdDev > 0.0) Random(seed) else null

        val basePose = createStandingPose()

        return (0 until totalFrames).map { i ->
            val t = i * dtNs
            val landmarks = if (rnd != null) addNoise(basePose, rnd, noiseStdDev) else basePose
            PoseFrame(t = t, landmarks = landmarks)
        }
    }

    /**
     * Generates a realistic countermovement jump:
     * - Quiet standing (default 1.2 s for calibration and baseline)
     * - Downward dip of the hips with knee bend
     * - Upward extension with arm swing
     * - Flight following real projectile motion (g = 9.81) for [flightTimeSeconds]
     * - Landing and settling
     */
    fun countermovementJump(
        flightTimeSeconds: Double = 0.500,
        fps: Int = 30,
        heightCm: Double = 175.0,
        standingDuration: Double = 1.2,
        dipDuration: Double = 0.4,
        extensionDuration: Double = 0.2,
        settleDuration: Double = 0.5,
        noiseStdDev: Double = 0.0,
        seed: Long = 42L
    ): List<PoseFrame> {
        return multipleJumps(
            flightTimes = listOf(flightTimeSeconds),
            fps = fps,
            heightCm = heightCm,
            standingDuration = standingDuration,
            dipDuration = dipDuration,
            extensionDuration = extensionDuration,
            settleDuration = settleDuration,
            noiseStdDev = noiseStdDev,
            seed = seed
        )
    }

    /**
     * Generates multiple consecutive jumps in a single take.
     */
    fun multipleJumps(
        flightTimes: List<Double>,
        fps: Int = 30,
        heightCm: Double = 175.0,
        standingDuration: Double = 1.2,
        dipDuration: Double = 0.4,
        extensionDuration: Double = 0.2,
        settleDuration: Double = 0.5,
        pauseBetweenJumps: Double = 0.8,
        noiseStdDev: Double = 0.0,
        seed: Long = 42L
    ): List<PoseFrame> {
        val dt = 1.0 / fps
        val rnd = if (noiseStdDev > 0.0) Random(seed) else null

        val eyeToHeelDist = 0.70
        val scaleCmPerUnit = (heightCm * 0.936) / eyeToHeelDist
        val scaleMPerUnit = scaleCmPerUnit / 100.0
        val threshold2Pct = 0.02 * (eyeToHeelDist / 0.936)

        val frames = mutableListOf<PoseFrame>()
        var currentT = 0.0

        // 1. Initial quiet standing
        val standingFrames = (standingDuration * fps).toInt()
        for (i in 0 until standingFrames) {
            val tNs = Math.round(currentT * 1e9)
            val pose = createStandingPose()
            val lms = if (rnd != null) addNoise(pose, rnd, noiseStdDev) else pose
            frames.add(PoseFrame(t = tNs, landmarks = lms))
            currentT += dt
        }

        // 2. Perform each jump
        for ((jumpIndex, flightTime) in flightTimes.withIndex()) {
            val v0 = 0.5 * GRAVITY * flightTime
            val v0Norm = v0 / scaleMPerUnit
            val gNorm = GRAVITY / scaleMPerUnit

            // (a) Dip phase: hip lowers, knees bend
            val dipFrames = (dipDuration * fps).toInt()
            val maxDipNorm = (15.0 / scaleCmPerUnit)
            for (i in 0 until dipFrames) {
                val progress = Math.sin((i.toDouble() / dipFrames) * Math.PI)
                val currentDip = (maxDipNorm * progress).toFloat()
                val kneeAngle = 180.0 - (70.0 * progress)
                val pose = createDippingPose(currentDip, kneeAngle)
                val tNs = Math.round(currentT * 1e9)
                val lms = if (rnd != null) addNoise(pose, rnd, noiseStdDev) else pose
                frames.add(PoseFrame(t = tNs, landmarks = lms))
                currentT += dt
            }

            // (b) Upward extension to take-off
            val extFrames = (extensionDuration * fps).toInt()
            for (i in 0 until extFrames) {
                val progress = i.toDouble() / extFrames
                val currentDip = (maxDipNorm * (1.0 - progress)).toFloat()
                val toeLift = if (i == extFrames - 1) (threshold2Pct * 0.5) else 0.0
                val pose = createExtendingPose(currentDip, armSwingProgress = progress, toeLift = toeLift)
                val tNs = Math.round(currentT * 1e9)
                val lms = if (rnd != null) addNoise(pose, rnd, noiseStdDev) else pose
                frames.add(PoseFrame(t = tNs, landmarks = lms))
                currentT += dt
            }

            // (c) Flight phase
            val flightFrames = Math.max(2, Math.round(flightTime * fps).toInt())
            for (i in 0 until flightFrames) {
                val tau = (i + 0.5) * dt
                val flightY = (v0Norm * tau - 0.5 * gNorm * tau * tau).toFloat()
                val toeArc = (threshold2Pct * (1.2 + 0.5 * Math.sin(Math.PI * tau / flightTime))).toFloat()
                val pose = createFlightPose(flightY, toeArc = toeArc, armSwing = true)
                val tNs = Math.round(currentT * 1e9)
                val lms = if (rnd != null) addNoise(pose, rnd, noiseStdDev) else pose
                frames.add(PoseFrame(t = tNs, landmarks = lms))
                currentT += dt
            }

            // (d) Landing and settling
            val settleFrames = (settleDuration * fps).toInt()
            for (i in 0 until settleFrames) {
                val progress = Math.max(0.0, 1.0 - (i.toDouble() / settleFrames))
                val cushionDip = (0.05 * progress).toFloat()
                val toeLift = if (i == 0) (threshold2Pct * 0.5) else 0.0
                val pose = createStandingPose(cushionDip, toeLift = toeLift)
                val tNs = Math.round(currentT * 1e9)
                val lms = if (rnd != null) addNoise(pose, rnd, noiseStdDev) else pose
                frames.add(PoseFrame(t = tNs, landmarks = lms))
                currentT += dt
            }

            // (e) Quiet standing pause between jumps
            if (jumpIndex < flightTimes.size - 1) {
                val pauseFrames = (pauseBetweenJumps * fps).toInt()
                for (i in 0 until pauseFrames) {
                    val pose = createStandingPose()
                    val tNs = Math.round(currentT * 1e9)
                    val lms = if (rnd != null) addNoise(pose, rnd, noiseStdDev) else pose
                    frames.add(PoseFrame(t = tNs, landmarks = lms))
                    currentT += dt
                }
            }
        }

        return frames
    }

    /**
     * Generates sit-up cycles at given [peakAngles] (degrees, horizontal is 0).
     */
    fun sitUps(
        peakAngles: List<Double>,
        fps: Int = 30,
        cycleDuration: Double = 1.5,
        pauseDuration: Double = 0.5,
        noiseStdDev: Double = 0.0,
        seed: Long = 42L
    ): List<PoseFrame> {
        val dt = 1.0 / fps
        val rnd = if (noiseStdDev > 0.0) Random(seed) else null

        val frames = mutableListOf<PoseFrame>()
        var currentT = 0.0

        for (peakAngle in peakAngles) {
            val cycleFrames = (cycleDuration * fps).toInt()
            for (i in 0 until cycleFrames) {
                val progress = Math.sin((i.toDouble() / cycleFrames) * Math.PI)
                val angle = 15.0 + (peakAngle - 15.0) * progress
                val pose = createSitUpPose(angle)
                val tNs = Math.round(currentT * 1e9)
                val lms = if (rnd != null) addNoise(pose, rnd, noiseStdDev) else pose
                frames.add(PoseFrame(t = tNs, landmarks = lms))
                currentT += dt
            }

            val pauseFrames = (pauseDuration * fps).toInt()
            for (i in 0 until pauseFrames) {
                val pose = createSitUpPose(15.0)
                val tNs = Math.round(currentT * 1e9)
                val lms = if (rnd != null) addNoise(pose, rnd, noiseStdDev) else pose
                frames.add(PoseFrame(t = tNs, landmarks = lms))
                currentT += dt
            }
        }

        return frames
    }

    private fun toImageCoordinates(x: Double, yUp: Double): Pair<Float, Float> {
        return (x.toFloat()) to ((1.0 - yUp).toFloat())
    }

    private fun createStandingPose(cushionDip: Float = 0.0f, toeLift: Double = 0.0): List<Landmark> {
        val groundY = 0.12 - cushionDip
        return build33Landmarks(
            toeY = groundY + toeLift,
            heelY = groundY + toeLift,
            ankleY = 0.18 - cushionDip,
            kneeY = 0.44 - cushionDip,
            hipY = 0.62 - cushionDip,
            shoulderY = 0.78 - cushionDip,
            elbowY = 0.68 - cushionDip,
            wristY = 0.58 - cushionDip,
            eyeY = 0.82 - cushionDip,
            noseY = 0.80 - cushionDip
        )
    }

    private fun createDippingPose(dipNorm: Float, kneeAngleDeg: Double): List<Landmark> {
        val groundY = 0.12
        return build33Landmarks(
            toeY = groundY,
            heelY = groundY,
            ankleY = 0.18,
            kneeY = 0.44 - (dipNorm * 0.5),
            hipY = 0.62 - dipNorm,
            shoulderY = 0.78 - dipNorm,
            elbowY = 0.68 - dipNorm,
            wristY = 0.58 - dipNorm,
            eyeY = 0.82 - dipNorm,
            noseY = 0.80 - dipNorm,
            kneeX = 0.54
        )
    }

    private fun createExtendingPose(dipNorm: Float, armSwingProgress: Double, toeLift: Double = 0.0): List<Landmark> {
        val groundY = 0.12
        val wristHeight = 0.58 - dipNorm + (0.30 * armSwingProgress)
        return build33Landmarks(
            toeY = groundY + toeLift,
            heelY = groundY + toeLift,
            ankleY = 0.18,
            kneeY = 0.44 - (dipNorm * 0.5),
            hipY = 0.62 - dipNorm,
            shoulderY = 0.78 - dipNorm,
            elbowY = 0.68 - dipNorm + (0.15 * armSwingProgress),
            wristY = wristHeight,
            eyeY = 0.82 - dipNorm,
            noseY = 0.80 - dipNorm
        )
    }

    private fun createFlightPose(flightY: Float, toeArc: Float, armSwing: Boolean): List<Landmark> {
        val groundY = 0.12
        val toeFlightY = groundY + toeArc + flightY
        val wristY = if (armSwing) (0.78 + flightY + 0.05) else (0.58 + flightY)

        return build33Landmarks(
            toeY = toeFlightY,
            heelY = toeFlightY,
            ankleY = 0.18 + flightY,
            kneeY = 0.44 + flightY,
            hipY = 0.62 + flightY,
            shoulderY = 0.78 + flightY,
            elbowY = 0.68 + flightY,
            wristY = wristY,
            eyeY = 0.82 + flightY,
            noseY = 0.80 + flightY
        )
    }

    private fun createSitUpPose(trunkAngleDeg: Double): List<Landmark> {
        val hipX = 0.40
        val hipY = 0.20
        val torsoLength = 0.25
        val rad = Math.toRadians(trunkAngleDeg)
        val shoulderX = hipX - torsoLength * Math.cos(rad)
        val shoulderY = hipY + torsoLength * Math.sin(rad)
        val headLength = 0.08
        val eyeX = shoulderX - headLength * Math.cos(rad)
        val eyeY = shoulderY + headLength * Math.sin(rad)

        return build33Landmarks(
            toeY = 0.15,
            heelY = 0.15,
            ankleY = 0.17,
            kneeY = 0.35,
            hipY = hipY,
            shoulderY = shoulderY,
            elbowY = shoulderY - 0.05,
            wristY = shoulderY,
            eyeY = eyeY,
            noseY = eyeY - 0.02,
            hipX = hipX,
            shoulderX = shoulderX,
            eyeX = eyeX
        )
    }

    private fun build33Landmarks(
        toeY: Double,
        heelY: Double,
        ankleY: Double,
        kneeY: Double,
        hipY: Double,
        shoulderY: Double,
        elbowY: Double,
        wristY: Double,
        eyeY: Double,
        noseY: Double,
        hipX: Double = 0.49,
        shoulderX: Double = 0.49,
        kneeX: Double = 0.50,
        eyeX: Double = 0.51
    ): List<Landmark> {
        val lms = ArrayList<Landmark>(33)
        for (i in 0..32) {
            val (x, yUp) = when (i) {
                PoseLandmarks.NOSE -> 0.53 to noseY
                PoseLandmarks.LEFT_EYE, PoseLandmarks.RIGHT_EYE -> eyeX to eyeY
                PoseLandmarks.LEFT_EYE_INNER, PoseLandmarks.LEFT_EYE_OUTER,
                PoseLandmarks.RIGHT_EYE_INNER, PoseLandmarks.RIGHT_EYE_OUTER -> eyeX to eyeY
                PoseLandmarks.LEFT_EAR, PoseLandmarks.RIGHT_EAR -> (eyeX - 0.03) to eyeY
                PoseLandmarks.MOUTH_LEFT, PoseLandmarks.MOUTH_RIGHT -> (eyeX + 0.01) to (noseY - 0.03)
                PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER -> shoulderX to shoulderY
                PoseLandmarks.LEFT_ELBOW, PoseLandmarks.RIGHT_ELBOW -> (shoulderX - 0.01) to elbowY
                PoseLandmarks.LEFT_WRIST, PoseLandmarks.RIGHT_WRIST -> (shoulderX - 0.01) to wristY
                PoseLandmarks.LEFT_PINKY, PoseLandmarks.RIGHT_PINKY,
                PoseLandmarks.LEFT_INDEX, PoseLandmarks.RIGHT_INDEX,
                PoseLandmarks.LEFT_THUMB, PoseLandmarks.RIGHT_THUMB -> (shoulderX - 0.01) to (wristY - 0.03)
                PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP -> hipX to hipY
                PoseLandmarks.LEFT_KNEE, PoseLandmarks.RIGHT_KNEE -> kneeX to kneeY
                PoseLandmarks.LEFT_ANKLE, PoseLandmarks.RIGHT_ANKLE -> 0.48 to ankleY
                PoseLandmarks.LEFT_HEEL, PoseLandmarks.RIGHT_HEEL -> 0.46 to heelY
                PoseLandmarks.LEFT_FOOT_INDEX, PoseLandmarks.RIGHT_FOOT_INDEX -> 0.54 to toeY
                else -> 0.5 to 0.5
            }
            val (imgX, imgY) = toImageCoordinates(x, yUp)
            lms.add(Landmark(x = imgX, y = imgY, visibility = 0.99f))
        }
        return lms
    }

    private fun addNoise(pose: List<Landmark>, rnd: Random, stdDev: Double): List<Landmark> {
        return pose.map { lm ->
            val nX = (rnd.nextGaussian() * stdDev).toFloat()
            val nY = (rnd.nextGaussian() * stdDev).toFloat()
            lm.copy(x = lm.x + nX, y = lm.y + nY)
        }
    }
}
