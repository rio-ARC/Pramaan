package app.pramaan.core.scoring.broadjump

import app.pramaan.core.model.PoseFrame
import app.pramaan.core.model.PoseLandmarks
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToLong

data class LiveBjState(
    val standingStill: Boolean,
    val bodyInFrame: Boolean,
    val attemptsDetected: Int,
    val lastApproxCm: Double? = null
)

class BroadJumpLiveTracker(
    private val athleteHeightCm: Double,
    private val params: BroadJumpParams = BroadJumpParams()
) {
    private var attemptsDetected = 0
    private var lastApproxCm: Double? = null

    private var standingStillCount = 0
    private var isAirborne = false
    private var takeoffTimeNs: Long = 0L

    private var stanceToeXPx: Double? = null
    private var stanceGroundYPx: Double? = null
    private var estimatedBodyPx: Double = 300.0

    private val recentHipXPx = ArrayDeque<Double>()
    private val recentHipYPx = ArrayDeque<Double>()
    private val recentTimesNs = ArrayDeque<Long>()

    fun reset() {
        attemptsDetected = 0
        lastApproxCm = null
        standingStillCount = 0
        isAirborne = false
        takeoffTimeNs = 0L
        stanceToeXPx = null
        stanceGroundYPx = null
        recentHipXPx.clear()
        recentHipYPx.clear()
        recentTimesNs.clear()
    }

    fun onFrame(frame: PoseFrame, frameW: Int = 640, frameH: Int = 480): LiveBjState {
        val landmarks = frame.landmarks
        if (landmarks.size != 33) {
            return LiveBjState(
                standingStill = false,
                bodyInFrame = false,
                attemptsDetected = attemptsDetected,
                lastApproxCm = lastApproxCm
            )
        }

        val required = listOf(
            PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER,
            PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP,
            PoseLandmarks.LEFT_KNEE, PoseLandmarks.RIGHT_KNEE,
            PoseLandmarks.LEFT_ANKLE, PoseLandmarks.RIGHT_ANKLE,
            PoseLandmarks.LEFT_HEEL, PoseLandmarks.RIGHT_HEEL,
            PoseLandmarks.LEFT_FOOT_INDEX, PoseLandmarks.RIGHT_FOOT_INDEX
        )
        val bodyInFrame = required.all { landmarks[it].visibility >= params.minLandmarkVisibility }

        val hipL = landmarks[PoseLandmarks.LEFT_HIP]
        val hipR = landmarks[PoseLandmarks.RIGHT_HIP]
        val hipX = (hipL.x + hipR.x) / 2.0 * frameW
        val hipY = (hipL.y + hipR.y) / 2.0 * frameH

        recentHipXPx.addLast(hipX)
        recentHipYPx.addLast(hipY)
        recentTimesNs.addLast(frame.t)
        if (recentHipXPx.size > 10) {
            recentHipXPx.removeFirst()
            recentHipYPx.removeFirst()
            recentTimesNs.removeFirst()
        }

        // Estimate bodyPx
        val eyeL = landmarks[PoseLandmarks.LEFT_EYE]
        val heelL = landmarks[PoseLandmarks.LEFT_HEEL]
        if (eyeL.visibility >= params.minLandmarkVisibility && heelL.visibility >= params.minLandmarkVisibility) {
            val dist = hypot(eyeL.x.toDouble() * frameW - heelL.x.toDouble() * frameW, eyeL.y.toDouble() * frameH - heelL.y.toDouble() * frameH)
            estimatedBodyPx = (dist / params.eyeHeightRatio).coerceAtLeast(50.0)
        }

        // Speed calculation
        var speedBodyPerS = 1.0
        if (recentHipXPx.size >= 5) {
            val dt = (recentTimesNs.last() - recentTimesNs.first()) / 1e9
            if (dt > 1e-4) {
                val dx = recentHipXPx.last() - recentHipXPx.first()
                val dy = recentHipYPx.last() - recentHipYPx.first()
                val dist = hypot(dx, dy)
                speedBodyPerS = (dist / estimatedBodyPx) / dt
            }
        }

        val isStill = speedBodyPerS < params.stillSpeedBody
        if (isStill && bodyInFrame) {
            standingStillCount++
            if (standingStillCount >= 15) { // ~0.5 s at 30 fps
                val toes = listOf(landmarks[PoseLandmarks.LEFT_FOOT_INDEX], landmarks[PoseLandmarks.RIGHT_FOOT_INDEX])
                    .filter { it.visibility >= params.minLandmarkVisibility }
                if (toes.isNotEmpty()) {
                    stanceToeXPx = toes.map { it.x.toDouble() * frameW }.average()
                }
                val feet = listOf(landmarks[PoseLandmarks.LEFT_HEEL], landmarks[PoseLandmarks.RIGHT_HEEL])
                    .filter { it.visibility >= params.minLandmarkVisibility }
                if (feet.isNotEmpty()) {
                    stanceGroundYPx = feet.map { it.y.toDouble() * frameH }.average()
                }
            }
        } else {
            standingStillCount = 0
        }

        val standingStill = standingStillCount >= 20

        // In-flight detection
        val sGroundY = stanceGroundYPx
        if (sGroundY != null) {
            val feet = listOf(
                landmarks[PoseLandmarks.LEFT_HEEL],
                landmarks[PoseLandmarks.RIGHT_HEEL],
                landmarks[PoseLandmarks.LEFT_FOOT_INDEX],
                landmarks[PoseLandmarks.RIGHT_FOOT_INDEX]
            ).filter { it.visibility >= params.minLandmarkVisibility }

            val lowestFootY = if (feet.isNotEmpty()) feet.maxOf { it.y.toDouble() * frameH } else sGroundY
            val lift = sGroundY - lowestFootY
            val airborneNow = lift > (params.liftThrBody * estimatedBodyPx)

            if (!isAirborne && airborneNow) {
                isAirborne = true
                takeoffTimeNs = frame.t
            } else if (isAirborne && !airborneNow) {
                // Touchdown!
                isAirborne = false
                val flightDurationS = (frame.t - takeoffTimeNs) / 1e9
                if (flightDurationS >= params.minFlightS) {
                    attemptsDetected++
                    // Approximate distance:
                    val sHeight = athleteHeightCm / estimatedBodyPx
                    val heelXs = listOf(landmarks[PoseLandmarks.LEFT_HEEL], landmarks[PoseLandmarks.RIGHT_HEEL])
                        .filter { it.visibility >= params.minLandmarkVisibility }
                    val landingHeelX = if (heelXs.isNotEmpty()) heelXs.map { it.x.toDouble() * frameW }.average() else hipX
                    val toeX = stanceToeXPx ?: hipX
                    val dxPx = abs(landingHeelX - toeX)
                    val approxCm = dxPx * sHeight
                    lastApproxCm = (approxCm * 10.0).roundToLong() / 10.0
                }
            }
        }

        return LiveBjState(
            standingStill = standingStill,
            bodyInFrame = bodyInFrame,
            attemptsDetected = attemptsDetected,
            lastApproxCm = lastApproxCm
        )
    }
}
