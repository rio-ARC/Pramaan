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
        val countedAttempts = result.attempts.filter { it.counted }
        if (countedAttempts.isEmpty()) {
            return CheckResult(
                id = "C2",
                pass = false,
                value = "No jump",
                limit = "${params.gravityBandMin}–${params.gravityBandMax} m/s²",
                reason = "No counted attempts available for gravity check"
            )
        }

        val bestAttempt = countedAttempts.firstOrNull { it.index == result.bestIndex } ?: countedAttempts.first()
        val g = bestAttempt.gEst

        if (g == null) {
            return CheckResult(
                id = "C2",
                pass = false,
                value = "Too few frames",
                limit = "${params.gravityBandMin}–${params.gravityBandMax} m/s²",
                reason = "TOO_FEW_FRAMES"
            )
        }

        val pass = g in params.gravityBandMin..params.gravityBandMax
        val reason = if (!pass) {
            if (g < params.gravityBandMin) "Motion too slow for real gravity (${String.format(Locale.US, "%.1f", g)} m/s²)"
            else "Motion too fast for real gravity (${String.format(Locale.US, "%.1f", g)} m/s²)"
        } else null

        return CheckResult(
            id = "C2",
            pass = pass,
            value = "${String.format(Locale.US, "%.1f", g)} m/s²",
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
        val countedAttempts = result.attempts.filter { it.counted }
        if (countedAttempts.isEmpty()) {
            return CheckResult(
                id = "C3",
                pass = false,
                value = "No attempts",
                limit = "Linearity & reach bounds",
                reason = "No counted attempts"
            )
        }

        val bestAttempt = countedAttempts.firstOrNull { it.index == result.bestIndex } ?: countedAttempts.first()
        val hipRms = bestAttempt.hipLinearityRmsCm
        val maxRms = params.hipLinearityTolCm(bestAttempt.hipTravelCm)
        val speedMps = bestAttempt.takeoffSpeedMps ?: 0.0
        val reachGap = bestAttempt.distanceCm - bestAttempt.hipTravelCm

        val minReach = params.reachGapMinFracH * athleteHeightCm
        val maxReach = params.reachGapMaxFracH * athleteHeightCm

        val rmsPass = hipRms <= maxRms
        val speedPass = speedMps <= params.maxHipSpeedMps
        val reachPass = reachGap in minReach..maxReach

        val allPass = rmsPass && speedPass && reachPass

        val reason = when {
            !rmsPass -> "Hip trajectory non-linear (RMS ${String.format(Locale.US, "%.1f", hipRms)} cm > limit ${String.format(Locale.US, "%.1f", maxRms)} cm)"
            !speedPass -> "Excessive forward speed (${String.format(Locale.US, "%.1f", speedMps)} m/s > ${params.maxHipSpeedMps} m/s)"
            !reachPass -> "Reach gap out of bounds (${String.format(Locale.US, "%.1f", reachGap)} cm not in [${String.format(Locale.US, "%.1f", minReach)}, ${String.format(Locale.US, "%.1f", maxReach)}])"
            else -> null
        }

        return CheckResult(
            id = "C3",
            pass = allPass,
            value = "RMS ${String.format(Locale.US, "%.1f", hipRms)} cm, Speed ${String.format(Locale.US, "%.1f", speedMps)} m/s",
            limit = "RMS ≤ ${String.format(Locale.US, "%.1f", maxRms)} cm, Speed ≤ ${params.maxHipSpeedMps} m/s",
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
            return CheckResult(
                id = "C5",
                pass = false,
                value = "Height only",
                limit = "MARKER mode required",
                reason = "CALIB_APPROX"
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
