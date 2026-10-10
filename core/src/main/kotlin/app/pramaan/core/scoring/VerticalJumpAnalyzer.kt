package app.pramaan.core.scoring

import app.pramaan.core.integrity.ParabolaFit
import app.pramaan.core.model.PoseFrame
import app.pramaan.core.model.PoseLandmarks

sealed class JumpEvent {
    data class Calibrated(val bodyScale: BodyScale) : JumpEvent()
    data class DipStarted(val timestampNs: Long) : JumpEvent()
    data class TakeOff(val timestampNs: Long, val attemptNumber: Int) : JumpEvent()
    data class Landing(val attempt: JumpAttemptResult) : JumpEvent()
}

/**
 * Vertical Jump Analyzer implementing PRD 10.2 steps 1-9.
 * Operates in both incremental (real-time stream) and batch mode.
 */
class VerticalJumpAnalyzer(
    private val heightCm: Double,
    private val maxAttempts: Int = 3,
    var onEvent: ((JumpEvent) -> Unit)? = null
) {
    companion object {
        const val GRAVITY = 9.81 // m/s^2
        const val CALIBRATION_DURATION_NS = 1_000_000_000L // 1.0 s
        const val MIN_CALIBRATION_FRAMES = 25
        const val BASELINE_WINDOW_DURATION_NS = 500_000_000L // 0.5 s
        const val MIN_FLIGHT_TIME_SECONDS = 0.12
        const val MAX_FLIGHT_TIME_SECONDS = 1.00

        fun analyze(frames: List<PoseFrame>, heightCm: Double): JumpResult {
            val analyzer = VerticalJumpAnalyzer(heightCm)
            for (frame in frames) {
                analyzer.onFrame(frame)
            }
            return analyzer.finish()
        }
    }

    private enum class State {
        CALIBRATING,
        STANDING,
        DIPPING,
        IN_FLIGHT,
        FINISHED
    }

    private var state = State.CALIBRATING
    private var firstTimestampNs: Long? = null

    // Calibration
    private val calibrationFrames = mutableListOf<PoseFrame>()
    private var bodyScale: BodyScale? = null

    // Lookahead smoothing buffer (window size 5, lookahead 2 frames)
    private class FrameData(
        val frame: PoseFrame,
        val rawHipY: Float,
        val rawLeftToeY: Float,
        val rawRightToeY: Float
    )
    private val lookaheadBuffer = ArrayDeque<FrameData>()

    // Standing baseline
    private class TimedToeSample(val t: Long, val toeY: Float, val hipY: Float)
    private val baselineBuffer = ArrayDeque<TimedToeSample>()
    private var toeBaseline = 0.0f
    private var standingHipBaseline = 0.0f
    private var baselineValid = false

    // Dip tracking
    private var dipDetected = false
    private var minKneeAngleDuringDip = 180.0

    // Flight tracking
    private var currentAttemptIndex = 1
    private var takeOffTimeNs = 0L
    private var takeOffHipHeight = 0.0f
    private var peakHipHeight = 0.0f
    private var wristsSwungAboveShoulders = false
    private val flightHipSamples = mutableListOf<Pair<Double, Double>>() // (tSec, yMetres)

    // Previous frame state for interpolation
    private var prevT = 0L
    private var prevBothToesY = 0.0f
    private var prevHipY = 0.0f

    // Results
    private val attempts = mutableListOf<JumpAttemptResult>()

    fun onFrame(frame: PoseFrame) {
        if (state == State.FINISHED || frame.landmarks.size < 33) return

        val t = frame.t
        if (firstTimestampNs == null) firstTimestampNs = t

        val lm = frame.landmarks
        val rawHipY = 1.0f - ((lm[PoseLandmarks.LEFT_HIP].y + lm[PoseLandmarks.RIGHT_HIP].y) / 2.0f)
        val rawLeftToeY = 1.0f - lm[PoseLandmarks.LEFT_TOE].y
        val rawRightToeY = 1.0f - lm[PoseLandmarks.RIGHT_TOE].y

        lookaheadBuffer.addLast(FrameData(frame, rawHipY, rawLeftToeY, rawRightToeY))

        // When lookahead buffer has 5 frames, apply Savitzky-Golay filter to center frame (index 2)
        if (lookaheadBuffer.size == 5) {
            processCenteredFrame(2)
            lookaheadBuffer.removeFirst()
        }
    }

    private fun processCenteredFrame(centerIndex: Int) {
        val center = lookaheadBuffer[centerIndex]
        val t = center.frame.t
        val lm = center.frame.landmarks

        val hipY: Float
        val leftToeY: Float
        val rightToeY: Float

        if (lookaheadBuffer.size == 5) {
            // Savitzky-Golay 5-point quadratic weights: [-3, 12, 17, 12, -3] / 35
            val f0 = lookaheadBuffer[0]
            val f1 = lookaheadBuffer[1]
            val f2 = lookaheadBuffer[2]
            val f3 = lookaheadBuffer[3]
            val f4 = lookaheadBuffer[4]

            hipY = (-3f * f0.rawHipY + 12f * f1.rawHipY + 17f * f2.rawHipY + 12f * f3.rawHipY - 3f * f4.rawHipY) / 35f
            leftToeY = (-3f * f0.rawLeftToeY + 12f * f1.rawLeftToeY + 17f * f2.rawLeftToeY + 12f * f3.rawLeftToeY - 3f * f4.rawLeftToeY) / 35f
            rightToeY = (-3f * f0.rawRightToeY + 12f * f1.rawRightToeY + 17f * f2.rawRightToeY + 12f * f3.rawRightToeY - 3f * f4.rawRightToeY) / 35f
        } else {
            // Fallback moving average for buffer edge flushing
            var sumH = 0f; var sumL = 0f; var sumR = 0f
            for (fd in lookaheadBuffer) {
                sumH += fd.rawHipY
                sumL += fd.rawLeftToeY
                sumR += fd.rawRightToeY
            }
            val count = lookaheadBuffer.size.toFloat()
            hipY = sumH / count
            leftToeY = sumL / count
            rightToeY = sumR / count
        }

        val bothToesY = minOf(leftToeY, rightToeY)

        when (state) {
            State.CALIBRATING -> {
                calibrationFrames.add(center.frame)
                val elapsed = t - (firstTimestampNs ?: t)
                if (calibrationFrames.size >= MIN_CALIBRATION_FRAMES || elapsed >= CALIBRATION_DURATION_NS) {
                    completeCalibration()
                }
            }

            State.STANDING -> {
                updateBaseline(t, bothToesY, hipY)
                val threshold = (bodyScale?.threshold2Percent ?: 0.015).toFloat()

                // Check for downward dip of the hip
                val dipThreshold = threshold * 1.5f
                if (baselineValid && (standingHipBaseline - hipY) > dipThreshold) {
                    state = State.DIPPING
                    dipDetected = true
                    minKneeAngleDuringDip = computeCurrentKneeAngle(lm)
                    onEvent?.invoke(JumpEvent.DipStarted(t))
                }
            }

            State.DIPPING -> {
                val kneeAngle = computeCurrentKneeAngle(lm)
                if (kneeAngle < minKneeAngleDuringDip) {
                    minKneeAngleDuringDip = kneeAngle
                }

                val threshold = (bodyScale?.threshold2Percent ?: 0.015).toFloat()
                val takeOffThresh = toeBaseline + threshold

                // Take-off: both toes rise more than 2% of body height above baseline
                if (bothToesY > takeOffThresh && prevBothToesY <= takeOffThresh) {
                    val alpha = if (bothToesY != prevBothToesY) {
                        ((takeOffThresh - prevBothToesY) / (bothToesY - prevBothToesY)).toDouble().coerceIn(0.0, 1.0)
                    } else 0.0

                    takeOffTimeNs = (prevT + alpha * (t - prevT)).toLong()
                    takeOffHipHeight = (prevHipY + alpha * (hipY - prevHipY)).toFloat()
                    peakHipHeight = takeOffHipHeight
                    wristsSwungAboveShoulders = false
                    flightHipSamples.clear()

                    val scaleM = (bodyScale?.scaleCmPerUnit ?: 100.0) / 100.0
                    flightHipSamples.add(0.0 to (takeOffHipHeight * scaleM))

                    state = State.IN_FLIGHT
                    onEvent?.invoke(JumpEvent.TakeOff(takeOffTimeNs, currentAttemptIndex))
                }
            }

            State.IN_FLIGHT -> {
                val scaleM = (bodyScale?.scaleCmPerUnit ?: 100.0) / 100.0
                val tSec = (t - takeOffTimeNs) / 1_000_000_000.0
                flightHipSamples.add(tSec to (hipY * scaleM))

                if (hipY > peakHipHeight) {
                    peakHipHeight = hipY
                }

                val midWristY = 1.0f - ((lm[PoseLandmarks.LEFT_WRIST].y + lm[PoseLandmarks.RIGHT_WRIST].y) / 2.0f)
                val midShoulderY = 1.0f - ((lm[PoseLandmarks.LEFT_SHOULDER].y + lm[PoseLandmarks.RIGHT_SHOULDER].y) / 2.0f)
                if (midWristY >= midShoulderY) {
                    wristsSwungAboveShoulders = true
                }

                val threshold = (bodyScale?.threshold2Percent ?: 0.015).toFloat()
                val landingThresh = toeBaseline + threshold

                // Landing: after peak, toes return to or below threshold
                if (bothToesY <= landingThresh && prevBothToesY > landingThresh) {
                    val beta = if (bothToesY != prevBothToesY) {
                        ((landingThresh - prevBothToesY) / (bothToesY - prevBothToesY)).toDouble().coerceIn(0.0, 1.0)
                    } else 0.0

                    val landingTimeNs = (prevT + beta * (t - prevT)).toLong()
                    val landingHipHeight = (prevHipY + beta * (hipY - prevHipY)).toFloat()
                    val landingTSec = (landingTimeNs - takeOffTimeNs) / 1_000_000_000.0
                    flightHipSamples.add(landingTSec to (landingHipHeight * scaleM))

                    finalizeAttempt(landingTimeNs)
                }
            }

            State.FINISHED -> {}
        }

        prevT = t
        prevBothToesY = bothToesY
        prevHipY = hipY
    }

    private fun completeCalibration() {
        val scale = BodyScale.calculate(calibrationFrames, heightCm)
            ?: BodyScale(
                scaleCmPerUnit = heightCm / 0.70,
                eyeToHeelDistance = 0.70 * 0.936,
                normalizedBodyHeight = 0.70,
                threshold2Percent = 0.014
            )
        bodyScale = scale
        state = State.STANDING
        onEvent?.invoke(JumpEvent.Calibrated(scale))
    }

    private fun updateBaseline(t: Long, bothToesY: Float, hipY: Float) {
        baselineBuffer.addLast(TimedToeSample(t, bothToesY, hipY))
        while (baselineBuffer.isNotEmpty() && (t - baselineBuffer.first().t) > BASELINE_WINDOW_DURATION_NS) {
            baselineBuffer.removeFirst()
        }

        if (baselineBuffer.size >= 5) {
            val toeValues = baselineBuffer.map { it.toeY }.sorted()
            val hipValues = baselineBuffer.map { it.hipY }.sorted()
            val mid = toeValues.size / 2
            toeBaseline = toeValues[mid]
            standingHipBaseline = hipValues[mid]
            baselineValid = true
        }
    }

    private fun finalizeAttempt(landingTimeNs: Long) {
        val flightTimeSec = (landingTimeNs - takeOffTimeNs) / 1_000_000_000.0
        val scaleCm = bodyScale?.scaleCmPerUnit ?: (heightCm / 0.70)

        val flightCm = (GRAVITY * flightTimeSec * flightTimeSec / 8.0) * 100.0
        val hipRiseCm = (peakHipHeight - takeOffHipHeight) * scaleCm

        val tArr = flightHipSamples.map { it.first }.toDoubleArray()
        val yArr = flightHipSamples.map { it.second }.toDoubleArray()
        val parabola = ParabolaFit.fit(tArr, yArr)
        val gravityEst = parabola?.gravity ?: GRAVITY

        val isValidFlightTime = flightTimeSec in MIN_FLIGHT_TIME_SECONDS..MAX_FLIGHT_TIME_SECONDS
        val isValid = isValidFlightTime && dipDetected
        val invalidReason = when {
            !isValidFlightTime -> "Flight time ${String.format(java.util.Locale.US, "%.3f", flightTimeSec)} s outside valid range [0.12, 1.00]"
            !dipDetected -> "No downward dip detected before take-off"
            else -> null
        }

        val feedback = Feedback.evaluate(minKneeAngleDuringDip, wristsSwungAboveShoulders)

        val result = JumpAttemptResult(
            attemptNumber = currentAttemptIndex,
            flightTimeSeconds = flightTimeSec,
            flightCm = flightCm,
            hipRiseCm = hipRiseCm,
            gravity = gravityEst,
            valid = isValid,
            takeOffTimestampNs = takeOffTimeNs,
            landingTimestampNs = landingTimeNs,
            feedback = feedback,
            invalidReason = invalidReason
        )

        attempts.add(result)
        onEvent?.invoke(JumpEvent.Landing(result))

        currentAttemptIndex++
        if (currentAttemptIndex > maxAttempts) {
            state = State.FINISHED
        } else {
            state = State.STANDING
            dipDetected = false
            minKneeAngleDuringDip = 180.0
            baselineBuffer.clear()
            baselineValid = false
        }
    }

    private fun computeCurrentKneeAngle(lm: List<app.pramaan.core.model.Landmark>): Double {
        val leftAngle = Feedback.calculateAngleDegrees(
            lm[PoseLandmarks.LEFT_HIP],
            lm[PoseLandmarks.LEFT_KNEE],
            lm[PoseLandmarks.LEFT_ANKLE]
        )
        val rightAngle = Feedback.calculateAngleDegrees(
            lm[PoseLandmarks.RIGHT_HIP],
            lm[PoseLandmarks.RIGHT_KNEE],
            lm[PoseLandmarks.RIGHT_ANKLE]
        )
        return (leftAngle + rightAngle) / 2.0
    }

    fun finish(): JumpResult {
        while (lookaheadBuffer.isNotEmpty()) {
            processCenteredFrame(0)
            lookaheadBuffer.removeFirst()
        }

        if (state == State.CALIBRATING && calibrationFrames.isNotEmpty()) {
            completeCalibration()
        }

        val validAttempts = attempts.filter { it.valid }
        val bestAttempt = validAttempts.maxByOrNull { it.flightCm }
            ?: attempts.filter { it.flightTimeSeconds >= MIN_FLIGHT_TIME_SECONDS }.maxByOrNull { it.flightCm }

        return JumpResult(
            attempts = attempts.toList(),
            bestFlightCm = bestAttempt?.flightCm ?: 0.0,
            bestHipRiseCm = bestAttempt?.hipRiseCm ?: 0.0,
            bestGravity = bestAttempt?.gravity ?: 0.0,
            feedback = bestAttempt?.feedback ?: if (attempts.isNotEmpty()) attempts.last().feedback else "No attempts",
            hasValidJump = validAttempts.isNotEmpty()
        )
    }
}
