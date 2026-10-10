package app.pramaan.core.scoring

import app.pramaan.core.manifest.RejectedRep
import app.pramaan.core.model.PoseFrame
import app.pramaan.core.model.PoseLandmarks

sealed class SitUpEvent {
    data class TimerStarted(val timestampNs: Long) : SitUpEvent()
    data class AngleUpdated(val timestampNs: Long, val angleDeg: Double) : SitUpEvent()
    data class RepCounted(val count: Int, val durationSeconds: Double, val peakAngleDeg: Double) : SitUpEvent()
    data class RepRejected(val timestampNs: Long, val reason: String, val peakAngleDeg: Double) : SitUpEvent()
}

/**
 * Sit-ups (30 s) counter and biomechanical state machine implementing PRD 10.3:
 * - Side view, athlete lying with knees bent.
 * - Trunk angle = angle of the hip->shoulder line above horizontal.
 * - DOWN when angle < 25°, UP when angle > 70°.
 * - Rep counts on DOWN -> UP -> DOWN, at least 0.5 s long.
 * - Leaving DOWN without reaching UP is rejected as NOT_UP.
 * - Timer starts on first upward movement; reps finishing after maxDurationSeconds don't count.
 */
class SitUpAnalyzer(
    private val maxDurationSeconds: Double = 30.0,
    var onEvent: ((SitUpEvent) -> Unit)? = null
) {
    companion object {
        const val DOWN_THRESHOLD_DEG = 25.0
        const val UP_THRESHOLD_DEG = 70.0
        const val MIN_REP_DURATION_SEC = 0.5
        const val HYSTERESIS_LEAVE_DOWN_DEG = 28.0
        const val HYSTERESIS_LEAVE_UP_DEG = 65.0

        fun analyze(frames: List<PoseFrame>, maxDurationSeconds: Double = 30.0): SitUpResult {
            val analyzer = SitUpAnalyzer(maxDurationSeconds)
            for (frame in frames) {
                analyzer.onFrame(frame)
            }
            return analyzer.finish()
        }

        fun calculateTrunkAngle(lm: List<app.pramaan.core.model.Landmark>): Double {
            if (lm.size < 33) return 0.0
            val midHipY = (lm[PoseLandmarks.LEFT_HIP].y + lm[PoseLandmarks.RIGHT_HIP].y) / 2.0
            val midHipX = (lm[PoseLandmarks.LEFT_HIP].x + lm[PoseLandmarks.RIGHT_HIP].x) / 2.0
            val midShoulderY = (lm[PoseLandmarks.LEFT_SHOULDER].y + lm[PoseLandmarks.RIGHT_SHOULDER].y) / 2.0
            val midShoulderX = (lm[PoseLandmarks.LEFT_SHOULDER].x + lm[PoseLandmarks.RIGHT_SHOULDER].x) / 2.0

            // Normalised image coordinates have Y growing downwards.
            // Shoulder higher in world space means midShoulderY < midHipY, so dy > 0.
            val dy = (midHipY - midShoulderY).toDouble()
            val dx = Math.abs((midShoulderX - midHipX).toDouble())
            return Math.toDegrees(Math.atan2(dy, dx))
        }
    }

    private enum class State {
        DOWN,
        RISING,
        UP,
        DESCENDING
    }

    private var state = State.DOWN
    private var firstUpwardMovementNs: Long? = null
    private var lastTimestampNs: Long = 0L

    // Current rep state
    private var currentRepStartNs: Long = 0L
    private var peakAngleThisRep: Double = 0.0
    private var currentRepCount = 0

    // Rolling angle smoothing buffer
    private val angleBuffer = ArrayDeque<Double>()

    private val completedReps = mutableListOf<CompletedRep>()
    private val rejectedReps = mutableListOf<RejectedRep>()

    fun onFrame(frame: PoseFrame) {
        if (frame.landmarks.size < 33) return
        val t = frame.t
        lastTimestampNs = t

        val rawAngle = calculateTrunkAngle(frame.landmarks)
        angleBuffer.addLast(rawAngle)
        if (angleBuffer.size > 5) angleBuffer.removeFirst()
        val smoothedAngle = angleBuffer.average()

        onEvent?.invoke(SitUpEvent.AngleUpdated(t, smoothedAngle))

        when (state) {
            State.DOWN -> {
                if (smoothedAngle >= HYSTERESIS_LEAVE_DOWN_DEG) {
                    if (firstUpwardMovementNs == null) {
                        firstUpwardMovementNs = t
                        onEvent?.invoke(SitUpEvent.TimerStarted(t))
                    }
                    state = State.RISING
                    currentRepStartNs = t
                    peakAngleThisRep = smoothedAngle
                }
            }

            State.RISING -> {
                if (smoothedAngle > peakAngleThisRep) {
                    peakAngleThisRep = smoothedAngle
                }

                if (smoothedAngle >= UP_THRESHOLD_DEG) {
                    state = State.UP
                } else if (smoothedAngle < DOWN_THRESHOLD_DEG) {
                    // Left DOWN, never reached UP, returned to DOWN -> NOT_UP
                    val tSec = getElapsedSec(t)
                    val rejected = RejectedRep(t = round2(tSec), reason = "NOT_UP")
                    rejectedReps.add(rejected)
                    onEvent?.invoke(SitUpEvent.RepRejected(t, "NOT_UP", peakAngleThisRep))
                    state = State.DOWN
                }
            }

            State.UP -> {
                if (smoothedAngle > peakAngleThisRep) {
                    peakAngleThisRep = smoothedAngle
                }

                if (smoothedAngle < HYSTERESIS_LEAVE_UP_DEG) {
                    state = State.DESCENDING
                }
            }

            State.DESCENDING -> {
                if (smoothedAngle < DOWN_THRESHOLD_DEG) {
                    val repDurationSec = (t - currentRepStartNs) / 1_000_000_000.0
                    val elapsedSinceStart = getElapsedSec(t)

                    if (repDurationSec < MIN_REP_DURATION_SEC) {
                        val rejected = RejectedRep(t = round2(elapsedSinceStart), reason = "TOO_FAST")
                        rejectedReps.add(rejected)
                        onEvent?.invoke(SitUpEvent.RepRejected(t, "TOO_FAST", peakAngleThisRep))
                    } else if (maxDurationSeconds > 0 && elapsedSinceStart > maxDurationSeconds) {
                        val rejected = RejectedRep(t = round2(elapsedSinceStart), reason = "TIME_EXPIRED")
                        rejectedReps.add(rejected)
                        onEvent?.invoke(SitUpEvent.RepRejected(t, "TIME_EXPIRED", peakAngleThisRep))
                    } else {
                        currentRepCount++
                        val rep = CompletedRep(
                            repNumber = currentRepCount,
                            startTimestampNs = currentRepStartNs,
                            endTimestampNs = t,
                            durationSeconds = round2(repDurationSec),
                            peakAngleDeg = round2(peakAngleThisRep)
                        )
                        completedReps.add(rep)
                        onEvent?.invoke(SitUpEvent.RepCounted(currentRepCount, repDurationSec, peakAngleThisRep))
                    }
                    state = State.DOWN
                } else if (smoothedAngle >= UP_THRESHOLD_DEG) {
                    // Went back up without touching down
                    val elapsedSinceStart = getElapsedSec(t)
                    val rejected = RejectedRep(t = round2(elapsedSinceStart), reason = "NOT_DOWN")
                    rejectedReps.add(rejected)
                    onEvent?.invoke(SitUpEvent.RepRejected(t, "NOT_DOWN", peakAngleThisRep))
                    currentRepStartNs = t
                    peakAngleThisRep = smoothedAngle
                    state = State.UP
                }
            }
        }
    }

    private fun getElapsedSec(tNs: Long): Double {
        val start = firstUpwardMovementNs ?: tNs
        return (tNs - start) / 1_000_000_000.0
    }

    private fun round2(v: Double): Double {
        return Math.round(v * 100.0) / 100.0
    }

    fun finish(): SitUpResult {
        // If left hanging in RISING, reject as NOT_UP
        if (state == State.RISING) {
            val tSec = getElapsedSec(lastTimestampNs)
            rejectedReps.add(RejectedRep(t = round2(tSec), reason = "NOT_UP"))
        }

        val totalDuration = if (firstUpwardMovementNs != null) {
            getElapsedSec(lastTimestampNs)
        } else 0.0

        return SitUpResult(
            totalReps = currentRepCount,
            completedReps = completedReps.toList(),
            rejectedReps = rejectedReps.toList(),
            durationSeconds = round2(totalDuration)
        )
    }
}
