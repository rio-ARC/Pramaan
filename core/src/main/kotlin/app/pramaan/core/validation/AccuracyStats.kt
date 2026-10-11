package app.pramaan.core.validation

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A comparison record between Pramaan AI scoring and ground-truth tape measure.
 */
@Serializable
data class ValidationAttemptComparison(
    val sessionId: String,
    val attemptIndex: Int,
    val aiDistanceCm: Double,
    val tapeCm: Double,
    val aiDistanceHeightOnlyCm: Double? = null,
    val truthFoul: String = "NONE",
    val aiFouls: List<String> = emptyList(),
    val dataSet: String = "REPORT" // "TUNE" or "REPORT"
) {
    val d: Double
        get() = aiDistanceCm - tapeCm

    val absD: Double
        get() = abs(d)

    val dHeightOnly: Double?
        get() = aiDistanceHeightOnlyCm?.let { it - tapeCm }
}

@Serializable
data class WilsonInterval(
    val lowerPct: Double,
    val upperPct: Double
)

@Serializable
data class FoulDetectionMetrics(
    val foulCode: String,
    val tp: Int,
    val fp: Int,
    val fn: Int,
    val precision: Double,
    val recall: Double
)

@Serializable
data class OverallFoulMetrics(
    val totalStaged: Int,
    val detectedStaged: Int,
    val stagedFoulRecallPct: Double,
    val totalClean: Int,
    val falseFoulClean: Int,
    val cleanJumpFalseFoulPct: Double,
    val byCode: List<FoulDetectionMetrics>
)

@Serializable
data class ModeAccuracyStats(
    val n: Int,
    val meanBiasCm: Double,
    val maeCm: Double,
    val sdCm: Double,
    val loaLowerCm: Double,
    val loaUpperCm: Double,
    val pctWithin2Cm: Double,
    val wilson2Cm: WilsonInterval,
    val pctWithin3Cm: Double,
    val wilson3Cm: WilsonInterval,
    val maxErrorCm: Double,
    val worstAttempt: ValidationAttemptComparison?
)

@Serializable
data class ComprehensiveAccuracyStats(
    val reportStats: ModeAccuracyStats,
    val heightOnlyStats: ModeAccuracyStats?,
    val foulStats: OverallFoulMetrics,
    val worstAttempts: List<ValidationAttemptComparison>,
    val tuneStats: ModeAccuracyStats?
)

object AccuracyStatsCalculator {

    private const val Z_95 = 1.95996398454

    fun computeWilsonInterval(successCount: Int, total: Int): WilsonInterval {
        if (total <= 0) return WilsonInterval(0.0, 0.0)
        val p = successCount.toDouble() / total
        val z2 = Z_95 * Z_95
        val n = total.toDouble()
        val denom = 1.0 + z2 / n
        val center = (p + z2 / (2.0 * n)) / denom
        val margin = (Z_95 * sqrt((p * (1.0 - p)) / n + z2 / (4.0 * n * n))) / denom
        val lower = max(0.0, (center - margin) * 100.0)
        val upper = min(100.0, (center + margin) * 100.0)
        return WilsonInterval(
            lowerPct = round2(lower),
            upperPct = round2(upper)
        )
    }

    fun computeModeStats(
        records: List<ValidationAttemptComparison>,
        useHeightOnly: Boolean = false
    ): ModeAccuracyStats {
        val validRecords = if (useHeightOnly) {
            records.filter { it.aiDistanceHeightOnlyCm != null }
        } else {
            records
        }

        val n = validRecords.size
        if (n == 0) {
            return ModeAccuracyStats(
                n = 0,
                meanBiasCm = 0.0,
                maeCm = 0.0,
                sdCm = 0.0,
                loaLowerCm = 0.0,
                loaUpperCm = 0.0,
                pctWithin2Cm = 0.0,
                wilson2Cm = WilsonInterval(0.0, 0.0),
                pctWithin3Cm = 0.0,
                wilson3Cm = WilsonInterval(0.0, 0.0),
                maxErrorCm = 0.0,
                worstAttempt = null
            )
        }

        val diffs = validRecords.map { r ->
            if (useHeightOnly) (r.aiDistanceHeightOnlyCm!! - r.tapeCm) else r.d
        }

        val sumDiff = diffs.sum()
        val meanBias = sumDiff / n

        val absDiffs = diffs.map { abs(it) }
        val mae = absDiffs.sum() / n

        val sd = if (n > 1) {
            val sumSquaredDev = diffs.sumOf { (it - meanBias) * (it - meanBias) }
            sqrt(sumSquaredDev / (n - 1))
        } else {
            0.0
        }

        val loaLower = meanBias - 1.96 * sd
        val loaUpper = meanBias + 1.96 * sd

        val countWithin2 = absDiffs.count { it <= 2.0 }
        val pctWithin2 = (countWithin2.toDouble() / n) * 100.0
        val wilson2 = computeWilsonInterval(countWithin2, n)

        val countWithin3 = absDiffs.count { it <= 3.0 }
        val pctWithin3 = (countWithin3.toDouble() / n) * 100.0
        val wilson3 = computeWilsonInterval(countWithin3, n)

        var maxErr = 0.0
        var worst: ValidationAttemptComparison? = null
        for (i in validRecords.indices) {
            val err = absDiffs[i]
            if (err >= maxErr) {
                maxErr = err
                worst = validRecords[i]
            }
        }

        return ModeAccuracyStats(
            n = n,
            meanBiasCm = round2(meanBias),
            maeCm = round2(mae),
            sdCm = round2(sd),
            loaLowerCm = round2(loaLower),
            loaUpperCm = round2(loaUpper),
            pctWithin2Cm = round1(pctWithin2),
            wilson2Cm = wilson2,
            pctWithin3Cm = round1(pctWithin3),
            wilson3Cm = wilson3,
            maxErrorCm = round2(maxErr),
            worstAttempt = worst
        )
    }

    fun computeFoulMetrics(records: List<ValidationAttemptComparison>): OverallFoulMetrics {
        val targetCodes = listOf("TOES_OVER_LINE", "STEP_OR_HOP", "ONE_FOOT_TAKEOFF", "FALL_BACK")
        val byCode = targetCodes.map { code ->
            var tp = 0
            var fp = 0
            var fn = 0
            for (r in records) {
                val hasTruth = r.truthFoul.equals(code, ignoreCase = true)
                val hasAi = r.aiFouls.any { it.equals(code, ignoreCase = true) }
                if (hasTruth && hasAi) tp++
                else if (!hasTruth && hasAi) fp++
                else if (hasTruth && !hasAi) fn++
            }
            val precision = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 1.0
            val recall = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 1.0
            FoulDetectionMetrics(
                foulCode = code,
                tp = tp,
                fp = fp,
                fn = fn,
                precision = round2(precision),
                recall = round2(recall)
            )
        }

        val stagedAttempts = records.filter { it.truthFoul.isNotBlank() && !it.truthFoul.equals("NONE", ignoreCase = true) }
        val stagedDetected = stagedAttempts.count { it.aiFouls.isNotEmpty() }
        val stagedRecall = if (stagedAttempts.isNotEmpty()) {
            (stagedDetected.toDouble() / stagedAttempts.size) * 100.0
        } else 100.0

        val cleanAttempts = records.filter { it.truthFoul.isBlank() || it.truthFoul.equals("NONE", ignoreCase = true) }
        val cleanFalseFouls = cleanAttempts.count { it.aiFouls.isNotEmpty() }
        val cleanFalseRate = if (cleanAttempts.isNotEmpty()) {
            (cleanFalseFouls.toDouble() / cleanAttempts.size) * 100.0
        } else 0.0

        return OverallFoulMetrics(
            totalStaged = stagedAttempts.size,
            detectedStaged = stagedDetected,
            stagedFoulRecallPct = round1(stagedRecall),
            totalClean = cleanAttempts.size,
            falseFoulClean = cleanFalseFouls,
            cleanJumpFalseFoulPct = round1(cleanFalseRate),
            byCode = byCode
        )
    }

    fun compute(records: List<ValidationAttemptComparison>): ComprehensiveAccuracyStats {
        val reportRecords = records.filter { it.dataSet.equals("REPORT", ignoreCase = true) }
        val tuneRecords = records.filter { it.dataSet.equals("TUNE", ignoreCase = true) }

        val reportStats = computeModeStats(reportRecords, useHeightOnly = false)
        val heightOnlyStats = if (reportRecords.any { it.aiDistanceHeightOnlyCm != null }) {
            computeModeStats(reportRecords, useHeightOnly = true)
        } else null

        val foulStats = computeFoulMetrics(reportRecords)

        val worstAttempts = reportRecords
            .sortedByDescending { it.absD }
            .take(5)

        val tuneStats = if (tuneRecords.isNotEmpty()) {
            computeModeStats(tuneRecords, useHeightOnly = false)
        } else null

        return ComprehensiveAccuracyStats(
            reportStats = reportStats,
            heightOnlyStats = heightOnlyStats,
            foulStats = foulStats,
            worstAttempts = worstAttempts,
            tuneStats = tuneStats
        )
    }

    private fun round1(v: Double): Double = (kotlin.math.round(v * 10.0)) / 10.0
    private fun round2(v: Double): Double = (kotlin.math.round(v * 100.0)) / 100.0
}
