package app.pramaan.core.scoring

import kotlinx.serialization.Serializable

@Serializable
data class JumpAttemptResult(
    val attemptNumber: Int,
    val flightTimeSeconds: Double,
    val flightCm: Double,
    val hipRiseCm: Double,
    val gravity: Double,
    val valid: Boolean,
    val takeOffTimestampNs: Long,
    val landingTimestampNs: Long,
    val feedback: String,
    val invalidReason: String? = null
)

@Serializable
data class JumpResult(
    val attempts: List<JumpAttemptResult>,
    val bestFlightCm: Double,
    val bestHipRiseCm: Double,
    val bestGravity: Double,
    val feedback: String,
    val hasValidJump: Boolean
) {
    val bestAttempt: JumpAttemptResult?
        get() = attempts.filter { it.valid }.maxByOrNull { it.flightCm }
}
