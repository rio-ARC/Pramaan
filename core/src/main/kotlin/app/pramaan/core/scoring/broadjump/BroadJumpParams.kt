package app.pramaan.core.scoring.broadjump

import kotlinx.serialization.Serializable

@Serializable
data class BroadJumpParams(
    val eyeHeightRatio: Double = 0.936,
    val minLandmarkVisibility: Float = 0.5f,
    val standStillMinS: Double = 0.4,
    val stillSpeedBody: Double = 0.15,
    val liftThrBody: Double = 0.03,
    val groundTolBody: Double = 0.03,
    val minFlightS: Double = 0.12,
    val maxFlightS: Double = 1.2,
    val minHipTravelCm: Double = 25.0,
    val lineTolCm: Double = 3.0,
    val stanceAheadErrorCm: Double = 45.0,
    val stepTolCm: Double = 5.0,
    val oneFootMs: Double = 60.0,
    val oneFootFrames: Double = 2.5,
    val landingWindowS: Double = 0.6,
    val fallWindowS: Double = 0.9,
    val fallBackMinCm: Double = 2.0,
    val wristContactBody: Double = 0.08,
    val hipContactBody: Double = 0.15,
    val maxAttempts: Int = 3,
    val heelOffsetCm: Double = 0.0,
    val analyzerVersion: String = "bj-1.0.0",
    val medianFilterFrames: Int = 3,
    val gravityBandMin: Double = 7.0,
    val gravityBandMax: Double = 12.5,
    val gravityTrimS: Double = 0.02,
    val gravityMinSamples: Int = 3,
    val maxHipSpeedMps: Double = 5.0,
    val reachGapMinFracH: Double = -0.10,
    val reachGapMaxFracH: Double = 0.65,
    val calibAgreementPct: Double = 10.0,
    val axisWarnDeg: Double = 10.0,
    val axisBlockDeg: Double = 20.0,
    val requireTapeMarker: Boolean = true
) {
    fun hipLinearityTolCm(hipTravelCm: Double): Double {
        return maxOf(5.0, 0.06 * hipTravelCm)
    }
}
