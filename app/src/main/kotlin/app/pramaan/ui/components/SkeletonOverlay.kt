package app.pramaan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import app.pramaan.core.model.PoseFrame
import app.pramaan.core.model.PoseLandmarks

// Skeleton connection pairs (start, end)
private val SkeletonConnections = listOf(
    // Torso & Shoulders
    PoseLandmarks.LEFT_SHOULDER to PoseLandmarks.RIGHT_SHOULDER,
    PoseLandmarks.LEFT_SHOULDER to PoseLandmarks.LEFT_HIP,
    PoseLandmarks.RIGHT_SHOULDER to PoseLandmarks.RIGHT_HIP,
    PoseLandmarks.LEFT_HIP to PoseLandmarks.RIGHT_HIP,

    // Left Arm
    PoseLandmarks.LEFT_SHOULDER to PoseLandmarks.LEFT_ELBOW,
    PoseLandmarks.LEFT_ELBOW to PoseLandmarks.LEFT_WRIST,

    // Right Arm
    PoseLandmarks.RIGHT_SHOULDER to PoseLandmarks.RIGHT_ELBOW,
    PoseLandmarks.RIGHT_ELBOW to PoseLandmarks.RIGHT_WRIST,

    // Left Leg
    PoseLandmarks.LEFT_HIP to PoseLandmarks.LEFT_KNEE,
    PoseLandmarks.LEFT_KNEE to PoseLandmarks.LEFT_ANKLE,
    PoseLandmarks.LEFT_ANKLE to PoseLandmarks.LEFT_HEEL,
    PoseLandmarks.LEFT_HEEL to PoseLandmarks.LEFT_TOE,

    // Right Leg
    PoseLandmarks.RIGHT_HIP to PoseLandmarks.RIGHT_KNEE,
    PoseLandmarks.RIGHT_KNEE to PoseLandmarks.RIGHT_ANKLE,
    PoseLandmarks.RIGHT_ANKLE to PoseLandmarks.RIGHT_HEEL,
    PoseLandmarks.RIGHT_HEEL to PoseLandmarks.RIGHT_TOE
)

@Composable
fun SkeletonOverlay(
    poseFrame: PoseFrame?,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF4ADE80),
    jointColor: Color = Color(0xFFFFFFFF),
    highlightColor: Color? = null
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val frame = poseFrame ?: return@Canvas
        val lms = frame.landmarks
        if (lms.size < 33) return@Canvas

        val w = size.width
        val h = size.height

        val strokeColor = highlightColor ?: lineColor

        // Draw connecting bones
        for ((startIdx, endIdx) in SkeletonConnections) {
            val start = lms[startIdx]
            val end = lms[endIdx]

            if (start.visibility > 0.4f && end.visibility > 0.4f) {
                drawLine(
                    color = strokeColor,
                    start = Offset(start.x * w, start.y * h),
                    end = Offset(end.x * w, end.y * h),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Draw joint points
        for (i in 0 until 33) {
            val lm = lms[i]
            if (lm.visibility > 0.4f) {
                drawCircle(
                    color = jointColor,
                    radius = 5f,
                    center = Offset(lm.x * w, lm.y * h)
                )
                drawCircle(
                    color = strokeColor,
                    radius = 3f,
                    center = Offset(lm.x * w, lm.y * h)
                )
            }
        }
    }
}
