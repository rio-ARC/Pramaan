package app.pramaan.core.scoring

import app.pramaan.core.model.PoseFrame
import app.pramaan.core.model.PoseLandmarks

/**
 * Body scale calculation (PRD 10.2 step 1):
 * Measures mid-eye to mid-heel distance during quiet standing,
 * scale s (cm per normalized unit) = heightCm * 0.936 / eyeToHeelDist.
 */
data class BodyScale(
    val scaleCmPerUnit: Double,
    val eyeToHeelDistance: Double,
    val normalizedBodyHeight: Double,
    val threshold2Percent: Double
) {
    companion object {
        const val EYE_HEIGHT_RATIO = 0.936

        /**
         * Calculates body scale from calibration frames collected during quiet standing.
         */
        fun calculate(frames: List<PoseFrame>, heightCm: Double): BodyScale? {
            if (frames.isEmpty()) return null
            val distances = frames.mapNotNull { frame ->
                extractEyeToHeelDistance(frame)
            }
            if (distances.isEmpty()) return null

            val medianDistance = median(distances)
            if (medianDistance <= 0.001) return null

            val scale = (heightCm * EYE_HEIGHT_RATIO) / medianDistance
            val bodyHeightNorm = medianDistance / EYE_HEIGHT_RATIO
            val threshold2Pct = 0.02 * bodyHeightNorm

            return BodyScale(
                scaleCmPerUnit = scale,
                eyeToHeelDistance = medianDistance,
                normalizedBodyHeight = bodyHeightNorm,
                threshold2Percent = threshold2Pct
            )
        }

        fun extractEyeToHeelDistance(frame: PoseFrame): Double? {
            val lm = frame.landmarks
            if (lm.size < 33) return null

            val leftEye = lm[PoseLandmarks.LEFT_EYE]
            val rightEye = lm[PoseLandmarks.RIGHT_EYE]
            val leftHeel = lm[PoseLandmarks.LEFT_HEEL]
            val rightHeel = lm[PoseLandmarks.RIGHT_HEEL]

            val midEyeY = (leftEye.y + rightEye.y) / 2.0
            val midHeelY = (leftHeel.y + rightHeel.y) / 2.0

            val dist = Math.abs(midHeelY - midEyeY)
            return if (dist > 0.01) dist else null
        }

        private fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) {
                sorted[mid]
            } else {
                (sorted[mid - 1] + sorted[mid]) / 2.0
            }
        }
    }
}
