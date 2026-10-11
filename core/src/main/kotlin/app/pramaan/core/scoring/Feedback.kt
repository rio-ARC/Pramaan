package app.pramaan.core.scoring

import app.pramaan.core.model.Landmark
import app.pramaan.core.model.PoseLandmarks

object Feedback {
    const val FEEDBACK_DIP_DEEPER = "Dip a little deeper before jumping"
    const val FEEDBACK_SWING_ARMS = "Swing your arms up"
    const val FEEDBACK_GOOD = "Good technique"

    const val FB_BJ_DEEPER_DIP = "fb_bj_deeper_dip"
    const val FB_BJ_SWING_ARMS = "fb_bj_swing_arms"
    const val FB_BJ_LAUNCH_HIGHER = "fb_bj_launch_higher"
    const val FB_BJ_LAUNCH_FLATTER = "fb_bj_launch_flatter"
    const val FB_BJ_REACH_FORWARD = "fb_bj_reach_forward"
    const val FB_BJ_GOOD = "fb_bj_good"

    /**
     * Computes the angle in degrees between three landmarks at vertex B: A -> B -> C.
     */
    fun calculateAngleDegrees(a: Landmark, b: Landmark, c: Landmark): Double {
        val uX = (a.x - b.x).toDouble()
        val uY = (a.y - b.y).toDouble()
        val vX = (c.x - b.x).toDouble()
        val vY = (c.y - b.y).toDouble()

        val dot = uX * vX + uY * vY
        val magU = Math.hypot(uX, uY)
        val magV = Math.hypot(vX, vY)

        if (magU <= 1e-6 || magV <= 1e-6) return 180.0
        val cos = (dot / (magU * magV)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(Math.acos(cos))
    }

    /**
     * Determines technique feedback for a jump based on PRD 10.2 step 9:
     * - If knee angle at the bottom of the dip is above 120 deg: "Dip a little deeper before jumping"
     * - Else if wrists stay below the shoulders throughout the jump: "Swing your arms up"
     * - Else: "Good technique"
     */
    fun evaluate(minKneeAngle: Double, wristsSwungAboveShoulders: Boolean): String {
        return when {
            minKneeAngle > 120.0 -> FEEDBACK_DIP_DEEPER
            !wristsSwungAboveShoulders -> FEEDBACK_SWING_ARMS
            else -> FEEDBACK_GOOD
        }
    }
}
