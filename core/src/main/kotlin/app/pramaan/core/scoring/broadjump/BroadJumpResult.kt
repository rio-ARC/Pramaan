package app.pramaan.core.scoring.broadjump

import kotlinx.serialization.Serializable

@Serializable
enum class Foul {
    TOES_OVER_LINE,
    STEP_OR_HOP,
    ONE_FOOT_TAKEOFF
}

@Serializable
enum class BjNote {
    FALL_BACK,
    HEEL_OCCLUDED,
    STANCE_NOT_STILL,
    GROUND_DRIFT,
    EXTRA_ATTEMPTS_IGNORED,
    NO_VALID_ATTEMPT
}

@Serializable
enum class ContactType {
    HEEL,
    WRIST,
    HIP
}

@Serializable
data class ContactPoint(
    val type: ContactType,
    val forwardCm: Double,
    val tNs: Long,
    val at: NormPoint
)

@Serializable
data class CalibSummary(
    val mode: CalibMode,
    val scaleCmPerPx: Double,
    val sHeightCmPerPx: Double,
    val scaleDiffPct: Double,
    val axisTiltDeg: Double
)

@Serializable
data class BjAttempt(
    val index: Int,
    val valid: Boolean,
    val counted: Boolean,
    val distanceCm: Double,
    val distanceHeightOnlyCm: Double? = null,
    val fouls: List<Foul> = emptyList(),
    val notes: List<BjNote> = emptyList(),
    val takeoffTNs: Long,
    val touchdownTNs: Long,
    val flightTimeS: Double,
    val gEst: Double? = null,
    val hipTravelCm: Double,
    val hipLinearityRmsCm: Double,
    val takeoffAngleDeg: Double? = null,
    val takeoffSpeedMps: Double? = null,
    val kneeDipDeg: Double? = null,
    val armSwingIndex: Double? = null,
    val landingReach: Double? = null,
    val contact: ContactPoint
)

@Serializable
data class BroadJumpResult(
    val unit: String = "cm",
    val best: Double? = null,
    val bestIndex: Int? = null,
    val attempts: List<BjAttempt> = emptyList(),
    val notes: List<BjNote> = emptyList(),
    val calibration: CalibSummary,
    val feedbackKey: String,
    val analyzerVersion: String
)
