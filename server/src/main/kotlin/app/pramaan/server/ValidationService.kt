package app.pramaan.server

import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.scoring.broadjump.BjNote
import app.pramaan.core.validation.AccuracyStatsCalculator
import app.pramaan.core.validation.ComprehensiveAccuracyStats
import app.pramaan.core.validation.ValidationAttemptComparison
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ValidationService {

    private val json = Json { ignoreUnknownKeys = true }

    fun getComparisons(): List<ValidationAttemptComparison> {
        val entries = ValidationRepo.getAll()
        val sessionCache = mutableMapOf<String, ServerSessionRecord?>()

        return entries.mapNotNull { entry ->
            val session = sessionCache.computeIfAbsent(entry.sessionId) { ServerDb.getById(it) }
            var aiDistance: Double? = null
            var aiDistanceHeightOnly: Double? = null
            val aiFouls = mutableListOf<String>()

            if (session?.rawManifest != null) {
                try {
                    val manifest = json.decodeFromString<SessionManifest>(session.rawManifest)
                    val bjAttempts = manifest.result.broadJumpAttempts
                    val attempt = bjAttempts?.find { it.index == entry.attempt }
                    if (attempt != null) {
                        aiDistance = attempt.distanceCm
                        aiDistanceHeightOnly = attempt.distanceHeightOnlyCm
                        aiFouls.addAll(attempt.fouls.map { it.name })
                        if (attempt.notes.contains(BjNote.FALL_BACK)) {
                            aiFouls.add("FALL_BACK")
                        }
                    }
                } catch (_: Exception) {}
            }

            // Fallback: If no broadJumpAttempts list, but session.best exists and attempt is 1
            if (aiDistance == null && session != null && entry.attempt == 1) {
                aiDistance = session.best
            }

            if (aiDistance != null) {
                ValidationAttemptComparison(
                    sessionId = entry.sessionId,
                    attemptIndex = entry.attempt,
                    aiDistanceCm = aiDistance,
                    tapeCm = entry.tapeCm,
                    aiDistanceHeightOnlyCm = aiDistanceHeightOnly,
                    truthFoul = entry.truthFoul,
                    aiFouls = aiFouls,
                    dataSet = entry.dataSet
                )
            } else {
                null
            }
        }
    }

    fun getAccuracyStats(): ComprehensiveAccuracyStats {
        val comparisons = getComparisons()
        return AccuracyStatsCalculator.compute(comparisons)
    }

    fun generateBlandAltmanSvg(
        comparisons: List<ValidationAttemptComparison>,
        width: Int = 680,
        height: Int = 360
    ): String {
        if (comparisons.isEmpty()) {
            return """<div style="padding: 40px; text-align: center; color: #64748B;">No validation data available for Bland-Altman plot.</div>"""
        }

        val padL = 60
        val padR = 40
        val padT = 30
        val padB = 50
        val plotW = width - padL - padR
        val plotH = height - padT - padB

        val points = comparisons.map { Pair((it.aiDistanceCm + it.tapeCm) / 2.0, it.d) }
        val xVals = points.map { it.first }
        val yVals = points.map { it.second }

        val xMin = max(100.0, (xVals.minOrNull() ?: 150.0) - 15.0)
        val xMax = (xVals.maxOrNull() ?: 250.0) + 15.0

        val stats = AccuracyStatsCalculator.computeModeStats(comparisons)
        val bias = stats.meanBiasCm
        val loaUpper = stats.loaUpperCm
        val loaLower = stats.loaLowerCm

        val yMin = min(-3.5, min(yVals.minOrNull() ?: -3.0, loaLower - 1.0))
        val yMax = max(3.5, max(yVals.maxOrNull() ?: 3.0, loaUpper + 1.0))

        fun mapX(x: Double): Double = padL + ((x - xMin) / (xMax - xMin)) * plotW
        fun mapY(y: Double): Double = padT + ((yMax - y) / (yMax - yMin)) * plotH

        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" width="100%" height="$height" style="background: white; border-radius: 12px; font-family: 'Lexend', sans-serif;">""")

        // Grid lines
        sb.append("""<line x1="$padL" y1="${padT + plotH}" x2="${padL + plotW}" y2="${padT + plotH}" stroke="#CBD5E1" stroke-width="1.5"/>""")
        sb.append("""<line x1="$padL" y1="$padT" x2="$padL" y2="${padT + plotH}" stroke="#CBD5E1" stroke-width="1.5"/>""")

        // Shaded +-2.0 cm acceptance zone
        val zoneTopY = mapY(2.0)
        val zoneBottomY = mapY(-2.0)
        val zoneH = max(0.0, zoneBottomY - zoneTopY)
        sb.append("""<rect x="$padL" y="$zoneTopY" width="$plotW" height="$zoneH" fill="rgba(22, 163, 74, 0.08)"/>""")
        sb.append("""<line x1="$padL" y1="$zoneTopY" x2="${padL + plotW}" y2="$zoneTopY" stroke="#16A34A" stroke-dasharray="3,3" stroke-width="1"/>""")
        sb.append("""<line x1="$padL" y1="$zoneBottomY" x2="${padL + plotW}" y2="$zoneBottomY" stroke="#16A34A" stroke-dasharray="3,3" stroke-width="1"/>""")
        sb.append("""<text x="${padL + plotW - 10}" y="${zoneTopY - 4}" fill="#16A34A" font-size="11" font-weight="600" text-anchor="end">SAI ±2 cm Target</text>""")

        // Zero line
        val zeroY = mapY(0.0)
        sb.append("""<line x1="$padL" y1="$zeroY" x2="${padL + plotW}" y2="$zeroY" stroke="#94A3B8" stroke-dasharray="4,4" stroke-width="1"/>""")

        // Bias line
        val biasY = mapY(bias)
        sb.append("""<line x1="$padL" y1="$biasY" x2="${padL + plotW}" y2="$biasY" stroke="#2563EB" stroke-width="2"/>""")
        sb.append("""<text x="${padL + 8}" y="${biasY - 4}" fill="#2563EB" font-size="11" font-weight="700">Mean Bias: ${String.format("%.2f", bias)} cm</text>""")

        // LoA lines (+1.96 SD, -1.96 SD)
        val loaUpperY = mapY(loaUpper)
        sb.append("""<line x1="$padL" y1="$loaUpperY" x2="${padL + plotW}" y2="$loaUpperY" stroke="#EA580C" stroke-dasharray="4,4" stroke-width="1.5"/>""")
        sb.append("""<text x="${padL + 8}" y="${loaUpperY - 4}" fill="#EA580C" font-size="11" font-weight="600">+1.96 SD (${String.format("%.2f", loaUpper)} cm)</text>""")

        val loaLowerY = mapY(loaLower)
        sb.append("""<line x1="$padL" y1="$loaLowerY" x2="${padL + plotW}" y2="$loaLowerY" stroke="#EA580C" stroke-dasharray="4,4" stroke-width="1.5"/>""")
        sb.append("""<text x="${padL + 8}" y="${loaLowerY + 12}" fill="#EA580C" font-size="11" font-weight="600">-1.96 SD (${String.format("%.2f", loaLower)} cm)</text>""")

        // Scatter points
        for (c in comparisons) {
            val meanVal = (c.aiDistanceCm + c.tapeCm) / 2.0
            val cx = mapX(meanVal)
            val cy = mapY(c.d)
            val isWithin2 = abs(c.d) <= 2.0
            val dotColor = if (isWithin2) "#16A34A" else "#DC2626"
            sb.append("""<circle cx="${String.format("%.1f", cx)}" cy="${String.format("%.1f", cy)}" r="5.5" fill="$dotColor" stroke="#FFFFFF" stroke-width="1.5">""")
            sb.append("""<title>Session: ${c.sessionId} Att: ${c.attemptIndex}&#10;Tape: ${c.tapeCm} cm&#10;AI: ${c.aiDistanceCm} cm&#10;Diff: ${String.format("%+.2f", c.d)} cm</title>""")
            sb.append("""</circle>""")
        }

        // Axis Titles
        sb.append("""<text x="${padL + plotW / 2}" y="${height - 12}" fill="#475569" font-size="12" font-weight="600" text-anchor="middle">Mean of Tape and AI Distance (cm)</text>""")
        sb.append("""<text x="16" y="${padT + plotH / 2}" fill="#475569" font-size="12" font-weight="600" text-anchor="middle" transform="rotate(-90 16 ${padT + plotH / 2})">Difference (AI − Tape, cm)</text>""")

        sb.append("</svg>")
        return sb.toString()
    }

    fun generateScatterSvg(
        comparisons: List<ValidationAttemptComparison>,
        width: Int = 440,
        height: Int = 360
    ): String {
        if (comparisons.isEmpty()) {
            return """<div style="padding: 40px; text-align: center; color: #64748B;">No validation data available for scatter plot.</div>"""
        }

        val padL = 55
        val padR = 25
        val padT = 30
        val padB = 50
        val plotW = width - padL - padR
        val plotH = height - padT - padB

        val allVals = comparisons.map { it.tapeCm } + comparisons.map { it.aiDistanceCm }
        val minVal = max(100.0, (allVals.minOrNull() ?: 150.0) - 10.0)
        val maxVal = (allVals.maxOrNull() ?: 250.0) + 10.0

        fun mapVal(v: Double): Double = padL + ((v - minVal) / (maxVal - minVal)) * plotW
        fun mapValY(v: Double): Double = padT + ((maxVal - v) / (maxVal - minVal)) * plotH

        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" width="100%" height="$height" style="background: white; border-radius: 12px; font-family: 'Lexend', sans-serif;">""")

        // Grid lines
        sb.append("""<line x1="$padL" y1="${padT + plotH}" x2="${padL + plotW}" y2="${padT + plotH}" stroke="#CBD5E1" stroke-width="1.5"/>""")
        sb.append("""<line x1="$padL" y1="$padT" x2="$padL" y2="${padT + plotH}" stroke="#CBD5E1" stroke-width="1.5"/>""")

        // Identity line y = x (45 degrees)
        val x0 = mapVal(minVal)
        val y0 = mapValY(minVal)
        val x1 = mapVal(maxVal)
        val y1 = mapValY(maxVal)
        sb.append("""<line x1="$x0" y1="$y0" x2="$x1" y2="$y1" stroke="#94A3B8" stroke-dasharray="4,4" stroke-width="2"/>""")
        sb.append("""<text x="${x1 - 10}" y="${y1 + 18}" fill="#94A3B8" font-size="11" font-weight="600" text-anchor="end">y = x (Perfect Concordance)</text>""")

        // Scatter points
        for (c in comparisons) {
            val cx = mapVal(c.tapeCm)
            val cy = mapValY(c.aiDistanceCm)
            val isWithin2 = abs(c.d) <= 2.0
            val dotColor = if (isWithin2) "#2563EB" else "#DC2626"
            sb.append("""<circle cx="${String.format("%.1f", cx)}" cy="${String.format("%.1f", cy)}" r="5" fill="$dotColor" stroke="#FFFFFF" stroke-width="1.5">""")
            sb.append("""<title>Session: ${c.sessionId}&#10;Tape: ${c.tapeCm} cm&#10;AI: ${c.aiDistanceCm} cm</title>""")
            sb.append("""</circle>""")
        }

        // Axis Titles
        sb.append("""<text x="${padL + plotW / 2}" y="${height - 12}" fill="#475569" font-size="12" font-weight="600" text-anchor="middle">Tape Measure (cm)</text>""")
        sb.append("""<text x="16" y="${padT + plotH / 2}" fill="#475569" font-size="12" font-weight="600" text-anchor="middle" transform="rotate(-90 16 ${padT + plotH / 2})">Pramaan AI (cm)</text>""")

        sb.append("</svg>")
        return sb.toString()
    }
}
