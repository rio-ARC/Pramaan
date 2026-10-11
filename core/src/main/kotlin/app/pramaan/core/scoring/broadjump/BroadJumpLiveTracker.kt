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
    private var lastTouchdownTNs: Long = 0L

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
        lastTouchdownTNs = 0L
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

        // Robust side-on profile detection: athlete is visible if at least one side is detected
        val hasUpper = landmarks[PoseLandmarks.LEFT_SHOULDER].visibility >= 0.35f ||
                landmarks[PoseLandmarks.RIGHT_SHOULDER].visibility >= 0.35f ||
                landmarks[PoseLandmarks.NOSE].visibility >= 0.35f
        val hasHip = landmarks[PoseLandmarks.LEFT_HIP].visibility >= 0.35f ||
                landmarks[PoseLandmarks.RIGHT_HIP].visibility >= 0.35f
        val hasKnee = landmarks[PoseLandmarks.LEFT_KNEE].visibility >= 0.35f ||
                landmarks[PoseLandmarks.RIGHT_KNEE].visibility >= 0.35f
        val hasFoot = landmarks[PoseLandmarks.LEFT_ANKLE].visibility >= 0.35f ||
                landmarks[PoseLandmarks.RIGHT_ANKLE].visibility >= 0.35f ||
                landmarks[PoseLandmarks.LEFT_HEEL].visibility >= 0.35f ||
                landmarks[PoseLandmarks.RIGHT_HEEL].visibility >= 0.35f ||
                landmarks[PoseLandmarks.LEFT_FOOT_INDEX].visibility >= 0.35f ||
                landmarks[PoseLandmarks.RIGHT_FOOT_INDEX].visibility >= 0.35f

        val bodyInFrame = hasUpper && hasHip && hasKnee && hasFoot

        val visibleHips = listOf(landmarks[PoseLandmarks.LEFT_HIP], landmarks[PoseLandmarks.RIGHT_HIP])
            .filter { it.visibility >= 0.3f }
        val hipX = if (visibleHips.isNotEmpty()) visibleHips.map { it.x.toDouble() * frameW }.average() else frameW * 0.5
        val hipY = if (visibleHips.isNotEmpty()) visibleHips.map { it.y.toDouble() * frameH }.average() else frameH * 0.5

        recentHipXPx.addLast(hipX)
        recentHipYPx.addLast(hipY)
        recentTimesNs.addLast(frame.t)
        if (recentHipXPx.size > 10) {
            recentHipXPx.removeFirst()
            recentHipYPx.removeFirst()
            recentTimesNs.removeFirst()
        }

        // Estimate vertical body pixel height
        val topY = listOf(landmarks[PoseLandmarks.NOSE], landmarks[PoseLandmarks.LEFT_EYE], landmarks[PoseLandmarks.RIGHT_EYE],
            landmarks[PoseLandmarks.LEFT_SHOULDER], landmarks[PoseLandmarks.RIGHT_SHOULDER])
            .filter { it.visibility >= 0.35f }
            .map { it.y.toDouble() * frameH }
            .minOrNull()
        val botY = listOf(landmarks[PoseLandmarks.LEFT_HEEL], landmarks[PoseLandmarks.RIGHT_HEEL],
            landmarks[PoseLandmarks.LEFT_ANKLE], landmarks[PoseLandmarks.RIGHT_ANKLE],
            landmarks[PoseLandmarks.LEFT_FOOT_INDEX], landmarks[PoseLandmarks.RIGHT_FOOT_INDEX])
            .filter { it.visibility >= 0.35f }
            .map { it.y.toDouble() * frameH }
            .maxOrNull()

        if (topY != null && botY != null && botY > topY) {
            val rawHeight = botY - topY
            estimatedBodyPx = (rawHeight / 0.936).coerceIn(80.0, frameH * 0.95)
        }

        // Velocity calculation
        var horizSpeedBodyPerS = 0.0
        var totalSpeedBodyPerS = 1.0
        if (recentHipXPx.size >= 4) {
            val dt = (recentTimesNs.last() - recentTimesNs.first()) / 1e9
            if (dt > 1e-4) {
                val dx = abs(recentHipXPx.last() - recentHipXPx.first())
                val dy = abs(recentHipYPx.last() - recentHipYPx.first())
                horizSpeedBodyPerS = (dx / estimatedBodyPx) / dt
                totalSpeedBodyPerS = (hypot(dx, dy) / estimatedBodyPx) / dt
            }
        }

        val isStill = totalSpeedBodyPerS < 0.25
        if (isStill && bodyInFrame) {
            standingStillCount++
            if (standingStillCount >= 4) { // ~0.12-0.15 s
                val toes = listOf(landmarks[PoseLandmarks.LEFT_FOOT_INDEX], landmarks[PoseLandmarks.RIGHT_FOOT_INDEX])
                    .filter { it.visibility >= 0.25f }
                if (toes.isNotEmpty()) {
                    stanceToeXPx = toes.map { it.x.toDouble() * frameW }.average()
                } else {
                    stanceToeXPx = hipX
                }
                val feet = listOf(landmarks[PoseLandmarks.LEFT_HEEL], landmarks[PoseLandmarks.RIGHT_HEEL])
                    .filter { it.visibility >= 0.25f }
                if (feet.isNotEmpty()) {
                    stanceGroundYPx = feet.map { it.y.toDouble() * frameH }.average()
                } else if (botY != null) {
                    stanceGroundYPx = botY
                }
            }
        } else {
            standingStillCount = 0
        }

        val standingStill = standingStillCount >= 4

        // In-flight detection and touchdown tracking
        val sGroundY = stanceGroundYPx ?: botY ?: (frameH * 0.85)
        val feet = listOf(
            landmarks[PoseLandmarks.LEFT_HEEL],
            landmarks[PoseLandmarks.RIGHT_HEEL],
            landmarks[PoseLandmarks.LEFT_FOOT_INDEX],
            landmarks[PoseLandmarks.RIGHT_FOOT_INDEX]
        ).filter { it.visibility >= 0.25f }

        val lowestFootY = if (feet.isNotEmpty()) feet.maxOf { it.y.toDouble() * frameH } else sGroundY
        val lift = sGroundY - lowestFootY

        // Airborne during broad jump requires BOTH foot lift and forward translation
        val airborneNow = (lift > (0.015 * estimatedBodyPx)) && (horizSpeedBodyPerS > 0.25)
        val cooldownOver = (frame.t - lastTouchdownTNs) > 600_000_000L

        if (!isAirborne && airborneNow && stanceToeXPx != null && cooldownOver) {
            isAirborne = true
            takeoffTimeNs = frame.t
        } else if (isAirborne && (!airborneNow || horizSpeedBodyPerS < 0.20)) {
            // Touchdown / Landing contact
            isAirborne = false
            lastTouchdownTNs = frame.t
            val flightDurationS = (frame.t - takeoffTimeNs) / 1e9
            if (flightDurationS >= 0.15) {
                val sHeight = athleteHeightCm / estimatedBodyPx
                val heelXs = listOf(landmarks[PoseLandmarks.LEFT_HEEL], landmarks[PoseLandmarks.RIGHT_HEEL])
                    .filter { it.visibility >= 0.25f }
                val landingHeelX = if (heelXs.isNotEmpty()) heelXs.map { it.x.toDouble() * frameW }.average() else hipX
                val toeX = stanceToeXPx ?: (hipX - 15.0)
                val dxPx = abs(landingHeelX - toeX)
                val approxCm = dxPx * sHeight

                if (approxCm >= 20.0) {
                    attemptsDetected++
                    lastApproxCm = (approxCm * 10.0).roundToLong() / 10.0
                    stanceToeXPx = null
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
