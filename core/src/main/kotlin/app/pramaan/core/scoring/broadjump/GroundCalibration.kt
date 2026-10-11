package app.pramaan.core.scoring.broadjump

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

@Serializable
enum class CalibMode {
    MARKER,
    HEIGHT_ONLY
}

@Serializable
data class NormPoint(
    val x: Double,
    val y: Double
)

@Serializable
data class GroundCalibration(
    val mode: CalibMode,
    val frameW: Int,
    val frameH: Int,
    val calibFrameTNs: Long,
    val line: NormPoint,
    val marker: NormPoint? = null,
    val markerCm: Double? = null
)

@Serializable
data class AthleteInfo(
    val heightCm: Double,
    val ageYears: Int = 16,
    val sex: String = "F"
)

class GroundFrame(
    val linePx: Pair<Double, Double>,
    val axisE: Pair<Double, Double>,
    val scaleCmPerPx: Double,
    val sHeightCmPerPx: Double,
    val groundYpx: Double,
    val axisTiltDeg: Double,
    val scaleDiffPct: Double
) {
    fun forwardCm(pxX: Double, pxY: Double): Double {
        val dx = pxX - linePx.first
        val dy = pxY - linePx.second
        return (dx * axisE.first + dy * axisE.second) * scaleCmPerPx
    }

    fun upCm(pxY: Double): Double {
        return (groundYpx - pxY) * scaleCmPerPx
    }
}

object GroundCalibrationHelper {

    fun heightScale(athleteHeightCm: Double, bodyPx: Double, eyeHeightRatio: Double = 0.936): Double {
        if (bodyPx <= 1e-4) return 0.5
        return athleteHeightCm / bodyPx
    }

    fun buildGroundFrame(
        calib: GroundCalibration,
        groundYpx: Double,
        athleteHeightCm: Double,
        bodyPx: Double,
        stanceXPx: Double? = null,
        eyeHeightRatio: Double = 0.936
    ): GroundFrame {
        val sHeight = heightScale(athleteHeightCm, bodyPx, eyeHeightRatio)
        val lPx = Pair(calib.line.x * calib.frameW, calib.line.y * calib.frameH)

        return when (calib.mode) {
            CalibMode.MARKER -> {
                val m = calib.marker ?: NormPoint(calib.line.x + 0.2, calib.line.y)
                val mPx = Pair(m.x * calib.frameW, m.y * calib.frameH)
                val dx = mPx.first - lPx.first
                val dy = mPx.second - lPx.second
                val distPx = hypot(dx, dy).coerceAtLeast(1e-4)
                val axisE = Pair(dx / distPx, dy / distPx)
                val markerCm = calib.markerCm ?: 200.0
                val s = markerCm / distPx

                val angleRad = abs(atan2(dy, dx))
                val tiltRad = minOf(angleRad, abs(PI - angleRad))
                val axisTiltDeg = (tiltRad * 180.0 / PI)
                val scaleDiffPct = if (s > 1e-4) 100.0 * abs(s - sHeight) / s else 0.0

                GroundFrame(
                    linePx = lPx,
                    axisE = axisE,
                    scaleCmPerPx = s,
                    sHeightCmPerPx = sHeight,
                    groundYpx = groundYpx,
                    axisTiltDeg = axisTiltDeg,
                    scaleDiffPct = scaleDiffPct
                )
            }
            CalibMode.HEIGHT_ONLY -> {
                val forwardDir = if (stanceXPx != null && stanceXPx > lPx.first) -1.0 else 1.0
                val axisE = Pair(forwardDir, 0.0)

                GroundFrame(
                    linePx = lPx,
                    axisE = axisE,
                    scaleCmPerPx = sHeight,
                    sHeightCmPerPx = sHeight,
                    groundYpx = groundYpx,
                    axisTiltDeg = 0.0,
                    scaleDiffPct = 0.0
                )
            }
        }
    }
}
