package app.pramaan.core.validation

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

data class ValidationSample(
    val sessionId: String,
    val attempt: Int,
    val aiCm: Double,
    val tapeCm: Double,
    val aiFouls: List<String> = emptyList(),
    val truthFoul: String = "NONE",
    val heightOnlyAiCm: Double? = null
)

data class WilsonInterval(
    val proportion: Double,
    val lower: Double,
    val upper: Double
)

data class FoulConfusion(
    val foulCode: String,
    val truePositives: Int,
    val falsePositives: Int,
    val falseNegatives: Int,
    val recall: Double,
    val precision: Double
)

data class AccuracyStats(
    val n: Int,
    val meanBiasCm: Double,
    val maeCm: Double,
    val sdCm: Double,
    val loaLowerCm: Double,
    val loaUpperCm: Double,
    val pctWithin2Cm: WilsonInterval,
    val pctWithin3Cm: WilsonInterval,
    val largestErrorCm: Double,
    val largestErrorSessionId: String,
    val foulStats: List<FoulConfusion>,
    val overallFoulRecall: Double,
    val cleanFalseFoulRate: Double
)

object AccuracyStatsCalculator {

    fun compute(samples: List<ValidationSample>): AccuracyStats? {
        if (samples.isEmpty()) return null

        val n = samples.size
        val diffs = samples.map { it.aiCm - it.tapeCm }
        val absDiffs = diffs.map { abs(it) }

        val meanBias = diffs.average()
        val mae = absDiffs.average()

        val variance = if (n > 1) {
            diffs.sumOf { (it - meanBias) * (it - meanBias) } / (n - 1)
        } else 0.0
        val sd = sqrt(variance)

        val loaLower = meanBias - 1.96 * sd
        val loaUpper = meanBias + 1.96 * sd

        val count2 = diffs.count { abs(it) <= 2.0 }
        val count3 = diffs.count { abs(it) <= 3.0 }

        val wilson2 = wilsonScoreInterval(count2, n, 0.95)
        val wilson3 = wilsonScoreInterval(count3, n, 0.95)

        var maxErr = -1.0
        var maxErrSession = ""
        for (s in samples) {
            val err = abs(s.aiCm - s.tapeCm)
            if (err > maxErr) {
                maxErr = err
                maxErrSession = s.sessionId
            }
        }

        // Foul statistics
        val foulCodes = listOf("TOES_OVER_LINE", "STEP_OR_HOP", "ONE_FOOT_TAKEOFF")
        val confusionList = mutableListOf<FoulConfusion>()

        var totalStagedFouls = 0
        var correctlyDetectedStagedFouls = 0

        var totalCleanAttempts = 0
        var falseFouledCleanAttempts = 0

        for (code in foulCodes) {
            var tp = 0
            var fp = 0
            var fn = 0
            for (s in samples) {
                val truthHas = s.truthFoul.equals(code, ignoreCase = true)
                val aiHas = s.aiFouls.any { it.equals(code, ignoreCase = true) }
                if (truthHas && aiHas) tp++
                else if (!truthHas && aiHas) fp++
                else if (truthHas && !aiHas) fn++
            }
            val rec = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 1.0
            val prec = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 1.0
            confusionList.add(
                FoulConfusion(
                    foulCode = code,
                    truePositives = tp,
                    falsePositives = fp,
                    falseNegatives = fn,
                    recall = round2(rec),
                    precision = round2(prec)
                )
            )
        }

        for (s in samples) {
            val isClean = s.truthFoul.equals("NONE", ignoreCase = true) || s.truthFoul.isBlank()
            val aiFlaggedAnyFoul = s.aiFouls.isNotEmpty()
            if (isClean) {
                totalCleanAttempts++
                if (aiFlaggedAnyFoul) falseFouledCleanAttempts++
            } else {
                totalStagedFouls++
                if (aiFlaggedAnyFoul) correctlyDetectedStagedFouls++
            }
        }

        val overallRecall = if (totalStagedFouls > 0) {
            correctlyDetectedStagedFouls.toDouble() / totalStagedFouls
        } else 1.0

        val cleanFalseRate = if (totalCleanAttempts > 0) {
            falseFouledCleanAttempts.toDouble() / totalCleanAttempts
        } else 0.0

        return AccuracyStats(
            n = n,
            meanBiasCm = round2(meanBias),
            maeCm = round2(mae),
            sdCm = round2(sd),
            loaLowerCm = round2(loaLower),
            loaUpperCm = round2(loaUpper),
            pctWithin2Cm = wilson2,
            pctWithin3Cm = wilson3,
            largestErrorCm = round2(maxErr),
            largestErrorSessionId = maxErrSession,
            foulStats = confusionList,
            overallFoulRecall = round2(overallRecall),
            cleanFalseFoulRate = round2(cleanFalseRate)
        )
    }

    private fun wilsonScoreInterval(successes: Int, total: Int, confidence: Double = 0.95): WilsonInterval {
        if (total == 0) return WilsonInterval(0.0, 0.0, 0.0)
        val p = successes.toDouble() / total
        val z = if (confidence == 0.95) 1.95996 else 1.64485
        val z2 = z * z
        val denominator = 1.0 + z2 / total
        val center = (p + z2 / (2.0 * total)) / denominator
        val factor = (z / denominator) * sqrt((p * (1.0 - p) / total) + (z2 / (4.0 * total * total)))

        val lower = maxOf(0.0, center - factor)
        val upper = minOf(1.0, center + factor)

        return WilsonInterval(
            proportion = round3(p),
            lower = round3(lower),
            upper = round3(upper)
        )
    }

    private fun round2(v: Double): Double = (v * 100.0).roundToLong() / 100.0
    private fun round3(v: Double): Double = (v * 1000.0).roundToLong() / 1000.0
}
