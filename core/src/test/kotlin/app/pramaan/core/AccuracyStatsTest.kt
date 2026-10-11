package app.pramaan.core

import app.pramaan.core.validation.AccuracyStatsCalculator
import app.pramaan.core.validation.ValidationAttemptComparison
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class AccuracyStatsTest {

    @Test
    fun `hand calculated 5-attempt fixture matches exact statistical formulas`() {
        // Diffs: [+0.5, -1.0, +1.5, -0.5, -1.0]
        // Sum = -0.5, Mean bias = -0.1
        // MAE = (0.5 + 1.0 + 1.5 + 0.5 + 1.0) / 5 = 4.5 / 5 = 0.9
        // Sample Variance = ((0.6)^2 + (-0.9)^2 + (1.6)^2 + (-0.4)^2 + (-0.9)^2) / 4
        // = (0.36 + 0.81 + 2.56 + 0.16 + 0.81) / 4 = 4.70 / 4 = 1.175
        // SD = sqrt(1.175) ≈ 1.08397
        // LoA lower = -0.1 - 1.96 * 1.08397 = -2.2246
        // LoA upper = -0.1 + 1.96 * 1.08397 = 2.0246
        // All 5 attempts within 2.0 cm -> 100%
        val attempts = listOf(
            ValidationAttemptComparison("s1", 1, 200.5, 200.0, 185.0, "NONE"),
            ValidationAttemptComparison("s2", 1, 179.0, 180.0, 170.0, "NONE"),
            ValidationAttemptComparison("s3", 1, 211.5, 210.0, 200.0, "NONE"),
            ValidationAttemptComparison("s4", 1, 194.5, 195.0, 180.0, "NONE"),
            ValidationAttemptComparison("s5", 1, 204.0, 205.0, 190.0, "NONE")
        )

        val stats = AccuracyStatsCalculator.computeModeStats(attempts)

        stats.n shouldBe 5
        stats.meanBiasCm shouldBe -0.1
        stats.maeCm shouldBe 0.9
        stats.sdCm shouldBe (1.08 plusOrMinus 0.02)
        stats.loaLowerCm shouldBe (-2.22 plusOrMinus 0.05)
        stats.loaUpperCm shouldBe (2.02 plusOrMinus 0.05)
        stats.pctWithin2Cm shouldBe 100.0
        stats.pctWithin3Cm shouldBe 100.0
        stats.maxErrorCm shouldBe 1.5
        stats.worstAttempt?.sessionId shouldBe "s3"

        // Wilson interval for 5/5 at 95% confidence
        stats.wilson2Cm.lowerPct shouldBe (56.55 plusOrMinus 1.0)
        stats.wilson2Cm.upperPct shouldBe 100.0
    }

    @Test
    fun `foul detection metrics compute precision recall and false-foul rate accurately`() {
        val attempts = listOf(
            // Staged toes over line: AI detected it (TP)
            ValidationAttemptComparison("s1", 1, 200.0, 200.0, null, "TOES_OVER_LINE", listOf("TOES_OVER_LINE")),
            // Staged toes over line: AI missed it (FN)
            ValidationAttemptComparison("s2", 1, 190.0, 190.0, null, "TOES_OVER_LINE", emptyList()),
            // Staged step or hop: AI detected (TP)
            ValidationAttemptComparison("s3", 1, 185.0, 185.0, null, "STEP_OR_HOP", listOf("STEP_OR_HOP")),
            // Clean jump: AI falsely flagged step or hop (FP)
            ValidationAttemptComparison("s4", 1, 195.0, 195.0, null, "NONE", listOf("STEP_OR_HOP")),
            // Clean jump: AI clean (True Negative)
            ValidationAttemptComparison("s5", 1, 205.0, 205.0, null, "NONE", emptyList())
        )

        val foulMetrics = AccuracyStatsCalculator.computeFoulMetrics(attempts)

        // 3 staged fouls (s1, s2, s3). AI detected in s1 and s3 -> 2/3 = 66.7% recall
        foulMetrics.totalStaged shouldBe 3
        foulMetrics.detectedStaged shouldBe 2
        foulMetrics.stagedFoulRecallPct shouldBe 66.7

        // 2 clean jumps (s4, s5). s4 had false foul -> 1/2 = 50.0% false foul rate
        foulMetrics.totalClean shouldBe 2
        foulMetrics.falseFoulClean shouldBe 1
        foulMetrics.cleanJumpFalseFoulPct shouldBe 50.0

        val toes = foulMetrics.byCode.first { it.foulCode == "TOES_OVER_LINE" }
        toes.tp shouldBe 1
        toes.fn shouldBe 1
        toes.fp shouldBe 0
        toes.recall shouldBe 0.5
        toes.precision shouldBe 1.0

        val step = foulMetrics.byCode.first { it.foulCode == "STEP_OR_HOP" }
        step.tp shouldBe 1
        step.fn shouldBe 0
        step.fp shouldBe 1
        step.recall shouldBe 1.0
        step.precision shouldBe 0.5
    }

    @Test
    fun `comprehensive stats splits report and tune sets and computes height only comparison`() {
        val attempts = listOf(
            // REPORT
            ValidationAttemptComparison("s1", 1, 200.0, 198.0, 180.0, "NONE", emptyList(), "REPORT"), // d=2, dH=-18
            ValidationAttemptComparison("s2", 1, 200.0, 202.0, 185.0, "NONE", emptyList(), "REPORT"), // d=-2, dH=-17
            // TUNE
            ValidationAttemptComparison("tune1", 1, 195.0, 190.0, null, "NONE", emptyList(), "TUNE") // d=5
        )

        val comp = AccuracyStatsCalculator.compute(attempts)

        comp.reportStats.n shouldBe 2
        comp.reportStats.meanBiasCm shouldBe 0.0
        comp.reportStats.maeCm shouldBe 2.0
        comp.reportStats.pctWithin2Cm shouldBe 100.0

        comp.heightOnlyStats shouldNotBe null
        comp.heightOnlyStats!!.n shouldBe 2
        comp.heightOnlyStats!!.meanBiasCm shouldBe -17.5
        comp.heightOnlyStats!!.pctWithin2Cm shouldBe 0.0

        comp.tuneStats shouldNotBe null
        comp.tuneStats!!.n shouldBe 1
        comp.tuneStats!!.meanBiasCm shouldBe 5.0
    }
}
