package app.pramaan.core.integrity

import app.pramaan.core.model.CheckResult
import app.pramaan.core.scoring.broadjump.BroadJumpParams
import app.pramaan.core.scoring.broadjump.BroadJumpResult
import app.pramaan.core.scoring.broadjump.CalibMode
import java.util.Locale

object BroadJumpChecks {

    /**
     * C2: Gravity Check for Standing Broad Jump.
     * Evaluates freefall acceleration g = -2a on hip height during airborne phases of counted attempts.
     */
    fun checkGravity(
        result: BroadJumpResult,
        params: BroadJumpParams = BroadJumpParams()
    ): CheckResult {
        val attempts = if (result.attempts.any { it.counted }) result.attempts.filter { it.counted } else result.attempts
        if (attempts.isEmpty()) {
            return CheckResult(
                id = "C2",
                pass = false,
                value = "No jump",
                limit = "${params.gravityBandMin}–${params.gravityBandMax} m/s²",
                reason = "No jump attempts detected"
            )
        }

        val bestAttempt = attempts.firstOrNull { it.index == result.bestIndex }
            ?: attempts.maxByOrNull { it.distanceCm }
            ?: attempts.first()
        val g = bestAttempt.gEst
        val flightTimeS = bestAttempt.flightTimeS

        val pass = if (g != null) {
            g in params.gravityBandMin..params.gravityBandMax
        } else {
            flightTimeS in 0.10..0.95
        }

        val effectiveG = g ?: 9.81
        val formattedG = String.format(Locale.US, "%.1f", effectiveG)
        val reason = if (!pass) {
            if (g != null) {
                if (g < params.gravityBandMin) "Motion too slow for real gravity ($formattedG m/s²)"
                else "Motion too fast for real gravity ($formattedG m/s²)"
            } else {
                "Motion not consistent with natural ballistic flight (${flightTimeS}s flight)"
            }
        } else null

        return CheckResult(
            id = "C2",
            pass = pass,
            value = "$formattedG m/s²",
            limit = "${params.gravityBandMin}–${params.gravityBandMax} m/s²",
            reason = reason
        )
    }

    /**
     * C3: Horizontal consistency for Standing Broad Jump.
     * Verifies forward hip trajectory linearity, maximum forward speed, and physical reach gap.
     */
    fun checkHorizontalConsistency(
        result: BroadJumpResult,
        athleteHeightCm: Double,
        params: BroadJumpParams = BroadJumpParams()
    ): CheckResult {
        val attempts = if (result.attempts.any { it.counted }) result.attempts.filter { it.counted } else result.attempts
        if (attempts.isEmpty()) {
            return CheckResult(
                id = "C3",
                pass = false,
                value = "No attempts",
                limit = "Linearity & reach bounds",
                reason = "No counted attempts"
            )
        }

        val bestAttempt = attempts.firstOrNull { it.index == result.bestIndex }
            ?: attempts.maxByOrNull { it.distanceCm }
            ?: attempts.first()
        val hipRms = bestAttempt.hipLinearityRmsCm
        val maxRms = params.hipLinearityTolCm(bestAttempt.hipTravelCm)
        val speedMps = bestAttempt.takeoffSpeedMps ?: 0.0
        val reachGap = bestAttempt.distanceCm - bestAttempt.hipTravelCm

        val minReach = -0.35 * athleteHeightCm
        val maxReach = 0.70 * athleteHeightCm

        val speedPass = speedMps <= params.maxHipSpeedMps
        val travelPass = bestAttempt.distanceCm > 0.0 || bestAttempt.hipTravelCm > 15.0
        val rmsPass = hipRms <= maxRms
        val reachPass = reachGap in minReach..maxReach || bestAttempt.distanceCm > 20.0

        val allPass = speedPass && travelPass && rmsPass && reachPass

        val reason = when {
            !speedPass -> "Excessive forward speed (${String.format(Locale.US, "%.1f", speedMps)} m/s > ${params.maxHipSpeedMps} m/s)"
            !travelPass -> "No forward travel detected"
            !rmsPass -> "Hip trajectory non-linear (RMS ${String.format(Locale.US, "%.1f", hipRms)} cm > limit ${String.format(Locale.US, "%.1f", maxRms)} cm)"
            !reachPass -> "Reach gap out of bounds (${String.format(Locale.US, "%.1f", reachGap)} cm not in [${String.format(Locale.US, "%.1f", minReach)}, ${String.format(Locale.US, "%.1f", maxReach)}])"
            else -> null
        }

        return CheckResult(
            id = "C3",
            pass = allPass,
            value = "Travel ${String.format(Locale.US, "%.1f", bestAttempt.distanceCm)} cm, Speed ${String.format(Locale.US, "%.1f", speedMps)} m/s",
            limit = "Travel > 0, Speed ≤ ${params.maxHipSpeedMps} m/s",
            reason = reason
        )
    }

    /**
     * C5: Calibration Agreement check.
     * Evaluates tape marker vs height scale agreement, tilt limits, and calibration mode validity.
     */
    fun checkCalibrationAgreement(
        result: BroadJumpResult,
        params: BroadJumpParams = BroadJumpParams()
    ): CheckResult {
        val calib = result.calibration
        if (calib.mode == CalibMode.HEIGHT_ONLY) {
            if (params.requireTapeMarker) {
                return CheckResult(
                    id = "C5",
                    pass = false,
                    value = "Height only",
                    limit = "MARKER mode required",
                    reason = "CALIB_APPROX"
                )
            }
            val scaleValid = calib.scaleCmPerPx in 0.05..2.5
            val tiltPass = calib.axisTiltDeg <= params.axisBlockDeg
            val pass = scaleValid && tiltPass
            val reason = when {
                !scaleValid -> "Unrealistic height scale (${String.format(Locale.US, "%.3f", calib.scaleCmPerPx)} cm/px)"
                !tiltPass -> "Jump axis tilted (${String.format(Locale.US, "%.1f", calib.axisTiltDeg)}° > ${params.axisBlockDeg}°)"
                else -> null
            }
            return CheckResult(
                id = "C5",
                pass = pass,
                value = "Athlete Height Scaled (${String.format(Locale.US, "%.2f", calib.scaleCmPerPx)} cm/px)",
                limit = "Scale in 0.05–2.5 cm/px, Tilt ≤ ${params.axisBlockDeg}°",
                reason = reason
            )
        }

        val tiltPass = calib.axisTiltDeg <= params.axisBlockDeg
        val diffPass = calib.scaleDiffPct <= params.calibAgreementPct

        val allPass = tiltPass && diffPass
        val reason = when {
            !tiltPass -> "Jump axis tilted (${String.format(Locale.US, "%.1f", calib.axisTiltDeg)}° > ${params.axisBlockDeg}°)"
            !diffPass -> "Marker scale diverges from height scale (${String.format(Locale.US, "%.1f", calib.scaleDiffPct)}% > ${params.calibAgreementPct}%)"
            else -> null
        }

        return CheckResult(
            id = "C5",
            pass = allPass,
            value = "Scale diff ${String.format(Locale.US, "%.1f", calib.scaleDiffPct)}%, Tilt ${String.format(Locale.US, "%.1f", calib.axisTiltDeg)}°",
            limit = "Diff ≤ ${params.calibAgreementPct}%, Tilt ≤ ${params.axisBlockDeg}°",
            reason = reason
        )
    }
}
